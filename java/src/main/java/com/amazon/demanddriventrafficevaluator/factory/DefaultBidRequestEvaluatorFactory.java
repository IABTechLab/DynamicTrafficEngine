// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluatorOnRuleBasedModel;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BloomFilterModelEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ConfigurableAggregator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.DelegatingModelEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluationResultsAggregator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.RuleBasedModelEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.experiment.ExperimentManager;
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
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelFormat;
import com.amazon.demanddriventrafficevaluator.repository.loader.DefaultLoader;
import com.amazon.demanddriventrafficevaluator.repository.loader.model.BloomFilterModelLoader;
import com.amazon.demanddriventrafficevaluator.repository.loader.model.ModelResultLoaderInput;
import com.amazon.demanddriventrafficevaluator.repository.localcache.LocalCacheRegistry;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ConfigurationProvider;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ModelConfigurationProvider;
import com.amazon.demanddriventrafficevaluator.repository.provider.model.BloomFilterModelResultProvider;
import com.amazon.demanddriventrafficevaluator.repository.provider.model.ModelResultProvider;
import com.amazon.demanddriventrafficevaluator.repository.provider.model.RuleBasedModelResultProvider;
import com.amazon.demanddriventrafficevaluator.task.InitializerTask;
import com.amazon.demanddriventrafficevaluator.task.InitializerTaskOnPeriodicTask;
import com.amazon.demanddriventrafficevaluator.task.PeriodicTaskWithRandomizedStart;
import com.amazon.demanddriventrafficevaluator.task.TaskConfiguration;
import com.amazon.demanddriventrafficevaluator.task.TaskInitializer;
import com.amazon.demanddriventrafficevaluator.task.dataloading.BloomFilterPeriodicLoadingTask;
import com.amazon.demanddriventrafficevaluator.task.dataloading.ModelResultPeriodicLoadingInitializerTask;
import com.amazon.demanddriventrafficevaluator.util.PropertiesUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.configuration2.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * A factory for creating BidRequestEvaluator instances that support both rule-based
 * and bloom filter model evaluation.
 * <p>
 * This factory extends {@link BidRequestEvaluatorFactory} and wires:
 * <ul>
 *   <li>A {@link DelegatingModelEvaluator} with both {@link RuleBasedModelEvaluator}
 *       and {@link BloomFilterModelEvaluator}</li>
 *   <li>A {@link ConfigurableAggregator} as the aggregator (with fallback to
 *       {@link com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluationResultsMaxAggregator}
 *       at evaluation time when the aggregation schema is null)</li>
 *   <li>A {@link TaskInitializer} with both rule-based and bloom filter loading tasks</li>
 * </ul>
 * </p>
 */
@Log4j2
public class DefaultBidRequestEvaluatorFactory extends BidRequestEvaluatorFactory {

    private static final long DEFAULT_PERIOD_MS = 300000L;
    private static final int DEFAULT_MAXIMUM_ATTEMPTS = 5;
    private static final long DEFAULT_MIN_DELAY_BEFORE_ATTEMPT_MS = 100L;
    private static final long DEFAULT_MAX_DELAY_BEFORE_ATTEMPT_MS = 30000L;

    final String sspIdentifier;
    final DefaultTaskInitializerFactory defaultTaskInitializerFactory;
    private final BloomFilterDao bloomFilterDao;
    private final String bucket;
    private final ScheduledThreadPoolExecutor executor;
    private final Dao<String, InputStream> fileDao;
    // The model loading tasks of the last TaskInitializer whose init() completed; null until one has.
    private volatile List<PeriodicTaskWithRandomizedStart> modelLoadingTasks;
    private volatile boolean ready;
    // The names the registry setup tasks register; listed once, on the first isReady() call that needs them.
    private volatile Set<String> extractorNames;
    private volatile Set<String> transformerNames;

    public DefaultBidRequestEvaluatorFactory(
            String supplierName,
            AwsCredentialsProvider credentialsProvider,
            String region,
            String bucket
    ) {
        this(supplierName, credentialsProvider, region, bucket, new ScheduledThreadPoolExecutor(5));
    }

    public DefaultBidRequestEvaluatorFactory(
            String supplierName,
            AwsCredentialsProvider credentialsProvider,
            String region,
            String bucket,
            ScheduledThreadPoolExecutor executor
    ) {
        this.sspIdentifier = supplierName;
        this.defaultTaskInitializerFactory = new DefaultTaskInitializerFactory(supplierName, credentialsProvider, region, bucket, executor);
        this.bloomFilterDao = new BloomFilterDao();
        this.bucket = bucket;
        this.executor = executor;
        this.fileDao = new S3ObjectDao(AWSServiceClientFactory.getInstance().getS3Client(credentialsProvider, region));
    }

    @Override
    public TaskInitializer getTaskInitializer() {
        log.info("getTaskInitializer Initializing task initializer with bloom filter support");
        Configuration taskProperties = PropertiesUtil.getTaskProperties();
        long overallTimeoutMs = taskProperties.getLong("overall.execution-timeout", 600000L);
        if (overallTimeoutMs <= 0L) {
            throw new IllegalStateException("Invalid overall execution timeout which should be larger than 0.");
        }

        List<InitializerTask> stageOneTasks = getStageOneTasks();
        ModelResultPeriodicLoadingInitializerTask ruleBasedTask =
                defaultTaskInitializerFactory.getInitializerTaskForPeriodicLoadingRuleBasedModelResult();
        InitializerTaskOnPeriodicTask bloomFilterTask = getInitializerTaskForPeriodicLoadingBloomFilterModelResult();
        List<InitializerTask> stageTwoTasks = List.of(ruleBasedTask, bloomFilterTask);
        List<PeriodicTaskWithRandomizedStart> tasks = List.of(ruleBasedTask.getTask(), bloomFilterTask.getTask());

        return new TaskInitializer(stageOneTasks, stageTwoTasks, overallTimeoutMs, () -> modelLoadingTasks = tasks);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This factory waits for the rule-based model-result and the bloom-filter loading tasks of the
     * {@link TaskInitializer} whose {@code init()} completed last.
     * </p>
     */
    @Override
    public boolean isReady() {
        if (ready) {
            return true;
        }
        List<PeriodicTaskWithRandomizedStart> tasks = modelLoadingTasks;
        if (tasks == null || !tasks.stream().allMatch(PeriodicTaskWithRandomizedStart::hasFinishedFirstRun)) {
            return false;
        }
        try {
            if (extractorNames == null || transformerNames == null) {
                extractorNames = getProviderNames(Extractor.class);
                transformerNames = getProviderNames(Transformer.class);
            }
        } catch (ServiceConfigurationError e) {
            log.error("isReady: cannot list the feature extractor and transformer providers", e);
            return false;
        }
        if (ExtractorRegistryFactory.getInstance().getSingleton().getRecords().keySet().containsAll(extractorNames)
                && TransformerRegistryFactory.getInstance().getSingleton().getRecords().keySet()
                .containsAll(transformerNames)) {
            ready = true;
        }
        return ready;
    }

    // The names the registry setup tasks register: the simple class names of the ServiceLoader providers.
    private static Set<String> getProviderNames(Class<?> type) {
        return ServiceLoader.load(type).stream()
                .map(provider -> provider.type().getSimpleName())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public BidRequestEvaluator getEvaluator() {
        ExperimentManager experimentManager = ExperimentManagerFactory.getInstance().provideExperimentManager();
        ConfigurationProvider<ModelConfiguration> modelConfigurationProvider = provideModelConfigurationProvider();
        ModelEvaluator modelEvaluator = provideModelEvaluator();
        ModelEvaluationResultsAggregator modelEvaluationResultsAggregator = provideModelEvaluationResultsAggregator();
        return new BidRequestEvaluatorOnRuleBasedModel(
                sspIdentifier,
                experimentManager,
                modelConfigurationProvider,
                modelEvaluator,
                modelEvaluationResultsAggregator
        );
    }

    ModelConfigurationProvider provideModelConfigurationProvider() {
        LocalCacheRegistry localCacheRegistry = DefaultLocalCacheRegistryFactory.getInstance().getDefaultLocalCacheRegistrySingleton();
        return new ModelConfigurationProvider(new LocalCacheDao<>(localCacheRegistry));
    }

    ModelEvaluator provideModelEvaluator() {
        ExtractorRegistry extractorRegistry = ExtractorRegistryFactory.getInstance().getSingleton();
        Extraction extraction = new Extraction(extractorRegistry);
        TransformerRegistry transformerRegistry = TransformerRegistryFactory.getInstance().getSingleton();
        Transformation transformation = new Transformation(transformerRegistry);

        // Rule-based evaluator
        LocalCacheRegistry localCacheRegistry = DefaultLocalCacheRegistryFactory.getInstance().getDefaultLocalCacheRegistrySingleton();
        Dao<String, Double> ruleBasedModelResultDao = new LocalCacheDao<>(localCacheRegistry);
        ModelResultProvider ruleBasedModelResultProvider = new RuleBasedModelResultProvider(ruleBasedModelResultDao);
        RuleBasedModelEvaluator ruleBasedModelEvaluator = new RuleBasedModelEvaluator(extraction, transformation, ruleBasedModelResultProvider);

        // Bloom filter evaluator
        ModelResultProvider bloomFilterModelResultProvider = new BloomFilterModelResultProvider(bloomFilterDao);
        BloomFilterModelEvaluator bloomFilterModelEvaluator = new BloomFilterModelEvaluator(extraction, transformation, bloomFilterModelResultProvider);

        // Delegating evaluator that routes based on ModelFormat
        Map<ModelFormat, ModelEvaluator> evaluatorsByFormat = Map.of(
                ModelFormat.RULE_BASED, ruleBasedModelEvaluator,
                ModelFormat.BLOOM_FILTER, bloomFilterModelEvaluator
        );
        return new DelegatingModelEvaluator(evaluatorsByFormat);
    }

    ModelEvaluationResultsAggregator provideModelEvaluationResultsAggregator() {
        return new ConfigurableAggregator();
    }

    private List<InitializerTask> getStageOneTasks() {
        return defaultTaskInitializerFactory.getStageOneTasks();
    }

    private InitializerTaskOnPeriodicTask getInitializerTaskForPeriodicLoadingBloomFilterModelResult() {
        LocalCacheRegistry localCacheRegistry = DefaultLocalCacheRegistryFactory.getInstance().getDefaultLocalCacheRegistrySingleton();
        LocalCacheDao<String, ModelConfiguration> modelConfigurationCacheDao = new LocalCacheDao<>(localCacheRegistry);
        ConfigurationProvider<ModelConfiguration> modelConfigurationProvider = new ModelConfigurationProvider(modelConfigurationCacheDao);
        Dao<String, String> fileIdentifierCacheDao = new LocalCacheDao<>(localCacheRegistry);
        DefaultLoader<ModelResultLoaderInput> bloomFilterModelLoader = new BloomFilterModelLoader(
                fileIdentifierCacheDao, bloomFilterDao, fileDao
        );
        TaskConfiguration taskConfiguration = getTaskConfigurationFromProperties("model-result.bloom-filter");
        BloomFilterPeriodicLoadingTask task = new BloomFilterPeriodicLoadingTask(
                sspIdentifier,
                "BloomFilterModelResultPeriodicLoading",
                taskConfiguration.getPeriodMs(),
                executor,
                modelConfigurationProvider,
                bloomFilterModelLoader,
                bucket
        );
        return new InitializerTaskOnPeriodicTask(
                "BloomFilterModelResultPeriodicLoadingInitializer",
                taskConfiguration.getMaximumAttempts(),
                taskConfiguration.getMinDelayBeforeAttemptMs(),
                taskConfiguration.getMaxDelayBeforeAttemptMs(),
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
