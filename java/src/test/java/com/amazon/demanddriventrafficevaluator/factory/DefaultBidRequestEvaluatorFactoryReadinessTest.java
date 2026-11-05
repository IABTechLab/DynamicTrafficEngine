// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import com.amazon.demanddriventrafficevaluator.modelfeature.extractor.ExtractorRegistry;
import com.amazon.demanddriventrafficevaluator.modelfeature.transformer.TransformerRegistry;
import com.amazon.demanddriventrafficevaluator.repository.dao.BloomFilterDao;
import com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao;
import com.amazon.demanddriventrafficevaluator.repository.localcache.LocalCacheRegistry;
import com.amazon.demanddriventrafficevaluator.task.TaskInitializer;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_KEY_EXPERIMENT_CONFIGURATION;
import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_KEY_MODEL_CONFIGURATION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link DefaultBidRequestEvaluatorFactory#isReady()} while the first model load stays on the periodic
 * schedule.
 * <p>
 * S3 is faked with a mocked {@link S3Client} injected into {@link AWSServiceClientFactory}. The executor runs
 * one-shot tasks (the registry setup) inline or defers them, and captures periodic tasks without running them,
 * so each test decides when a periodic run happens.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class DefaultBidRequestEvaluatorFactoryReadinessTest {

    private static final String SSP_IDENTIFIER = "readiness-test-ssp";
    private static final String RULE_BASED_MODEL_IDENTIFIER = "adsp_low-value_v2";
    private static final String BLOOM_FILTER_MODEL_IDENTIFIER = "adsp_inv_v1";
    private static final String MODEL_RESULT_KEY = "site|video|5895-EB|USA|640x390|u|0";
    private static final long DEFAULT_PERIOD_MS = 300000L;
    // stage one schedules the experiment and model configuration refreshes, stage two the two model loads
    private static final int CONFIGURATION_TASK_COUNT = 2;
    private static final int PERIODIC_TASK_COUNT = 4;
    // DefaultTaskInitializerFactory's stage two schedules the rule-based model load only
    private static final int RULE_BASED_ONLY_PERIODIC_TASK_COUNT = 3;

    private final LocalCacheRegistry localCacheRegistry =
            DefaultLocalCacheRegistryFactory.getInstance().getDefaultLocalCacheRegistrySingleton();
    private final LocalCacheDao<String, Double> modelResultsCacheDao = new LocalCacheDao<>(localCacheRegistry);
    private final ControllableExecutor executor = new ControllableExecutor();
    private final String eTag = UUID.randomUUID().toString();
    private final ExtractorRegistry extractorRegistryBefore = ExtractorRegistryFactory.getInstance().getSingleton();
    private final TransformerRegistry transformerRegistryBefore = TransformerRegistryFactory.getInstance().getSingleton();
    // while true, the model configuration file is missing
    private volatile boolean modelConfigurationMissing;

    @Mock
    private AwsCredentialsProvider credentialsProvider;

    @Mock
    private S3Client s3Client;

    private S3Client s3ClientBefore;

    @BeforeEach
    void setUp() throws Exception {
        s3ClientBefore = getS3Client();
        setS3Client(s3Client);
        // the registries are JVM-wide singletons; start empty so readiness depends on this test's init() only
        // (safe while test classes run sequentially, as configured today). getRecords() is an unmodifiable
        // snapshot, so swap in empty registries rather than clearing the shared ones.
        emptyRegistries();
        modelResultsCacheDao.clear(RULE_BASED_MODEL_IDENTIFIER);
        when(s3Client.headObject(ArgumentMatchers.<HeadObjectRequest>any()))
                .thenReturn(HeadObjectResponse.builder().contentLength(1024L).build());
    }

    @AfterEach
    void tearDown() throws Exception {
        // restore the shared registries first so a later failure here cannot leave the empty ones installed
        setRegistries(extractorRegistryBefore, transformerRegistryBefore);
        setS3Client(s3ClientBefore);
        executor.shutdownNow();
        // the configurations loaded by init() live in JVM-wide caches; leave them as a fresh JVM would
        modelResultsCacheDao.clear(CACHE_KEY_MODEL_CONFIGURATION);
        modelResultsCacheDao.clear(CACHE_KEY_EXPERIMENT_CONFIGURATION);
        modelResultsCacheDao.clear(RULE_BASED_MODEL_IDENTIFIER);
    }

    @Test
    void testIsReady_falseAfterInitUntilEveryModelTaskHasRunOnce() throws Exception {
        // Arrange
        BloomFilter<String> bloomFilter = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), 100, 0.01);
        bloomFilter.put("539014228|USA");
        stubS3("/test/ModelConfigurationWithBloomFilter.json", () -> response(resource("/test/ModelResult.csv")),
                serialize(bloomFilter));
        DefaultBidRequestEvaluatorFactory factory = newFactory();
        assertFalse(factory.isReady(), "not ready before init()");

        // Act
        factory.getTaskInitializer().init();

        // Assert: init() only scheduled the model loads, with a random first delay of up to one period
        assertEquals(PERIODIC_TASK_COUNT, executor.periodicTasks.size());
        for (ScheduledRun run : executor.periodicTasks) {
            assertTrue(run.initialDelayMs >= 0 && run.initialDelayMs < DEFAULT_PERIOD_MS);
        }
        assertFalse(modelResultsCacheDao.get(RULE_BASED_MODEL_IDENTIFIER, MODEL_RESULT_KEY).isPresent(),
                "init() must not load the rule-based model results");
        assertFalse(getBloomFilterDao(factory).get(BLOOM_FILTER_MODEL_IDENTIFIER, null).isPresent(),
                "init() must not load the bloom filter");
        assertFalse(factory.isReady(), "not ready before the first model runs");

        executor.runPeriodicTasks(0, CONFIGURATION_TASK_COUNT);
        assertFalse(factory.isReady(), "configuration refreshes do not make it ready");
        executor.runPeriodicTasks(CONFIGURATION_TASK_COUNT, CONFIGURATION_TASK_COUNT + 1);
        assertFalse(factory.isReady(), "not ready while one model task has not run yet");
        executor.runPeriodicTasks(CONFIGURATION_TASK_COUNT + 1, PERIODIC_TASK_COUNT);
        assertTrue(factory.isReady(), "ready once every model task has run once");
        assertTrue(modelResultsCacheDao.get(RULE_BASED_MODEL_IDENTIFIER, MODEL_RESULT_KEY).isPresent());
        assertTrue(getBloomFilterDao(factory).get(BLOOM_FILTER_MODEL_IDENTIFIER, null).isPresent());
    }

    @Test
    void testIsReady_trueAfterFirstRunsWhenModelFilesAreMissing() {
        // Arrange: configurations exist, but neither model file exists
        stubS3("/test/ModelConfigurationWithBloomFilter.json", null, null);
        DefaultBidRequestEvaluatorFactory factory = newFactory();
        factory.getTaskInitializer().init();
        assertFalse(factory.isReady());

        // Act
        executor.runPeriodicTasks(0, PERIODIC_TASK_COUNT);

        // Assert
        assertFalse(modelResultsCacheDao.get(RULE_BASED_MODEL_IDENTIFIER, MODEL_RESULT_KEY).isPresent());
        assertTrue(factory.isReady(), "a missing model file still finishes the first run");
    }

    @Test
    void testIsReady_trueAfterFirstRunsWhenTheLoadersFail() {
        // Arrange: the rule-based model file cannot be parsed, so its loader throws, and the bloom filter is missing
        stubS3("/test/ModelConfigurationWithBloomFilter.json",
                () -> new ResponseInputStream<>(GetObjectResponse.builder().eTag(UUID.randomUUID().toString()).build(),
                        new InputStream() {
                            @Override
                            public int read() {
                                throw new IllegalArgumentException("malformed model data");
                            }
                        }),
                null);
        DefaultBidRequestEvaluatorFactory factory = newFactory();
        factory.getTaskInitializer().init();
        assertFalse(factory.isReady());

        // Act: the scheduled wrapper logs the exceptions
        assertDoesNotThrow(() -> executor.runPeriodicTasks(0, PERIODIC_TASK_COUNT));

        // Assert
        assertFalse(modelResultsCacheDao.get(RULE_BASED_MODEL_IDENTIFIER, MODEL_RESULT_KEY).isPresent());
        assertTrue(factory.isReady(), "a failed first load still finishes the first run");
    }

    @Test
    void testIsReady_bloomFilterTaskDoesNotBlockWhenNoBloomFilterModelIsConfigured() {
        // Arrange: a configuration with a rule-based model only
        stubS3("/test/ModelConfiguration.json", () -> response(resource("/test/ModelResult.csv")), null);
        DefaultBidRequestEvaluatorFactory factory = newFactory();
        factory.getTaskInitializer().init();

        // Act
        executor.runPeriodicTasks(0, PERIODIC_TASK_COUNT);

        // Assert
        assertTrue(factory.isReady());
    }

    @Test
    void testIsReady_staysTrueAfterLaterRuns() throws Exception {
        // Arrange
        stubS3("/test/ModelConfigurationWithBloomFilter.json", null, null);
        DefaultBidRequestEvaluatorFactory factory = newFactory();
        factory.getTaskInitializer().init();
        executor.runPeriodicTasks(0, PERIODIC_TASK_COUNT);
        assertTrue(factory.isReady());

        // Act: a later refresh fails and the registries are emptied
        doThrow(S3Exception.builder().message("throttled").statusCode(503).build())
                .when(s3Client).getObject(ArgumentMatchers.<GetObjectRequest>any());
        executor.runPeriodicTasks(0, PERIODIC_TASK_COUNT);
        emptyRegistries();

        // Assert
        assertTrue(factory.isReady(), "once true it stays true");
    }

    @Test
    void testIsReady_waitsForRegistrySetup() {
        // Arrange: one-shot registry setup is deferred, as it is with a real executor
        stubS3("/test/ModelConfigurationWithBloomFilter.json", null, null);
        executor.deferOneShotTasks = true;
        DefaultBidRequestEvaluatorFactory factory = newFactory();
        factory.getTaskInitializer().init();
        executor.runPeriodicTasks(0, PERIODIC_TASK_COUNT);

        // Assert
        assertFalse(factory.isReady(), "not ready while the registries are still empty");
        executor.runDeferredOneShotTasks();
        assertTrue(factory.isReady());
    }

    @Test
    void testIsReady_falseWhenInitFails() {
        // Arrange: no configuration file exists, so the configuration load fails on every attempt
        when(s3Client.getObject(ArgumentMatchers.<GetObjectRequest>any()))
                .thenThrow(NoSuchKeyException.builder().message("no such key").build());
        DefaultBidRequestEvaluatorFactory factory = newFactory();

        // Act
        assertThrows(RuntimeException.class, () -> factory.getTaskInitializer().init());
        executor.runPeriodicTasks(0, executor.periodicTasks.size());

        // Assert
        assertFalse(factory.isReady());
    }

    @Test
    void testIsReady_retryAfterAFailedInitSchedulesEachModelTaskOnce() {
        // Arrange: the first attempt schedules both configuration refreshes and one model task, then the executor
        // rejects the other model task on every attempt, so init() throws after a model task was scheduled
        stubS3("/test/ModelConfigurationWithBloomFilter.json", null, null);
        DefaultBidRequestEvaluatorFactory factory = newFactory();
        executor.rejectPeriodicTasksFrom = CONFIGURATION_TASK_COUNT + 1;
        assertThrows(RuntimeException.class, () -> factory.getTaskInitializer().init());
        assertEquals(CONFIGURATION_TASK_COUNT + 1, executor.periodicTasks.size());
        assertFalse(factory.isReady(), "not ready after a failed init()");

        // Act: the caller retries and the executor accepts the task now
        executor.rejectPeriodicTasksFrom = Integer.MAX_VALUE;
        factory.getTaskInitializer().init();

        // Assert: the retry schedules only the model task that the failed attempt did not schedule
        assertEquals(PERIODIC_TASK_COUNT, executor.periodicTasks.size(),
                "two configuration refreshes, one rule-based and one bloom-filter task in total");
        assertFalse(factory.isReady());
        executor.runPeriodicTasks(0, executor.periodicTasks.size());
        assertTrue(factory.isReady(), "ready after the successful retry");
    }

    @Test
    void testRetryAfterAFailedStageOneSchedulesEachConfigurationRefreshOnce() {
        // Arrange: the model configuration is missing on the first attempt, so stage one fails after the
        // experiment configuration has loaded and scheduled its refresh
        stubS3("/test/ModelConfigurationWithBloomFilter.json", null, null);
        modelConfigurationMissing = true;
        DefaultBidRequestEvaluatorFactory factory = newFactory();
        assertThrows(RuntimeException.class, () -> factory.getTaskInitializer().init());
        assertEquals(1, executor.periodicTasks.size(), "only the experiment configuration refresh is scheduled");

        // Act: the file appears and the caller retries, then calls init() once more
        modelConfigurationMissing = false;
        factory.getTaskInitializer().init();
        int scheduledByTheRetry = executor.periodicTasks.size();
        factory.getTaskInitializer().init();

        // Assert: each configuration refresh and each model load is scheduled exactly once
        assertEquals(PERIODIC_TASK_COUNT, scheduledByTheRetry,
                "the retry schedules the model configuration refresh and the model loads, not a second "
                        + "experiment configuration refresh");
        assertEquals(PERIODIC_TASK_COUNT, executor.periodicTasks.size(), "a later init() schedules nothing");
        executor.runPeriodicTasks(0, executor.periodicTasks.size());
        assertTrue(factory.isReady());
    }

    @Test
    void testTaskInitializerFactoryRetryAfterAFailedStageOneSchedulesEachConfigurationRefreshOnce() {
        // Arrange: as above, through the rule-based-only factory
        stubS3("/test/ModelConfiguration.json", null, null);
        modelConfigurationMissing = true;
        DefaultTaskInitializerFactory factory = new DefaultTaskInitializerFactory(SSP_IDENTIFIER, credentialsProvider,
                "us-east-1", "test-bucket", executor);
        assertThrows(RuntimeException.class, () -> factory.getTaskInitializer().init());
        assertEquals(1, executor.periodicTasks.size(), "only the experiment configuration refresh is scheduled");

        // Act
        modelConfigurationMissing = false;
        factory.getTaskInitializer().init();
        int scheduledByTheRetry = executor.periodicTasks.size();
        factory.getTaskInitializer().init();

        // Assert
        assertEquals(RULE_BASED_ONLY_PERIODIC_TASK_COUNT, scheduledByTheRetry,
                "the retry schedules the model configuration refresh and the model load, not a second "
                        + "experiment configuration refresh");
        assertEquals(RULE_BASED_ONLY_PERIODIC_TASK_COUNT, executor.periodicTasks.size(),
                "a later init() schedules nothing");
    }

    @Test
    void testRetryOnACallerScheduledExecutorServiceSchedulesEachTaskOnceOnIt() {
        // Arrange: a ScheduledExecutorService that is not a ScheduledThreadPoolExecutor, in front of the test
        // executor; the model configuration is missing on the first attempt, so stage one fails
        ScheduledExecutorService scheduler = Executors.unconfigurableScheduledExecutorService(executor);
        stubS3("/test/ModelConfigurationWithBloomFilter.json", null, null);
        modelConfigurationMissing = true;
        DefaultBidRequestEvaluatorFactory factory = new DefaultBidRequestEvaluatorFactory(SSP_IDENTIFIER,
                credentialsProvider, "us-east-1", "test-bucket", scheduler);
        assertThrows(RuntimeException.class, () -> factory.getTaskInitializer().init());
        assertEquals(1, executor.periodicTasks.size(), "only the experiment configuration refresh is scheduled");

        // Act: the file appears and the caller retries, then calls init() once more
        modelConfigurationMissing = false;
        factory.getTaskInitializer().init();
        factory.getTaskInitializer().init();

        // Assert: every task the factory built once ran on the caller's scheduler, each scheduled exactly once
        assertEquals(PERIODIC_TASK_COUNT, executor.periodicTasks.size());
        executor.runPeriodicTasks(0, executor.periodicTasks.size());
        assertTrue(factory.isReady());
        factory.close();
        assertFalse(executor.isShutdown(), "close() leaves a caller's scheduler running");
    }

    @Test
    void testTaskInitializerFactoryRetryOnACallerScheduledExecutorServiceSchedulesEachTaskOnceOnIt() {
        // Arrange: as above, through the rule-based-only factory
        ScheduledExecutorService scheduler = Executors.unconfigurableScheduledExecutorService(executor);
        stubS3("/test/ModelConfiguration.json", null, null);
        modelConfigurationMissing = true;
        DefaultTaskInitializerFactory factory = new DefaultTaskInitializerFactory(SSP_IDENTIFIER, credentialsProvider,
                "us-east-1", "test-bucket", scheduler);
        assertThrows(RuntimeException.class, () -> factory.getTaskInitializer().init());
        assertEquals(1, executor.periodicTasks.size());

        // Act
        modelConfigurationMissing = false;
        factory.getTaskInitializer().init();
        factory.getTaskInitializer().init();

        // Assert
        assertEquals(RULE_BASED_ONLY_PERIODIC_TASK_COUNT, executor.periodicTasks.size());
    }

    @Test
    void testCloseCancelsEveryPeriodicTaskOnACallerExecutor() {
        // Arrange: the caller's executor, which close() leaves running
        stubS3("/test/ModelConfigurationWithBloomFilter.json", null, null);
        DefaultBidRequestEvaluatorFactory factory = newFactory();
        factory.getTaskInitializer().init();
        assertEquals(PERIODIC_TASK_COUNT, executor.periodicTasks.size());
        int s3CallsBeforeClose = mockingDetails(s3Client).getInvocations().size();

        // Act
        factory.close();
        executor.runPeriodicTasks(0, executor.periodicTasks.size());

        // Assert: no refresh or model load of the closed factory runs on the caller's executor, and the skipped
        // first model runs do not make the factory ready
        assertEquals(s3CallsBeforeClose, mockingDetails(s3Client).getInvocations().size(), "no S3 call after close");
        assertFalse(factory.isReady(), "a skipped run after close() is not a first model load");
        assertFalse(executor.isShutdown());
    }

    @Test
    void testIsReady_falseAfterClose() {
        // Arrange: a factory that became ready
        stubS3("/test/ModelConfigurationWithBloomFilter.json", null, null);
        DefaultBidRequestEvaluatorFactory factory = newFactory();
        factory.getTaskInitializer().init();
        executor.runPeriodicTasks(0, executor.periodicTasks.size());
        assertTrue(factory.isReady());

        // Act
        factory.close();

        // Assert: a closed factory is never ready, also when a run in progress at close() finishes later
        assertFalse(factory.isReady());
    }

    @Test
    void testInitOnAClosedFactoryFailsBeforeRunningAnyTask() {
        // Arrange: an initializer obtained before close(), nothing run yet; S3 is not stubbed, it must not be called
        reset(s3Client);
        DefaultBidRequestEvaluatorFactory factory = newFactory();
        TaskInitializer initializer = factory.getTaskInitializer();
        factory.close();

        // Act & Assert: it throws at once, with no configuration load and nothing scheduled
        assertThrows(IllegalStateException.class, initializer::init);
        assertEquals(0, mockingDetails(s3Client).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("getObject")).count());
        assertEquals(0, executor.periodicTasks.size());
    }

    @Test
    void testCloseAfterInitStopsTheOwnedExecutorAndALaterInitFails() throws Exception {
        // Arrange: the factory creates and owns its executor
        stubS3("/test/ModelConfigurationWithBloomFilter.json", null, null);
        DefaultBidRequestEvaluatorFactory factory = new DefaultBidRequestEvaluatorFactory(SSP_IDENTIFIER,
                credentialsProvider, "us-east-1", "test-bucket");
        Field field = DefaultBidRequestEvaluatorFactory.class.getDeclaredField("executor");
        field.setAccessible(true);
        ScheduledThreadPoolExecutor owned = (ScheduledThreadPoolExecutor) field.get(factory);
        try {
            TaskInitializer initializer = factory.getTaskInitializer();
            initializer.init();
            assertFalse(factory.isReady(), "the first model loads are still a random delay away");

            // Act
            factory.close();

            // Assert: the periodic refreshes and model loads the once-built tasks scheduled are cancelled, the
            // pool's threads end, and init() on the closed factory throws: every once-built task has succeeded,
            // so without the check it would return normally while nothing is scheduled any more
            assertTrue(owned.awaitTermination(5, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, factory::getTaskInitializer);
            assertThrows(IllegalStateException.class, initializer::init);
            assertFalse(factory.isReady());
        } finally {
            owned.shutdownNow();
        }
    }

    // --- helpers ---

    private DefaultBidRequestEvaluatorFactory newFactory() {
        return new DefaultBidRequestEvaluatorFactory(SSP_IDENTIFIER, credentialsProvider, "us-east-1", "test-bucket",
                executor);
    }

    private void stubS3(String modelConfiguration, Supplier<ResponseInputStream<GetObjectResponse>> modelResult,
                        byte[] bloomFilterBytes) {
        when(s3Client.getObject(ArgumentMatchers.<GetObjectRequest>any())).thenAnswer(invocation -> {
            String key = ((GetObjectRequest) invocation.getArgument(0)).key();
            if ((SSP_IDENTIFIER + "/configuration/model/config.json").equals(key) && !modelConfigurationMissing) {
                return response(resource(modelConfiguration));
            } else if ((SSP_IDENTIFIER + "/configuration/experiment/config.json").equals(key)) {
                return response(resource("/test/ExperimentConfigurationWithAggregation.json"));
            } else if (key.matches(SSP_IDENTIFIER + "/\\d{4}-\\d{2}-\\d{2}/\\d{2}/" + RULE_BASED_MODEL_IDENTIFIER + "\\.csv")
                    && modelResult != null) {
                return modelResult.get();
            } else if ((SSP_IDENTIFIER + "/models/" + BLOOM_FILTER_MODEL_IDENTIFIER + ".bloom").equals(key)
                    && bloomFilterBytes != null) {
                return response(new ByteArrayInputStream(bloomFilterBytes));
            }
            throw NoSuchKeyException.builder().message("no such key: " + key).build();
        });
    }

    private ResponseInputStream<GetObjectResponse> response(InputStream content) {
        return new ResponseInputStream<>(GetObjectResponse.builder().eTag(eTag).build(), content);
    }

    private InputStream resource(String path) {
        return getClass().getResourceAsStream(path);
    }

    private static byte[] serialize(BloomFilter<String> bloomFilter) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bloomFilter.writeTo(out);
        return out.toByteArray();
    }

    private static S3Client getS3Client() throws Exception {
        Field field = AWSServiceClientFactory.class.getDeclaredField("s3Client");
        field.setAccessible(true);
        return (S3Client) field.get(AWSServiceClientFactory.getInstance());
    }

    private static void setS3Client(S3Client client) throws Exception {
        Field field = AWSServiceClientFactory.class.getDeclaredField("s3Client");
        field.setAccessible(true);
        field.set(AWSServiceClientFactory.getInstance(), client);
    }

    private static void emptyRegistries() throws Exception {
        setRegistries(new ExtractorRegistry(new HashMap<>()), new TransformerRegistry(new HashMap<>()));
    }

    private static void setRegistries(ExtractorRegistry extractorRegistry, TransformerRegistry transformerRegistry)
            throws Exception {
        Field extractorField = ExtractorRegistryFactory.class.getDeclaredField("extractorRegistry");
        extractorField.setAccessible(true);
        extractorField.set(ExtractorRegistryFactory.getInstance(), extractorRegistry);
        Field transformerField = TransformerRegistryFactory.class.getDeclaredField("transformerRegistry");
        transformerField.setAccessible(true);
        transformerField.set(TransformerRegistryFactory.getInstance(), transformerRegistry);
    }

    private static BloomFilterDao getBloomFilterDao(DefaultBidRequestEvaluatorFactory factory) throws Exception {
        Field field = DefaultBidRequestEvaluatorFactory.class.getDeclaredField("bloomFilterDao");
        field.setAccessible(true);
        return (BloomFilterDao) field.get(factory);
    }

    private static final class ScheduledRun {
        private final Runnable command;
        private final long initialDelayMs;

        ScheduledRun(Runnable command, long initialDelayMs) {
            this.command = command;
            this.initialDelayMs = initialDelayMs;
        }
    }

    /**
     * Runs one-shot tasks inline or defers them, and captures periodic tasks in scheduling order without running
     * them. Stage one completes before stage two starts, so the configuration refreshes come first.
     */
    private static final class ControllableExecutor extends ScheduledThreadPoolExecutor {
        private final List<Runnable> deferredOneShotTasks = new ArrayList<>();
        private final List<ScheduledRun> periodicTasks = new ArrayList<>();
        private boolean deferOneShotTasks;
        // scheduleAtFixedRate() rejects every task once this many periodic tasks have been captured
        private volatile int rejectPeriodicTasksFrom = Integer.MAX_VALUE;

        ControllableExecutor() {
            super(1);
        }

        @Override
        public synchronized ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            if (deferOneShotTasks) {
                deferredOneShotTasks.add(command);
            } else {
                command.run();
            }
            return null;
        }

        @Override
        public synchronized ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
                                                                   TimeUnit unit) {
            if (periodicTasks.size() >= rejectPeriodicTasksFrom) {
                throw new RejectedExecutionException("rejected by the test");
            }
            periodicTasks.add(new ScheduledRun(command, unit.toMillis(initialDelay)));
            return null;
        }

        synchronized void runPeriodicTasks(int fromIndex, int toIndex) {
            periodicTasks.subList(fromIndex, toIndex).forEach(run -> run.command.run());
        }

        synchronized void runDeferredOneShotTasks() {
            deferredOneShotTasks.forEach(Runnable::run);
        }
    }
}
