// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.vertx;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluatorInput;
import com.amazon.demanddriventrafficevaluator.factory.DefaultLocalCacheRegistryFactory;
import com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DTE created from a verticle's {@code start()}, the usual Vert.x pattern, then that verticle undeployed without
 * calling {@link VertxDte#close()}: the refreshes must keep running on DTE's own pool and timers.
 */
class VertxDteVerticleTest {

    private static final String LOGGER_PREFIX = "com.amazon.demanddriventrafficevaluator";
    private static final long PERIOD_MS = 1000L;
    private static final long WAIT_SECONDS = 30L;
    private static final String RULE_BASED_MODEL_IDENTIFIER = "adsp_low-value_v2";

    private final String poolName = "dte-verticle-" + UUID.randomUUID().toString().substring(0, 8);
    private final List<LogEvent> events = new CopyOnWriteArrayList<>();
    private final LoggerContext logContext = (LoggerContext) LogManager.getContext(VertxDte.class.getClassLoader(), false);
    private AbstractAppender appender;
    private Vertx vertx;
    private FakeS3Client s3;

    @BeforeEach
    void setUp() throws Exception {
        s3 = FakeS3Client.install();
        new LocalCacheDao<String, Double>(DefaultLocalCacheRegistryFactory.getInstance().getDefaultLocalCacheRegistrySingleton())
                .clear(RULE_BASED_MODEL_IDENTIFIER);
        appender = new AbstractAppender("verticle-capture-" + System.nanoTime(), null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                events.add(event.toImmutable());
            }
        };
        appender.start();
        LoggerConfig loggerConfig = new LoggerConfig(LOGGER_PREFIX, Level.WARN, true);
        loggerConfig.addAppender(appender, null, null);
        logContext.getConfiguration().addLogger(LOGGER_PREFIX, loggerConfig);
        logContext.updateLoggers();
        vertx = Vertx.vertx();
    }

    @AfterEach
    void tearDown() throws Exception {
        logContext.getConfiguration().removeLogger(LOGGER_PREFIX);
        logContext.updateLoggers();
        appender.stop();
        vertx.close().toCompletionStage().toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
        FakeS3Client.uninstall();
    }

    @Test
    void refreshesKeepRunningAfterTheCreatingVerticleIsUndeployed() throws Exception {
        // Arrange: a verticle creates DTE in start(), as Vert.x applications usually do
        AtomicReference<VertxDte> created = new AtomicReference<>();
        String deploymentId = await(vertx.deployVerticle(new AbstractVerticle() {
            @Override
            public void start(Promise<Void> startPromise) {
                VertxDte.create(vertx, options()).onSuccess(dte -> {
                    created.set(dte);
                    startPromise.complete();
                }).onFailure(startPromise::fail);
            }
        }));
        VertxDte dte = created.get();
        assertNotNull(dte);
        waitUntil(dte::isReady, "readiness");

        // Act: undeploy the verticle without closing DTE, then let three refresh periods pass
        await(vertx.undeploy(deploymentId));
        int errorsBefore = errors().size();
        int callsAtUndeploy = s3.callThreads().size();
        Thread.sleep(3 * PERIOD_MS + 500);
        int callsAfter = s3.callThreads().size();
        List<String> errorsAfter = errors().subList(errorsBefore, errors().size());
        boolean poolAlive = poolThreadsAlive();
        assertNotNull(dte.evaluator().evaluate(BidRequestEvaluatorInput.builder()
                .openRtbRequest(resource("/test/RawOpenRTBRequest.json")).build()).getResponse());

        // Assert: the refresh goes on, quietly, on DTE's own pool
        assertTrue(callsAfter > callsAtUndeploy, "the periodic refresh keeps calling S3 after the undeploy");
        assertEquals(List.of(), errorsAfter, "no error is logged per tick");
        assertTrue(poolAlive, "DTE's worker pool is still running");

        // Act: close() still releases everything
        await(dte.close());
        int callsAtClose = s3.callThreads().size();
        Thread.sleep(2 * PERIOD_MS);
        assertEquals(callsAtClose, s3.callThreads().size(), "no S3 call after close");
        waitUntil(() -> !poolThreadsAlive(), "worker pool threads to end");
    }

    @Test
    void closeFromAVerticleStopKeepsItsBoundAfterTheUndeploy() throws Exception {
        // Arrange: a model load is in progress, held in its S3 read, and the drain bound is shortened to 500 ms
        s3.holdModelResult();
        try {
            VertxDte dte = await(VertxDte.create(vertx, options()));
            assertTrue(s3.awaitModelResultRequested(WAIT_SECONDS, TimeUnit.SECONDS));
            dte.executor().setDrainTimeoutMs(500);
            AtomicReference<io.vertx.core.Future<Void>> closing = new AtomicReference<>();
            String deploymentId = await(vertx.deployVerticle(new AbstractVerticle() {
                @Override
                public void stop() {
                    // the README pattern, without waiting for the future
                    closing.set(dte.close());
                }
            }));

            // Act: close() runs in stop(), on the verticle's context, which the undeploy then closes
            await(vertx.undeploy(deploymentId));

            // Assert: the bound still ends the wait although the load is still held
            closing.get().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertTrue(closing.get().succeeded());
        } finally {
            s3.releaseModelResult();
        }
    }

    private List<String> errors() {
        return events.stream().filter(event -> event.getLevel().isMoreSpecificThan(Level.ERROR))
                .map(event -> event.getLoggerName() + ": " + event.getMessage().getFormattedMessage()
                        + (event.getThrown() == null ? "" : " / " + event.getThrown()))
                .toList();
    }

    private VertxDteOptions options() {
        return new VertxDteOptions()
                .setSupplierName(FakeS3Client.SSP)
                .setBucket(FakeS3Client.BUCKET)
                .setCredentialsProvider(AnonymousCredentialsProvider.create())
                .setRegion("us-east-1")
                .setWorkerPoolName(poolName)
                .setMaxWorkerExecuteTime(30, TimeUnit.SECONDS);
    }

    private boolean poolThreadsAlive() {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> thread.isAlive() && thread.getName().startsWith(poolName + "-"));
    }

    private static <T> T await(io.vertx.core.Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out waiting for " + what);
            }
            Thread.sleep(10);
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = VertxDteVerticleTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
