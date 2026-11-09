// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.vertx;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluatorInput;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.Response;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.Slot;
import com.amazon.demanddriventrafficevaluator.factory.DefaultLocalCacheRegistryFactory;
import com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * VertxDte against a thread-recording fake S3, on a Vertx whose blocked-thread checker warns after 200 ms
 * on an event loop, with the 1 s refresh period of vertx-test.properties. The create future means the
 * configurations are loaded; the model results load on the first periodic run, which isReady() reports.
 */
@ExtendWith(VertxExtension.class)
class VertxDteTest {

    private static final long PERIOD_MS = 1000L;
    private static final long WAIT_SECONDS = 30L;
    /** Upper bound for waits that return as soon as their condition holds; generous for loaded CI hosts. */
    private static final long WAIT_MS = TimeUnit.SECONDS.toMillis(WAIT_SECONDS);
    /** The response the core functional test expects for RawOpenRTBRequest.json once the models are loaded. */
    private static final Response LOADED_RESPONSE = Response.builder()
            .slots(List.of(Slot.builder().filterDecision(1.0).decision(0.0).build()))
            .learning(1)
            .build();
    /** The response before the only model, a LowValue one, has loaded: it forwards (fail open). */
    private static final Response NOT_LOADED_RESPONSE = Response.builder()
            .slots(List.of(Slot.builder().filterDecision(1.0).decision(1.0).build()))
            .learning(1)
            .build();
    private static final String RULE_BASED_MODEL_IDENTIFIER = "adsp_low-value_v2";

    private final String poolName = "dte-test-" + UUID.randomUUID().toString().substring(0, 8);
    private final BlockedThreadWarnings blockedThreadWarnings = new BlockedThreadWarnings();
    private Vertx vertx;
    private TimerTracker timers;
    private FakeS3Client s3;

    @BeforeEach
    void setUp() throws Exception {
        s3 = FakeS3Client.install();
        // The model-result cache is JVM-wide: start every test with no model loaded.
        new LocalCacheDao<String, Double>(DefaultLocalCacheRegistryFactory.getInstance().getDefaultLocalCacheRegistrySingleton())
                .clear(RULE_BASED_MODEL_IDENTIFIER);
        vertx = Vertx.vertx(new VertxOptions()
                .setBlockedThreadCheckInterval(10).setBlockedThreadCheckIntervalUnit(TimeUnit.MILLISECONDS)
                .setMaxEventLoopExecuteTime(200).setMaxEventLoopExecuteTimeUnit(TimeUnit.MILLISECONDS));
        timers = new TimerTracker(vertx);
    }

    @AfterEach
    void tearDown() throws Exception {
        blockedThreadWarnings.close();
        vertx.close().toCompletionStage().toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
        FakeS3Client.uninstall();
    }

    @Test
    void createAndRefreshNeverCallS3OnAnEventLoop() throws Exception {
        // Act: create from an event loop, then let the first model loads and at least one more periodic run happen
        VertxDte dte = await(onEventLoop(() -> VertxDte.create(timers.vertx(), options())));
        int callsDuringCreate = s3.callThreads().size();
        waitUntil(dte::isReady, WAIT_MS, "readiness");
        int callsUntilReady = s3.callThreads().size();
        waitUntil(() -> s3.callThreads().size() > callsUntilReady, WAIT_MS, "a periodic refresh");
        await(dte.close());

        // Assert
        assertTrue(callsDuringCreate > 0, "create loads the configurations");
        assertEquals(List.of(), s3.eventLoopCalls(), "no S3 call on an event loop");
        List<String> refreshThreads = s3.callThreads().subList(callsDuringCreate, s3.callThreads().size());
        assertTrue(refreshThreads.stream().allMatch(thread -> thread.startsWith(poolName + "-")),
                "refreshes run on the DTE worker pool: " + refreshThreads);
        assertEquals(List.of(), List.copyOf(blockedThreadWarnings.eventLoopWarnings));
    }

    @Test
    void evaluateRunsInlineOnTheEventLoop(VertxTestContext testContext) throws Exception {
        VertxDte dte = await(VertxDte.create(timers.vertx(), options()));
        waitUntil(dte::isReady, WAIT_MS, "readiness");

        vertx.runOnContext(ignored -> testContext.verify(() -> {
            assertTrue(Context.isOnEventLoopThread());
            assertEquals(LOADED_RESPONSE, evaluate(dte));
            testContext.completeNow();
        }));
        // No blocked-thread assertion here: the first evaluate() pays one-time class loading on the event loop.
        assertTrue(testContext.awaitCompletion(WAIT_SECONDS, TimeUnit.SECONDS));
        await(dte.close());
    }

    @Test
    void closeStopsRefreshesAndLeavesVertxRunning() throws Exception {
        VertxDte dte = await(VertxDte.create(timers.vertx(), options()));
        assertTrue(poolThreadsAlive());
        assertFalse(timers.liveTimers().isEmpty(), "refresh timers are set");

        // Act
        Future<Void> closed = dte.close();
        await(closed);

        // Assert: every timer is cancelled, the pool is gone and no S3 call happens any more
        assertEquals(Set.of(), timers.liveTimers());
        waitUntil(() -> !poolThreadsAlive(), WAIT_MS, "worker pool threads to end");
        int callsAfterClose = s3.callThreads().size();
        Thread.sleep(3 * PERIOD_MS);
        assertEquals(callsAfterClose, s3.callThreads().size(), "no S3 call after close");
        assertSame(closed, dte.close(), "close is idempotent");
        assertEquals("still running", await(vertx.executeBlocking(() -> "still running")));
    }

    @Test
    void closeLetsALoadInProgressFinishBeforeReleasingThePool() throws Exception {
        // Arrange: the first model load is in progress, held in its S3 read
        s3.holdModelResult();
        VertxDte dte = await(VertxDte.create(timers.vertx(), options()));
        assertTrue(s3.awaitModelResultRequested(WAIT_SECONDS, TimeUnit.SECONDS));

        // Act
        Future<Void> closed = dte.close();
        Thread.sleep(300);

        // Assert: the pool is not released under the load, which then completes uninterrupted
        assertFalse(closed.isComplete(), "close waits for the load in progress");
        assertEquals(1, timers.liveTimers().size(), "the refresh timers are cancelled at once; only the 60 s close "
                + "bound is left");
        s3.releaseModelResult();
        await(closed);
        assertFalse(s3.gateInterrupted(), "the load was not interrupted");
        assertEquals(LOADED_RESPONSE, evaluate(dte), "the whole model was loaded");
        assertEquals(Set.of(), timers.liveTimers());
        waitUntil(() -> !poolThreadsAlive(), WAIT_MS, "worker pool threads to end");
    }

    @Test
    void createSucceedsBeforeTheFirstModelLoadAndIsReadyFollowsIt() throws Exception {
        // Arrange: hold the first model-result read, so the periodic run that loads it cannot finish
        s3.holdModelResult();

        // Act
        VertxDte dte = await(VertxDte.create(timers.vertx(), options()));

        // Assert: the future succeeds with the configurations loaded, and evaluations fail open until the model loads
        assertFalse(dte.isReady(), "not ready before the first model load");
        assertEquals(NOT_LOADED_RESPONSE, evaluate(dte));
        assertTrue(s3.awaitModelResultRequested(WAIT_SECONDS, TimeUnit.SECONDS), "the first periodic model load starts");
        assertFalse(dte.isReady(), "not ready while the first model load is in flight");
        s3.releaseModelResult();
        waitUntil(dte::isReady, WAIT_MS, "readiness after the first model load");
        assertEquals(LOADED_RESPONSE, evaluate(dte));
        await(dte.close());
    }

    @Test
    void failedFirstModelLoadStillSucceedsAndBecomesReady() throws Exception {
        // Arrange: every read of the model result fails
        s3.failModelResult();

        // Act
        VertxDte dte = await(VertxDte.create(timers.vertx(), options()));

        // Assert: the failed first run counts for readiness, and the unloaded LowValue model forwards
        waitUntil(dte::isReady, WAIT_MS, "readiness after a failed first model load");
        assertEquals(NOT_LOADED_RESPONSE, evaluate(dte));
        await(dte.close());
    }

    @Test
    void initFailureFailsTheFutureAndLeavesNoTimers() throws Exception {
        // Arrange: the model configuration fails to load, after the other configuration tasks have set their timers
        s3.failModelConfiguration();

        // Act
        ExecutionException failure = assertThrows(ExecutionException.class, () -> await(VertxDte.create(timers.vertx(), options())));

        // Assert
        assertTrue(failure.getCause().getMessage().contains("ModelConfigurationPeriodicLoadingInitializer"), failure.getCause().toString());
        assertEquals(Set.of(), timers.liveTimers(), "no timer left set");
        waitUntil(() -> !poolThreadsAlive(), WAIT_MS, "worker pool threads to end");
        int callsAfterFailure = s3.callThreads().size();
        Thread.sleep(3 * PERIOD_MS);
        assertEquals(callsAfterFailure, s3.callThreads().size(), "no configuration refresh after a failed create");
    }

    @Test
    void createAgainAfterAFailedCreateSucceedsAndBecomesReady() throws Exception {
        // Arrange: a first create fails, as when init() throws because a configuration cannot be loaded
        s3.failModelConfiguration();
        assertThrows(ExecutionException.class, () -> await(VertxDte.create(timers.vertx(), options())));
        waitUntil(() -> !poolThreadsAlive(), WAIT_MS, "worker pool threads of the failed create to end");

        // Act: the file loads again and the caller retries with a new create
        s3.restoreModelConfiguration();
        VertxDte dte = await(VertxDte.create(timers.vertx(), options()));

        // Assert: the retry starts over on a new factory and its own pool, and its models load
        waitUntil(dte::isReady, WAIT_MS, "readiness after the retried create");
        assertEquals(LOADED_RESPONSE, evaluate(dte));
        await(dte.close());
        assertEquals(Set.of(), timers.liveTimers());
    }

    @Test
    void blockedThreadWarningsAreCaptured() throws Exception {
        // Probe: the "no warning" assertions above are only meaningful if a blocked event loop is seen
        vertx.runOnContext(ignored -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        waitUntil(() -> !blockedThreadWarnings.eventLoopWarnings.isEmpty(), WAIT_MS, "a blocked-thread warning");
    }

    @Test
    void createFailsWithoutRequiredOptions() {
        Future<VertxDte> created = VertxDte.create(vertx, new VertxDteOptions());
        assertTrue(created.failed());
        assertFalse(poolThreadsAlive());
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

    private <T> Future<T> onEventLoop(java.util.function.Supplier<Future<T>> action) throws Exception {
        CompletableFuture<Future<T>> result = new CompletableFuture<>();
        vertx.runOnContext(ignored -> result.complete(action.get()));
        return result.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private boolean poolThreadsAlive() {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> thread.isAlive() && thread.getName().startsWith(poolName + "-"));
    }

    private static void waitUntil(BooleanSupplier condition, long timeoutMs, String what) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("timed out after " + timeoutMs + " ms waiting for " + what);
            }
            Thread.sleep(10);
        }
    }

    private static Response evaluate(VertxDte dte) throws IOException {
        return dte.evaluator()
                .evaluate(BidRequestEvaluatorInput.builder().openRtbRequest(resource("/test/RawOpenRTBRequest.json")).build())
                .getResponse();
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream = VertxDteTest.class.getResourceAsStream(path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Collects the Vert.x blocked-thread warnings about event-loop threads through JUL; the test JVM sets
     * vertx.logger-delegate-factory-class-name to the JUL delegate. The logger name is the same in 4.5 and 5.
     */
    private static final class BlockedThreadWarnings extends Handler implements AutoCloseable {

        private final Queue<String> eventLoopWarnings = new ConcurrentLinkedQueue<>();
        private final Logger logger = Logger.getLogger("io.vertx.core.impl.BlockedThreadChecker");

        BlockedThreadWarnings() {
            logger.addHandler(this);
        }

        @Override
        public void publish(LogRecord record) {
            if (record.getMessage() != null && record.getMessage().contains("vert.x-eventloop-thread")) {
                eventLoopWarnings.add(record.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            logger.removeHandler(this);
        }
    }
}
