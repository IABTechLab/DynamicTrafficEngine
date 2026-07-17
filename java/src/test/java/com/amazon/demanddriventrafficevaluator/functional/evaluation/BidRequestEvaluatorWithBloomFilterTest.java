// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.functional.evaluation;

import com.amazon.demanddriventrafficevaluator.BaseTestCase;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluatorInput;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluatorOnRuleBasedModel;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluatorOutput;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BloomFilterModelEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ConfigurableAggregator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.DelegatingModelEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluationResultsAggregator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.Response;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.RuleBasedModelEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.Slot;
import com.amazon.demanddriventrafficevaluator.factory.DefaultLocalCacheRegistryFactory;
import com.amazon.demanddriventrafficevaluator.factory.ExperimentManagerFactory;
import com.amazon.demanddriventrafficevaluator.factory.ExtractorRegistryFactory;
import com.amazon.demanddriventrafficevaluator.factory.TransformerRegistryFactory;
import com.amazon.demanddriventrafficevaluator.modelfeature.Extraction;
import com.amazon.demanddriventrafficevaluator.modelfeature.Transformation;
import com.amazon.demanddriventrafficevaluator.modelfeature.extractor.Extractor;
import com.amazon.demanddriventrafficevaluator.modelfeature.extractor.ExtractorRegistry;
import com.amazon.demanddriventrafficevaluator.modelfeature.transformer.Transformer;
import com.amazon.demanddriventrafficevaluator.modelfeature.transformer.TransformerRegistry;
import com.amazon.demanddriventrafficevaluator.repository.dao.BloomFilterDao;
import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao;
import com.amazon.demanddriventrafficevaluator.repository.dao.S3ObjectDao;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelFormat;
import com.amazon.demanddriventrafficevaluator.repository.loader.DefaultLoader;
import com.amazon.demanddriventrafficevaluator.repository.loader.configuration.ConfigurationLoaderInput;
import com.amazon.demanddriventrafficevaluator.repository.loader.configuration.DefaultConfigurationLoader;
import com.amazon.demanddriventrafficevaluator.repository.loader.configuration.ExperimentConfigurationLoader;
import com.amazon.demanddriventrafficevaluator.repository.loader.model.BloomFilterModelLoader;
import com.amazon.demanddriventrafficevaluator.repository.loader.model.ModelResultLoaderInput;
import com.amazon.demanddriventrafficevaluator.repository.loader.model.RuleBasedModelResultLoader;
import com.amazon.demanddriventrafficevaluator.repository.localcache.LocalCacheRegistry;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ConfigurationProvider;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ModelConfigurationProvider;
import com.amazon.demanddriventrafficevaluator.repository.provider.model.BloomFilterModelResultProvider;
import com.amazon.demanddriventrafficevaluator.repository.provider.model.ModelResultProvider;
import com.amazon.demanddriventrafficevaluator.repository.provider.model.RuleBasedModelResultProvider;
import com.amazon.demanddriventrafficevaluator.task.dataloading.BloomFilterPeriodicLoadingTask;
import com.amazon.demanddriventrafficevaluator.task.dataloading.ConfigurationPeriodicLoadingTask;
import com.amazon.demanddriventrafficevaluator.task.dataloading.ModelResultPeriodicLoadingTask;
import com.amazon.demanddriventrafficevaluator.task.registrysetup.ModelFeatureOperatorRegistrySetupTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_KEY_EXPERIMENT_CONFIGURATION;
import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_KEY_MODEL_CONFIGURATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Functional tests for the full evaluation flow with both rule-based and bloom filter models.
 * <p>
 * These tests follow the same pattern as {@link BidRequestEvaluatorOnRuleBasedModelTest}:
 * mock S3Client, load configs and models via periodic tasks, evaluate bid requests, and assert responses.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
public class BidRequestEvaluatorWithBloomFilterTest extends BaseTestCase {

    private static final String SSP_IDENTIFIER = "test-ssp";
    private static final String BLOOM_FILTER_MODEL_IDENTIFIER = "adsp_inv_v1";
    private static final String BLOOM_FILTER_S3_KEY = SSP_IDENTIFIER + "/models/" + BLOOM_FILTER_MODEL_IDENTIFIER + ".bloom";

    @Mock
    private static ScheduledThreadPoolExecutor executor;

    private final LocalCacheRegistry localCacheRegistry =
            DefaultLocalCacheRegistryFactory.getInstance().getDefaultLocalCacheRegistrySingleton();
    private final ObjectMapper mapper = new ObjectMapper();
    private final BloomFilterDao bloomFilterDao = new BloomFilterDao();

    private Dao<String, String> fileIdentifierCacheDao;
    private LocalCacheDao<String, ModelConfiguration> modelConfigurationCacheDao;
    private ConfigurationProvider<ModelConfiguration> modelConfigurationProvider;
    private Dao<String, InputStream> fileDao;
    private BidRequestEvaluator bidRequestEvaluator;

    @Mock
    private S3Client s3Client;

    @BeforeAll
    public static void setUp() {
        executeModelFeatureTransformerRegistrationTask();
    }

    @BeforeEach
    public void setUpTest() {
        fileIdentifierCacheDao = new LocalCacheDao<>(localCacheRegistry);
        modelConfigurationCacheDao = new LocalCacheDao<>(localCacheRegistry);
        modelConfigurationProvider = new ModelConfigurationProvider(modelConfigurationCacheDao);
        bloomFilterDao.clear(BLOOM_FILTER_MODEL_IDENTIFIER);

        // Stub headObject to always return a small content-length (size check passes)
        when(s3Client.headObject(ArgumentMatchers.<software.amazon.awssdk.services.s3.model.HeadObjectRequest>any()))
                .thenReturn(software.amazon.awssdk.services.s3.model.HeadObjectResponse.builder()
                        .contentLength(1024L).build());
    }

    // ========================================================================
    // 12.1 - End-to-end evaluation with both rule-based and bloom filter models
    // ========================================================================

    /**
     * Test case: bid request matches bloom filter (LowValue) → filter decision 0.0 via OR aggregation.
     * <p>
     * The bloom filter contains the tuple "539014228|USA" which matches the OpenRTB request's
     * publisherId=539014228 and country=USA. Since the bloom filter model is LowValue,
     * a hit returns cacheValue=0.0 (filter). The OR aggregation produces 0.0 because
     * at least one child (bloom filter) recommends filtering.
     * </p>
     */
    @Test
    public void testEvaluateWithBloomFilterMatch_returnFilterDecision() {
        // Arrange: create bloom filter containing the matching tuple
        @SuppressWarnings("UnstableApiUsage")
        BloomFilter<String> bloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8), 100, 0.01);
        bloomFilter.put("539014228|USA");

        setupMocksWithBloomFilter("etag-bf-match", bloomFilter);
        executeTasks();
        executeBloomFilterLoadingTask();

        String openRtbRequest = readJsonResourceAsString("/test/RawOpenRTBRequest.json");
        BidRequestEvaluatorInput input = BidRequestEvaluatorInput.builder()
                .openRtbRequest(openRtbRequest)
                .build();
        bidRequestEvaluator = getBidRequestEvaluatorWithBloomFilter();

        // Act
        BidRequestEvaluatorOutput output = bidRequestEvaluator.evaluate(input);

        // Assert: OR aggregation → score 0.0 because bloom filter matched (filter)
        // filterDecision = Math.max(score, treatmentCodeInInt) = Math.max(0.0, 1) = 1.0 (treatment C)
        Response expectedResponse = Response.builder()
                .slots(List.of(Slot.builder()
                        .filterDecision(1.0)
                        .decision(0.0)
                        .build()))
                .learning(1)
                .build();

        assertEquals(expectedResponse, output.getResponse());
    }

    /**
     * Test case: bid request does NOT match bloom filter → forward decision 1.0.
     * <p>
     * The bloom filter does NOT contain the tuple for the request's publisherId/country.
     * The rule-based model also does NOT match (different data). Both models return forward (1.0).
     * The OR aggregation produces 1.0 because no child recommends filtering.
     * </p>
     */
    @Test
    public void testEvaluateWithBloomFilterNoMatch_returnForwardDecision() {
        // Arrange: create bloom filter that does NOT contain the request's tuple
        @SuppressWarnings("UnstableApiUsage")
        BloomFilter<String> bloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8), 100, 0.01);
        bloomFilter.put("999999|GBR"); // different publisher/country

        setupMocksWithBloomFilterNoRuleMatch("etag-bf-nomatch", bloomFilter);
        executeTasksWithNoRuleMatch();
        executeBloomFilterLoadingTask();

        String openRtbRequest = readJsonResourceAsString("/test/RawOpenRTBRequest.json");
        BidRequestEvaluatorInput input = BidRequestEvaluatorInput.builder()
                .openRtbRequest(openRtbRequest)
                .build();
        bidRequestEvaluator = getBidRequestEvaluatorWithBloomFilter();

        // Act
        BidRequestEvaluatorOutput output = bidRequestEvaluator.evaluate(input);

        // Assert: OR aggregation → 1.0 because neither model matched
        Response expectedResponse = Response.builder()
                .slots(List.of(Slot.builder()
                        .filterDecision(1.0)
                        .decision(1.0)
                        .build()))
                .learning(1)
                .build();

        assertEquals(expectedResponse, output.getResponse());
    }

    /**
     * Test case: bid request matches rule-based model but NOT bloom filter → filter decision 0.0 via OR aggregation.
     * <p>
     * The rule-based model matches (the existing ModelResult.csv contains the matching tuple).
     * The bloom filter does NOT contain the request's tuple. Since OR aggregation is used,
     * the rule-based model's filter (0.0) causes the overall result to be 0.0.
     * </p>
     */
    @Test
    public void testEvaluateRuleBasedMatchBloomFilterNoMatch_returnFilterDecision() {
        // Arrange: create bloom filter that does NOT contain the request's tuple
        @SuppressWarnings("UnstableApiUsage")
        BloomFilter<String> bloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8), 100, 0.01);
        bloomFilter.put("999999|GBR"); // different publisher/country

        setupMocksWithBloomFilter("etag-bf-rulematch", bloomFilter);
        executeTasks();
        executeBloomFilterLoadingTask();

        String openRtbRequest = readJsonResourceAsString("/test/RawOpenRTBRequest.json");
        BidRequestEvaluatorInput input = BidRequestEvaluatorInput.builder()
                .openRtbRequest(openRtbRequest)
                .build();
        bidRequestEvaluator = getBidRequestEvaluatorWithBloomFilter();

        // Act
        BidRequestEvaluatorOutput output = bidRequestEvaluator.evaluate(input);

        // Assert: OR aggregation → score 0.0 because rule-based model matched (filter)
        // filterDecision = Math.max(score, treatmentCodeInInt) = Math.max(0.0, 1) = 1.0 (treatment C)
        Response expectedResponse = Response.builder()
                .slots(List.of(Slot.builder()
                        .filterDecision(1.0)
                        .decision(0.0)
                        .build()))
                .learning(1)
                .build();

        assertEquals(expectedResponse, output.getResponse());
    }

    // ========================================================================
    // 12.2 - Backward compatibility with existing rule-based-only configuration
    // ========================================================================

    /**
     * Test backward compatibility: using createWithBloomFilter() factory wiring pattern
     * with existing rule-based-only configuration (no bloom filter models, no aggregation schema)
     * produces identical results to the existing BidRequestEvaluatorOnRuleBasedModelTest.
     */
    @Test
    public void testBackwardCompatibility_ruleBasedOnlyConfig_returnExpectedResponse() {
        // Arrange: use existing test resources (no bloom filter, no aggregation schema)
        setupMocksRuleBasedOnly("etag-compat");
        executeTasksRuleBasedOnly();

        String openRtbRequest = readJsonResourceAsString("/test/RawOpenRTBRequest.json");
        BidRequestEvaluatorInput input = BidRequestEvaluatorInput.builder()
                .openRtbRequest(openRtbRequest)
                .build();
        // Wire evaluator using the bloom filter factory pattern (DelegatingModelEvaluator + ConfigurableAggregator)
        bidRequestEvaluator = getBidRequestEvaluatorWithBloomFilter();

        // Act
        BidRequestEvaluatorOutput output = bidRequestEvaluator.evaluate(input);

        // Assert: identical to BidRequestEvaluatorOnRuleBasedModelTest.testEvaluate_returnExpectedResponse
        Response expectedResponse = Response.builder()
                .slots(List.of(Slot.builder()
                        .filterDecision(1.0)
                        .decision(0.0)
                        .build()))
                .learning(1)
                .build();

        assertEquals(expectedResponse, output.getResponse());
    }

    // ========================================================================
    // 12.3 - Default-forward on bloom filter evaluation failure (missing model)
    // ========================================================================

    /**
     * Test default-forward when bloom filter model data is not loaded.
     * <p>
     * The model configuration includes a bloom filter model, but no bloom filter data
     * is loaded (simulating a missing/failed model load). The bloom filter model result
     * provider returns the default value (1.0 for LowValue = forward). The rule-based
     * model matches and returns 0.0 (filter). OR aggregation produces 0.0 because
     * the rule-based model recommends filtering.
     * </p>
     */
    @Test
    public void testDefaultForwardOnMissingBloomFilter_returnFilterFromRuleBased() {
        // Arrange: configure bloom filter model but do NOT load bloom filter data
        setupMocksWithBloomFilterConfig("etag-bf-missing");
        executeTasks();
        // Intentionally do NOT call executeBloomFilterLoadingTask()

        String openRtbRequest = readJsonResourceAsString("/test/RawOpenRTBRequest.json");
        BidRequestEvaluatorInput input = BidRequestEvaluatorInput.builder()
                .openRtbRequest(openRtbRequest)
                .build();
        bidRequestEvaluator = getBidRequestEvaluatorWithBloomFilter();

        // Act
        BidRequestEvaluatorOutput output = bidRequestEvaluator.evaluate(input);

        // Assert: bloom filter defaults to forward (1.0), rule-based matches (0.0)
        // OR aggregation → score 0.0 because rule-based recommends filtering
        // filterDecision = Math.max(score, treatmentCodeInInt) = Math.max(0.0, 1) = 1.0 (treatment C)
        Response expectedResponse = Response.builder()
                .slots(List.of(Slot.builder()
                        .filterDecision(1.0)
                        .decision(0.0)
                        .build()))
                .learning(1)
                .build();

        assertEquals(expectedResponse, output.getResponse());
    }

    /**
     * Test default-forward when bloom filter model data is not loaded and rule-based also does not match.
     * <p>
     * Both models return forward (1.0). OR aggregation produces 1.0.
     * </p>
     */
    @Test
    public void testDefaultForwardOnMissingBloomFilterAndNoRuleMatch_returnForward() {
        // Arrange: configure bloom filter model but do NOT load bloom filter data
        setupMocksWithBloomFilterConfigNoRuleMatch("etag-bf-missing-norule");
        executeTasksWithNoRuleMatch();
        // Intentionally do NOT call executeBloomFilterLoadingTask()

        String openRtbRequest = readJsonResourceAsString("/test/RawOpenRTBRequest.json");
        BidRequestEvaluatorInput input = BidRequestEvaluatorInput.builder()
                .openRtbRequest(openRtbRequest)
                .build();
        bidRequestEvaluator = getBidRequestEvaluatorWithBloomFilter();

        // Act
        BidRequestEvaluatorOutput output = bidRequestEvaluator.evaluate(input);

        // Assert: both models forward → OR aggregation → 1.0
        Response expectedResponse = Response.builder()
                .slots(List.of(Slot.builder()
                        .filterDecision(1.0)
                        .decision(1.0)
                        .build()))
                .learning(1)
                .build();

        assertEquals(expectedResponse, output.getResponse());
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    /**
     * Creates a BidRequestEvaluator wired with both rule-based and bloom filter evaluators
     * and a ConfigurableAggregator, matching the createWithBloomFilter() factory pattern.
     */
    private BidRequestEvaluator getBidRequestEvaluatorWithBloomFilter() {
        ExtractorRegistry extractorRegistry = ExtractorRegistryFactory.getInstance().getSingleton();
        Extraction extraction = new Extraction(extractorRegistry);
        TransformerRegistry transformerRegistry = TransformerRegistryFactory.getInstance().getSingleton();
        Transformation transformation = new Transformation(transformerRegistry);

        // Rule-based evaluator
        Dao<String, Double> ruleBasedModelResultDao = new LocalCacheDao<>(localCacheRegistry);
        ModelResultProvider ruleBasedModelResultProvider = new RuleBasedModelResultProvider(ruleBasedModelResultDao);
        RuleBasedModelEvaluator ruleBasedModelEvaluator =
                new RuleBasedModelEvaluator(extraction, transformation, ruleBasedModelResultProvider);

        // Bloom filter evaluator
        ModelResultProvider bloomFilterModelResultProvider = new BloomFilterModelResultProvider(bloomFilterDao);
        BloomFilterModelEvaluator bloomFilterModelEvaluator =
                new BloomFilterModelEvaluator(extraction, transformation, bloomFilterModelResultProvider);

        // Delegating evaluator
        Map<ModelFormat, ModelEvaluator> evaluatorsByFormat = Map.of(
                ModelFormat.RULE_BASED, ruleBasedModelEvaluator,
                ModelFormat.BLOOM_FILTER, bloomFilterModelEvaluator
        );
        ModelEvaluator delegatingModelEvaluator = new DelegatingModelEvaluator(evaluatorsByFormat);

        // Configurable aggregator (falls back to max aggregation when schema is null)
        ModelEvaluationResultsAggregator aggregator = new ConfigurableAggregator();

        return new BidRequestEvaluatorOnRuleBasedModel(
                SSP_IDENTIFIER,
                ExperimentManagerFactory.getInstance().provideExperimentManager(),
                modelConfigurationProvider,
                delegatingModelEvaluator,
                aggregator
        );
    }

    // --- Mock setup methods ---

    private void setupMocksWithBloomFilter(String eTag, BloomFilter<String> bloomFilter) {
        byte[] bloomFilterBytes = serializeBloomFilter(bloomFilter);
        when(s3Client.getObject(ArgumentMatchers.<GetObjectRequest>any()))
                .thenAnswer(invocation -> {
                    GetObjectRequest request = invocation.getArgument(0);
                    return getResponseForBloomFilterSetup(
                            request.bucket(), request.key(), eTag, bloomFilterBytes,
                            "/test/ModelConfigurationWithBloomFilter.json",
                            "/test/ExperimentConfigurationWithAggregation.json",
                            "/test/ModelResult.csv");
                });
        fileDao = new S3ObjectDao(s3Client);
    }

    private void setupMocksWithBloomFilterNoRuleMatch(String eTag, BloomFilter<String> bloomFilter) {
        byte[] bloomFilterBytes = serializeBloomFilter(bloomFilter);
        when(s3Client.getObject(ArgumentMatchers.<GetObjectRequest>any()))
                .thenAnswer(invocation -> {
                    GetObjectRequest request = invocation.getArgument(0);
                    return getResponseForBloomFilterSetup(
                            request.bucket(), request.key(), eTag, bloomFilterBytes,
                            "/test/ModelConfigurationWithBloomFilter.json",
                            "/test/ExperimentConfigurationWithAggregation.json",
                            null); // no rule-based model result → empty CSV
                });
        fileDao = new S3ObjectDao(s3Client);
    }

    private void setupMocksWithBloomFilterConfig(String eTag) {
        when(s3Client.getObject(ArgumentMatchers.<GetObjectRequest>any()))
                .thenAnswer(invocation -> {
                    GetObjectRequest request = invocation.getArgument(0);
                    return getResponseForBloomFilterSetup(
                            request.bucket(), request.key(), eTag, null,
                            "/test/ModelConfigurationWithBloomFilter.json",
                            "/test/ExperimentConfigurationWithAggregation.json",
                            "/test/ModelResult.csv");
                });
        fileDao = new S3ObjectDao(s3Client);
    }

    private void setupMocksWithBloomFilterConfigNoRuleMatch(String eTag) {
        when(s3Client.getObject(ArgumentMatchers.<GetObjectRequest>any()))
                .thenAnswer(invocation -> {
                    GetObjectRequest request = invocation.getArgument(0);
                    return getResponseForBloomFilterSetup(
                            request.bucket(), request.key(), eTag, null,
                            "/test/ModelConfigurationWithBloomFilter.json",
                            "/test/ExperimentConfigurationWithAggregation.json",
                            null);
                });
        fileDao = new S3ObjectDao(s3Client);
    }

    private void setupMocksRuleBasedOnly(String eTag) {
        when(s3Client.getObject(ArgumentMatchers.<GetObjectRequest>any()))
                .thenAnswer(invocation -> {
                    GetObjectRequest request = invocation.getArgument(0);
                    return getResponseForRuleBasedOnlySetup(request.bucket(), request.key(), eTag);
                });
        fileDao = new S3ObjectDao(s3Client);
    }

    private ResponseInputStream<GetObjectResponse> getResponseForBloomFilterSetup(
            String bucketName, String key, String eTag, byte[] bloomFilterBytes,
            String modelConfigPath, String experimentConfigPath, String modelResultPath) {

        ZonedDateTime now = Instant.now().atZone(ZoneId.of("UTC"));
        String date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String hour = now.format(DateTimeFormatter.ofPattern("HH"));

        if ((SSP_IDENTIFIER + "/configuration/model/config.json").equals(key)) {
            return new ResponseInputStream<>(
                    GetObjectResponse.builder().eTag(eTag).build(),
                    readResourceAsInputStream(modelConfigPath));
        } else if ((SSP_IDENTIFIER + "/configuration/experiment/config.json").equals(key)) {
            return new ResponseInputStream<>(
                    GetObjectResponse.builder().eTag(eTag).build(),
                    readResourceAsInputStream(experimentConfigPath));
        } else if ((SSP_IDENTIFIER + "/" + date + "/" + hour + "/adsp_low-value_v2.csv").equals(key)) {
            if (modelResultPath != null) {
                return new ResponseInputStream<>(
                        GetObjectResponse.builder().eTag(eTag).build(),
                        readResourceAsInputStream(modelResultPath));
            } else {
                // Return empty CSV for no rule-based match
                return new ResponseInputStream<>(
                        GetObjectResponse.builder().eTag(eTag).build(),
                        new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
            }
        } else if (BLOOM_FILTER_S3_KEY.equals(key) && bloomFilterBytes != null) {
            return new ResponseInputStream<>(
                    GetObjectResponse.builder().eTag(eTag).build(),
                    new ByteArrayInputStream(bloomFilterBytes));
        }
        throw new IllegalStateException(
                "Unexpected S3 request in getResponseForBloomFilterSetup: bucket=" + bucketName + ", key=" + key);
    }

    private ResponseInputStream<GetObjectResponse> getResponseForRuleBasedOnlySetup(
            String bucketName, String key, String eTag) {

        ZonedDateTime now = Instant.now().atZone(ZoneId.of("UTC"));
        String date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String hour = now.format(DateTimeFormatter.ofPattern("HH"));

        if ((SSP_IDENTIFIER + "/configuration/model/config.json").equals(key)) {
            return new ResponseInputStream<>(
                    GetObjectResponse.builder().eTag(eTag).build(),
                    readResourceAsInputStream("/test/ModelConfiguration.json"));
        } else if ((SSP_IDENTIFIER + "/configuration/experiment/config.json").equals(key)) {
            return new ResponseInputStream<>(
                    GetObjectResponse.builder().eTag(eTag).build(),
                    readResourceAsInputStream("/test/ExperimentConfiguration.json"));
        } else if ((SSP_IDENTIFIER + "/" + date + "/" + hour + "/adsp_low-value_v2.csv").equals(key)) {
            return new ResponseInputStream<>(
                    GetObjectResponse.builder().eTag(eTag).build(),
                    readResourceAsInputStream("/test/ModelResult.csv"));
        }
        throw new IllegalStateException(
                "Unexpected S3 request in getResponseForRuleBasedOnlySetup: bucket=" + bucketName + ", key=" + key);
    }

    // --- Task execution methods ---

    private void executeTasks() {
        executeExperimentConfigurationPeriodicLoadingTask("/test/ExperimentConfigurationWithAggregation.json");
        executeModelConfigurationPeriodicLoadingTask("/test/ModelConfigurationWithBloomFilter.json");
        executeModelResultPeriodicLoadingTask();
    }

    private void executeTasksWithNoRuleMatch() {
        executeExperimentConfigurationPeriodicLoadingTask("/test/ExperimentConfigurationWithAggregation.json");
        executeModelConfigurationPeriodicLoadingTask("/test/ModelConfigurationWithBloomFilter.json");
        executeModelResultPeriodicLoadingTask();
    }

    private void executeTasksRuleBasedOnly() {
        executeExperimentConfigurationPeriodicLoadingTask("/test/ExperimentConfiguration.json");
        executeModelConfigurationPeriodicLoadingTask("/test/ModelConfiguration.json");
        executeModelResultPeriodicLoadingTask();
    }

    private void executeExperimentConfigurationPeriodicLoadingTask(String experimentConfigPath) {
        LocalCacheDao<String, ExperimentConfiguration> experimentConfigurationCacheDao =
                new LocalCacheDao<>(localCacheRegistry);
        DefaultConfigurationLoader<ExperimentConfiguration> defaultConfigurationLoader =
                new DefaultConfigurationLoader<>(
                        fileIdentifierCacheDao,
                        fileDao,
                        experimentConfigurationCacheDao,
                        CACHE_KEY_EXPERIMENT_CONFIGURATION,
                        ExperimentConfiguration.class,
                        mapper
                );
        ExperimentConfigurationLoader experimentConfigurationLoader = new ExperimentConfigurationLoader(
                defaultConfigurationLoader,
                ExperimentManagerFactory.getInstance().provideExperimentConfigurationProvider(),
                ExperimentManagerFactory.getInstance().provideTreatmentAllocator()
        );
        ConfigurationPeriodicLoadingTask experimentConfigurationPeriodicLoadingTask =
                new ConfigurationPeriodicLoadingTask(
                        SSP_IDENTIFIER,
                        "ExperimentConfigurationPeriodicLoading",
                        1000,
                        executor,
                        experimentConfigurationLoader,
                        "experiment",
                        "test-bucket"
                );
        experimentConfigurationPeriodicLoadingTask.executeTask();
    }

    private void executeModelConfigurationPeriodicLoadingTask(String modelConfigPath) {
        DefaultLoader<ConfigurationLoaderInput> modelConfigurationLoader =
                new DefaultConfigurationLoader<>(
                        fileIdentifierCacheDao,
                        fileDao,
                        modelConfigurationCacheDao,
                        CACHE_KEY_MODEL_CONFIGURATION,
                        ModelConfiguration.class,
                        mapper
                );
        ConfigurationPeriodicLoadingTask modelConfigurationPeriodicLoadingTask =
                new ConfigurationPeriodicLoadingTask(
                        SSP_IDENTIFIER,
                        "ModelConfigurationPeriodicLoading",
                        1000,
                        executor,
                        modelConfigurationLoader,
                        "model",
                        "test-bucket"
                );
        modelConfigurationPeriodicLoadingTask.executeTask();
    }

    private void executeModelResultPeriodicLoadingTask() {
        Dao<String, Double> modelResultsCacheDao = new LocalCacheDao<>(localCacheRegistry);
        DefaultLoader<ModelResultLoaderInput> modelResultLoader = new RuleBasedModelResultLoader(
                fileIdentifierCacheDao, modelResultsCacheDao, fileDao
        );
        ModelResultPeriodicLoadingTask modelResultPeriodicLoadingTask = new ModelResultPeriodicLoadingTask(
                SSP_IDENTIFIER,
                "RuleBasedModelResultPeriodicLoading",
                1000,
                executor,
                modelConfigurationProvider,
                modelResultLoader,
                "test-bucket"
        );
        modelResultPeriodicLoadingTask.executeTask();
    }

    private void executeBloomFilterLoadingTask() {
        DefaultLoader<ModelResultLoaderInput> bloomFilterModelLoader = new BloomFilterModelLoader(
                fileIdentifierCacheDao, bloomFilterDao, fileDao
        );
        BloomFilterPeriodicLoadingTask bloomFilterPeriodicLoadingTask = new BloomFilterPeriodicLoadingTask(
                SSP_IDENTIFIER,
                "BloomFilterModelResultPeriodicLoading",
                1000,
                executor,
                modelConfigurationProvider,
                bloomFilterModelLoader,
                "test-bucket"
        );
        bloomFilterPeriodicLoadingTask.executeTask();
    }

    private static void executeModelFeatureTransformerRegistrationTask() {
        try {
            ModelFeatureOperatorRegistrySetupTask<Extractor> modelFeatureExtractorRegistrationTask =
                    new ModelFeatureOperatorRegistrySetupTask<>(
                            "ModelFeatureExtractorRegistration",
                            executor,
                            ExtractorRegistryFactory.getInstance().getSingleton(),
                            Extractor.class
                    );
            modelFeatureExtractorRegistrationTask.executeTask();
            ModelFeatureOperatorRegistrySetupTask<Transformer> modelFeatureTransformerRegistrationTask =
                    new ModelFeatureOperatorRegistrySetupTask<>(
                            "ModelFeatureTransformerRegistration",
                            executor,
                            TransformerRegistryFactory.getInstance().getSingleton(),
                            Transformer.class
                    );
            modelFeatureTransformerRegistrationTask.executeTask();
        } catch (IllegalArgumentException e) {
            // Extractors/transformers already registered by another test class in the same JVM
        }
    }

    // --- Utility methods ---

    @SuppressWarnings("UnstableApiUsage")
    private byte[] serializeBloomFilter(BloomFilter<String> bloomFilter) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bloomFilter.writeTo(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize bloom filter", e);
        }
    }

    @Override
    protected Class<?> getResourceClass() {
        return BidRequestEvaluatorWithBloomFilterTest.class;
    }
}
