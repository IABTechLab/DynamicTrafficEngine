// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.functional.evaluation;

import com.amazon.demanddriventrafficevaluator.BaseTestCase;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluatorInput;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluatorOnRuleBasedModel;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluatorOutput;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluationResultsMaxAggregator;
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
import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao;
import com.amazon.demanddriventrafficevaluator.repository.dao.S3ObjectDao;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.loader.DefaultLoader;
import com.amazon.demanddriventrafficevaluator.repository.loader.configuration.ConfigurationLoaderInput;
import com.amazon.demanddriventrafficevaluator.repository.loader.configuration.DefaultConfigurationLoader;
import com.amazon.demanddriventrafficevaluator.repository.loader.configuration.ExperimentConfigurationLoader;
import com.amazon.demanddriventrafficevaluator.repository.loader.model.ModelResultLoaderInput;
import com.amazon.demanddriventrafficevaluator.repository.loader.model.RuleBasedModelResultLoader;
import com.amazon.demanddriventrafficevaluator.repository.localcache.LocalCacheRegistry;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ConfigurationProvider;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ModelConfigurationProvider;
import com.amazon.demanddriventrafficevaluator.repository.provider.model.ModelResultProvider;
import com.amazon.demanddriventrafficevaluator.repository.provider.model.RuleBasedModelResultProvider;
import com.amazon.demanddriventrafficevaluator.task.dataloading.ConfigurationPeriodicLoadingTask;
import com.amazon.demanddriventrafficevaluator.task.dataloading.ModelResultPeriodicLoadingTask;
import com.amazon.demanddriventrafficevaluator.task.registrysetup.ModelFeatureOperatorRegistrySetupTask;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_KEY_EXPERIMENT_CONFIGURATION;
import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_KEY_MODEL_CONFIGURATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BidRequestEvaluatorOnRuleBasedModelTest extends BaseTestCase {

    private static final String SSP_IDENTIFIER = "test-ssp";
    @Mock
    private static ScheduledThreadPoolExecutor executor;

    private final LocalCacheRegistry localCacheRegistry = DefaultLocalCacheRegistryFactory.getInstance().getDefaultLocalCacheRegistrySingleton();
    private final ObjectMapper mapper = new ObjectMapper();
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
    }

    private void setupMocks(boolean useMultiModel, String eTag) {
        when(s3Client.getObject(ArgumentMatchers.<GetObjectRequest>any()))
                .thenAnswer(invocation -> {
                    GetObjectRequest request = invocation.getArgument(0);
                    return getResponseInputStreamForBucketAndKey(request.bucket(), request.key(), useMultiModel, eTag);
                });
        fileDao = new S3ObjectDao(s3Client);
    }

    private ResponseInputStream<GetObjectResponse> getResponseInputStreamForBucketAndKey(String bucketName, String key, boolean useMultiModel, String eTag) {
        ZonedDateTime now = Instant.now().atZone(ZoneId.of("UTC"));
        String date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String hour = now.format(DateTimeFormatter.ofPattern("HH"));

        String modelConfigurationResourcePath = useMultiModel ? "/test/ModelConfigurationTwoModels.json" : "/test/ModelConfiguration.json";
        String experimentConfigurationResourcePath = useMultiModel ? "/test/ExperimentConfigurationTwoModels.json" : "/test/ExperimentConfiguration.json";

        if ((SSP_IDENTIFIER + "/configuration/model/config.json").equals(key)) {
            return new ResponseInputStream<>(GetObjectResponse.builder().eTag(eTag).build(), readResourceAsInputStream(modelConfigurationResourcePath));
        } else if ((SSP_IDENTIFIER + "/configuration/experiment/config.json").equals(key)) {
            return new ResponseInputStream<>(GetObjectResponse.builder().eTag(eTag).build(), readResourceAsInputStream(experimentConfigurationResourcePath));
        } else if ((SSP_IDENTIFIER + "/" + date + "/" + hour + "/adsp_low-value_v2.csv").equals(key)) {
            return new ResponseInputStream<>(GetObjectResponse.builder().eTag(eTag).build(), readResourceAsInputStream("/test/ModelResult.csv"));
        } else if ((SSP_IDENTIFIER + "/" + date + "/" + hour + "/adsp_high-priority-deals_v1.csv").equals(key)) {
            return new ResponseInputStream<>(GetObjectResponse.builder().eTag(eTag).build(), readResourceAsInputStream("/test/ModelResultDeals.csv"));
        }
        throw new IllegalStateException("Error in getResponseInputStreamForBucketAndKey with " + bucketName + "," + key);
    }

    // Decision is high value because request has a high value deal in it, even though the request itself was low value
    @Test
    public void testEvaluateMultiModel_returnExpectedResponse() {
        setupMocks(true, "multiModel");
        executeTasks();
        String openRtbRequest = readJsonResourceAsString("/test/RawOpenRTBRequest.json");
        BidRequestEvaluatorInput input = BidRequestEvaluatorInput.builder()
                .openRtbRequest(openRtbRequest)
                .build();
        bidRequestEvaluator = getBidRequestEvaluator();
        BidRequestEvaluatorOutput output = bidRequestEvaluator.evaluate(input);

        Response expectedResponse = Response.builder()
                .slots(List.of(Slot.builder()
                        .filterDecision(1.0)
                        .ext("{\"amazontest\":{\"decision\":1.0}}")
                        .build()))
                .ext("{\"amazontest\":{\"learning\":1}}")
                .build();
        assertEquals(expectedResponse, output.getResponse());
    }

    @Test
    public void testEvaluate_returnExpectedResponse() {
        setupMocks(false, "singleModel");
        executeTasks();
        String openRtbRequest = readJsonResourceAsString("/test/RawOpenRTBRequest.json");
        BidRequestEvaluatorInput input = BidRequestEvaluatorInput.builder()
                .openRtbRequest(openRtbRequest)
                .build();
        bidRequestEvaluator = getBidRequestEvaluator();
        BidRequestEvaluatorOutput output = bidRequestEvaluator.evaluate(input);

        Response expectedResponse = Response.builder()
                .slots(List.of(Slot.builder()
                        .filterDecision(1.0)
                        .ext("{\"amazontest\":{\"decision\":0.0}}")
                        .build()))
                .ext("{\"amazontest\":{\"learning\":1}}")
                .build();
        assertEquals(expectedResponse, output.getResponse());
    }

    private BidRequestEvaluator getBidRequestEvaluator() {
        ExtractorRegistry extractorRegistry = ExtractorRegistryFactory.getInstance().getSingleton();
        Extraction extraction = new Extraction(extractorRegistry);
        TransformerRegistry transformerRegistry = TransformerRegistryFactory.getInstance().getSingleton();
        Transformation transformation = new Transformation(transformerRegistry);
        Dao<String, Double> ruleBasedModelResultDao = new LocalCacheDao<>(localCacheRegistry);
        ModelResultProvider ruleBasedmodelResultProvider = new RuleBasedModelResultProvider(ruleBasedModelResultDao);
        RuleBasedModelEvaluator modelEvaluator = new RuleBasedModelEvaluator(extraction, transformation, ruleBasedmodelResultProvider);
        ModelEvaluationResultsMaxAggregator modelEvaluationResultsAggregator = new ModelEvaluationResultsMaxAggregator();
        return new BidRequestEvaluatorOnRuleBasedModel(
                SSP_IDENTIFIER,
                ExperimentManagerFactory.getInstance().provideExperimentManager(),
                modelConfigurationProvider,
                modelEvaluator,
                modelEvaluationResultsAggregator
        );
    }

    private void executeTasks() {
        executeExperimentConfigurationPeriodicLoadingTask();
        executeModelConfigurationPeriodicLoadingTask();
        executeModelResultPeriodicLoadingTask();
    }

    private void executeExperimentConfigurationPeriodicLoadingTask() {
        LocalCacheDao<String, ExperimentConfiguration> experimentConfigurationCacheDao = new LocalCacheDao<>(localCacheRegistry);
        DefaultConfigurationLoader<ExperimentConfiguration> defaultConfigurationLoader = new DefaultConfigurationLoader<>(
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
        ConfigurationPeriodicLoadingTask experimentConfigurationPeriodicLoadingTask = new ConfigurationPeriodicLoadingTask(
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

    private void executeModelConfigurationPeriodicLoadingTask() {
        DefaultLoader<ConfigurationLoaderInput> modelConfigurationLoader = new DefaultConfigurationLoader<>(
                fileIdentifierCacheDao,
                fileDao,
                modelConfigurationCacheDao,
                CACHE_KEY_MODEL_CONFIGURATION,
                ModelConfiguration.class,
                mapper
        );
        ConfigurationPeriodicLoadingTask modelConfigurationPeriodicLoadingTask = new ConfigurationPeriodicLoadingTask(
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

    private static void executeModelFeatureTransformerRegistrationTask() {
        ModelFeatureOperatorRegistrySetupTask<Extractor> modelFeatureExtractorRegistrationTask = new ModelFeatureOperatorRegistrySetupTask<>(
                "ModelFeatureExtractorRegistration",
                executor,
                ExtractorRegistryFactory.getInstance().getSingleton(),
                Extractor.class
        );
        modelFeatureExtractorRegistrationTask.executeTask();
        ModelFeatureOperatorRegistrySetupTask<Transformer> modelFeatureTransformerRegistrationTask = new ModelFeatureOperatorRegistrySetupTask<>(
                "ModelFeatureTransformerRegistration",
                executor,
                TransformerRegistryFactory.getInstance().getSingleton(),
                Transformer.class
        );
        modelFeatureTransformerRegistrationTask.executeTask();
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

    @Override
    protected Class<?> getResourceClass() {
        return BidRequestEvaluatorOnRuleBasedModelTest.class;
    }
}
