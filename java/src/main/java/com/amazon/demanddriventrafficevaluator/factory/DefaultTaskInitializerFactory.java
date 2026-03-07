// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeatureOperator;
import com.amazon.demanddriventrafficevaluator.modelfeature.Registry;
import com.amazon.demanddriventrafficevaluator.modelfeature.extractor.Extractor;
import com.amazon.demanddriventrafficevaluator.modelfeature.transformer.Transformer;
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
import com.amazon.demanddriventrafficevaluator.task.InitializerTask;
import com.amazon.demanddriventrafficevaluator.task.InitializerTaskOnOneShotTask;
import com.amazon.demanddriventrafficevaluator.task.InitializerTaskOnPeriodicTask;
import com.amazon.demanddriventrafficevaluator.task.TaskConfiguration;
import com.amazon.demanddriventrafficevaluator.task.TaskInitializer;
import com.amazon.demanddriventrafficevaluator.task.dataloading.ConfigurationPeriodicLoadingTask;
import com.amazon.demanddriventrafficevaluator.task.dataloading.ModelResultPeriodicLoadingInitializerTask;
import com.amazon.demanddriventrafficevaluator.task.dataloading.ModelResultPeriodicLoadingTask;
import com.amazon.demanddriventrafficevaluator.task.registrysetup.ModelFeatureOperatorRegistrySetupTask;
import com.amazon.demanddriventrafficevaluator.util.PropertiesUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.configuration2.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_KEY_EXPERIMENT_CONFIGURATION;
import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_KEY_MODEL_CONFIGURATION;

/**
 * A factory class for creating and configuring task initializers and related components.
 * <p>
 * This class is responsible for setting up various initialization tasks, including
 * configuration loading, model result loading, and feature extractor/transformer registration.
 * It provides methods to create and configure these tasks with appropriate settings and dependencies.
 * </p>
 */
@Log4j2
public class DefaultTaskInitializerFactory {

    private static final long DEFAULT_OVERALL_EXECUTION_TIMEOUT = 600000L;
    private static final long DEFAULT_PERIOD_MS = 300000L;
    private static final int DEFAULT_MAXIMUM_ATTEMPTS = 5;
    private static final long DEFAULT_MIN_DELAY_BEFORE_ATTEMPT_MS = 100L;
    private static final long DEFAULT_MAX_DELAY_BEFORE_ATTEMPT_MS = 30000L;

    private final ObjectMapper mapper = new ObjectMapper();
    private final LocalCacheRegistry localCacheRegistry = DefaultLocalCacheRegistryFactory.getInstance().getDefaultLocalCacheRegistrySingleton();

    private final String sspIdentifier;
    private final S3Client s3Client;
    private final Dao<String, InputStream> fileDao;
    private final String bucket;

    private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(5);

    public DefaultTaskInitializerFactory(String sspIdentifier, AwsCredentialsProvider credentialsProvider, String region, String bucket) {
        this.sspIdentifier = sspIdentifier;
        this.s3Client = AWSServiceClientFactory.getInstance().getS3Client(credentialsProvider, region);
        this.fileDao = new S3ObjectDao(s3Client);
        this.bucket = bucket;
    }

    public DefaultTaskInitializerFactory(String sspIdentifier, AwsCredentialsProvider credentialsProvider, String region, String bucket, ScheduledThreadPoolExecutor executor) {
        this.sspIdentifier = sspIdentifier;
        this.s3Client = AWSServiceClientFactory.getInstance().getS3Client(credentialsProvider, region);
        this.fileDao = new S3ObjectDao(s3Client);
        this.bucket = bucket;
        this.executor = executor;
    }

    public TaskInitializer getTaskInitializer() {
        log.warn("getTaskInitializer Initializing task initializer");
        Configuration taskProperties = PropertiesUtil.getTaskProperties();
        long overallTimeoutMs = taskProperties.getLong("overall.execution-timeout", DEFAULT_OVERALL_EXECUTION_TIMEOUT);
        if (overallTimeoutMs <= 0L) {
            throw new IllegalStateException("Invalid overall execution timeout which should be larger than 0.");
        }
        return new TaskInitializer(getStageOneTasks(), getStageTwoTasks(), overallTimeoutMs);
    }

    private List<InitializerTask> getStageOneTasks() {
        InitializerTask experimentConfigurationPeriodicLoadingTask = getInitializerTaskForPeriodicLoadingExperimentConfiguration();
        InitializerTask modelConfigurationPeriodicLoadingTask = getInitializerTaskForPeriodicLoadingModelConfiguration();
        InitializerTask ModelFeatureExtractorRegistrationInitializerTask = getInitializerTaskForRegisteringModelFeatureExtractor();
        InitializerTask ModelFeatureTransformerRegistrationInitializerTask = getInitializerTaskForRegisteringModelFeatureTransformer();
        return List.of(
                experimentConfigurationPeriodicLoadingTask,
                modelConfigurationPeriodicLoadingTask,
                ModelFeatureExtractorRegistrationInitializerTask,
                ModelFeatureTransformerRegistrationInitializerTask
        );
    }

    private List<InitializerTask> getStageTwoTasks() {
        InitializerTask modelResultPeriodicLoadingInitializerTask = getInitializerTaskForPeriodicLoadingRuleBasedModelResult();
        return List.of(modelResultPeriodicLoadingInitializerTask);
    }

    private InitializerTaskOnPeriodicTask getInitializerTaskForPeriodicLoadingModelConfiguration() {
        LocalCacheDao<String, ModelConfiguration> modelConfigurationCacheDao = new LocalCacheDao<>(localCacheRegistry);
        Dao<String, String> fileIdentifierCacheDao = new LocalCacheDao<>(localCacheRegistry);
        DefaultLoader<ConfigurationLoaderInput> modelConfigurationLoader = new DefaultConfigurationLoader<>(
                fileIdentifierCacheDao, fileDao, modelConfigurationCacheDao, CACHE_KEY_MODEL_CONFIGURATION, ModelConfiguration.class,
                mapper
        );
        TaskConfiguration taskConfiguration = getTaskConfigurationFromProperties("model-configuration");
        return getInitializerTaskForPeriodicLoadingConfiguration(
                "ModelConfigurationPeriodicLoading",
                taskConfiguration.getPeriodMs(),
                taskConfiguration.getMaximumAttempts(),
                taskConfiguration.getMinDelayBeforeAttemptMs(),
                taskConfiguration.getMaxDelayBeforeAttemptMs(),
                executor,
                modelConfigurationLoader,
                "model"
        );
    }

    private InitializerTaskOnPeriodicTask getInitializerTaskForPeriodicLoadingExperimentConfiguration() {
        LocalCacheDao<String, ExperimentConfiguration> experimentConfigurationCacheDao = new LocalCacheDao<>(localCacheRegistry);
        Dao<String, String> fileIdentifierCacheDao = new LocalCacheDao<>(localCacheRegistry);
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
        TaskConfiguration taskConfiguration = getTaskConfigurationFromProperties("experiment-configuration");
        return getInitializerTaskForPeriodicLoadingConfiguration(
                "ExperimentConfigurationPeriodicLoading",
                taskConfiguration.getPeriodMs(),
                taskConfiguration.getMaximumAttempts(),
                taskConfiguration.getMinDelayBeforeAttemptMs(),
                taskConfiguration.getMaxDelayBeforeAttemptMs(),
                executor,
                experimentConfigurationLoader,
                "experiment"
        );
    }

    private InitializerTask getInitializerTaskForPeriodicLoadingRuleBasedModelResult() {
        LocalCacheDao<String, ModelConfiguration> modelConfigurationCacheDao = new LocalCacheDao<>(localCacheRegistry);
        ConfigurationProvider<ModelConfiguration> modelConfigurationProvider = new ModelConfigurationProvider(modelConfigurationCacheDao);
        Dao<String, Double> modelResultsCacheDao = new LocalCacheDao<>(localCacheRegistry);
        Dao<String, String> fileIdentifierCacheDao = new LocalCacheDao<>(localCacheRegistry);
        DefaultLoader<ModelResultLoaderInput> modelResultLoader = new RuleBasedModelResultLoader(
                fileIdentifierCacheDao, modelResultsCacheDao, fileDao
        );
        TaskConfiguration taskConfiguration = getTaskConfigurationFromProperties("model-result.rule-based");
        return getInitializerTaskForPeriodicLoadingModelResult(
                "RuleBasedModelResultPeriodicLoading",
                taskConfiguration.getPeriodMs(),
                taskConfiguration.getMaximumAttempts(),
                taskConfiguration.getMinDelayBeforeAttemptMs(),
                taskConfiguration.getMaxDelayBeforeAttemptMs(),
                executor,
                modelConfigurationProvider,
                modelResultLoader
        );
    }


    private InitializerTaskOnPeriodicTask getInitializerTaskForPeriodicLoadingConfiguration(
            String taskName,
            long periodMs,
            int maximumAttempts,
            long minDelayBeforeAttemptMs,
            long maxDelayBeforeAttemptMs,
            ScheduledThreadPoolExecutor executor,
            DefaultLoader<ConfigurationLoaderInput> configurationLoader,
            String configType
    ) {
        ConfigurationPeriodicLoadingTask periodicLoadingTask = new ConfigurationPeriodicLoadingTask(
                sspIdentifier,
                taskName,
                periodMs,
                executor,
                configurationLoader,
                configType,
                bucket
        );

        return new InitializerTaskOnPeriodicTask(
                taskName + "Initializer",
                maximumAttempts,
                minDelayBeforeAttemptMs,
                maxDelayBeforeAttemptMs,
                periodicLoadingTask
        );
    }

    private InitializerTask getInitializerTaskForPeriodicLoadingModelResult(
            String taskName,
            long periodMs,
            int maximumAttempts,
            long minDelayBeforeAttemptMs,
            long maxDelayBeforeAttemptMs,
            ScheduledThreadPoolExecutor executor,
            ConfigurationProvider<ModelConfiguration> modelConfigurationProvider,
            DefaultLoader<ModelResultLoaderInput> modelResultLoader
    ) {
        ModelResultPeriodicLoadingTask task = new ModelResultPeriodicLoadingTask(
                sspIdentifier,
                taskName,
                periodMs,
                executor,
                modelConfigurationProvider,
                modelResultLoader,
                bucket
        );
        return new ModelResultPeriodicLoadingInitializerTask(
                taskName + "Initializer",
                maximumAttempts,
                minDelayBeforeAttemptMs,
                maxDelayBeforeAttemptMs,
                task
        );
    }

    private InitializerTask getInitializerTaskForRegisteringModelFeatureExtractor() {
        TaskConfiguration taskConfiguration = getTaskConfigurationFromProperties("model-feature.extractor");
        return getInitializerTaskForRegisteringModelFeatureOperator(
                "ModelFeatureExtractorRegistration",
                executor,
                ExtractorRegistryFactory.getInstance().getSingleton(),
                Extractor.class,
                taskConfiguration.getMaximumAttempts(),
                taskConfiguration.getMinDelayBeforeAttemptMs(),
                taskConfiguration.getMaxDelayBeforeAttemptMs()
        );
    }

    private InitializerTask getInitializerTaskForRegisteringModelFeatureTransformer() {
        TaskConfiguration taskConfiguration = getTaskConfigurationFromProperties("model-feature.transformer");
        return getInitializerTaskForRegisteringModelFeatureOperator(
                "ModelFeatureTransformerRegistration",
                executor,
                TransformerRegistryFactory.getInstance().getSingleton(),
                Transformer.class,
                taskConfiguration.getMaximumAttempts(),
                taskConfiguration.getMinDelayBeforeAttemptMs(),
                taskConfiguration.getMaxDelayBeforeAttemptMs()
        );
    }

    private <T extends ModelFeatureOperator> InitializerTaskOnOneShotTask getInitializerTaskForRegisteringModelFeatureOperator(
            String taskName,
            ScheduledThreadPoolExecutor executor,
            Registry<T> registry,
            Class<T> type,
            int maximumAttempts,
            long minDelayBeforeAttemptMs,
            long maxDelayBeforeAttemptMs
    ) {

        ModelFeatureOperatorRegistrySetupTask<T> task = new ModelFeatureOperatorRegistrySetupTask<>(
                taskName,
                executor,
                registry,
                type
        );
        return new InitializerTaskOnOneShotTask(
                taskName + "Initializer",
                maximumAttempts,
                minDelayBeforeAttemptMs,
                maxDelayBeforeAttemptMs,
                task
        );
    }

    private TaskConfiguration getTaskConfigurationFromProperties(String taskType) {
        Configuration taskProperties = PropertiesUtil.getTaskProperties();
        long periodMs = taskProperties.getLong("period.ms." + taskType,
                taskProperties.getLong("period.ms", DEFAULT_PERIOD_MS));
        int maximumAttempts = taskProperties.getInt("maximum.attempts." + taskType,
                taskProperties.getInt("maximum.attempts", DEFAULT_MAXIMUM_ATTEMPTS));
        long minDelayBeforeAttemptMs = taskProperties.getLong("min.delay.before.attempt.ms." + taskType,
                taskProperties.getLong("min.delay.before.attempt.ms", DEFAULT_MIN_DELAY_BEFORE_ATTEMPT_MS));
        long maxDelayBeforeAttemptMs = taskProperties.getLong("max.delay.before.attempt.ms." + taskType,
                taskProperties.getLong("max.delay.before.attempt.ms", DEFAULT_MAX_DELAY_BEFORE_ATTEMPT_MS));
        return TaskConfiguration.builder()
                .maximumAttempts(maximumAttempts)
                .minDelayBeforeAttemptMs(minDelayBeforeAttemptMs)
                .maxDelayBeforeAttemptMs(maxDelayBeforeAttemptMs)
                .periodMs(periodMs)
                .build();
    }
}
