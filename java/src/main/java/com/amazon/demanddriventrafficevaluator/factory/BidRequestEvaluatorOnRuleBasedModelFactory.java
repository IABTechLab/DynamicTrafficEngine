// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluatorOnRuleBasedModel;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluationResultsAggregator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluationResultsMaxAggregator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.RuleBasedModelEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.experiment.ExperimentManager;
import com.amazon.demanddriventrafficevaluator.modelfeature.Extraction;
import com.amazon.demanddriventrafficevaluator.modelfeature.Transformation;
import com.amazon.demanddriventrafficevaluator.modelfeature.extractor.ExtractorRegistry;
import com.amazon.demanddriventrafficevaluator.modelfeature.transformer.TransformerRegistry;
import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.localcache.LocalCacheRegistry;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ConfigurationProvider;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ModelConfigurationProvider;
import com.amazon.demanddriventrafficevaluator.repository.provider.model.ModelResultProvider;
import com.amazon.demanddriventrafficevaluator.repository.provider.model.RuleBasedModelResultProvider;
import com.amazon.demanddriventrafficevaluator.task.TaskInitializer;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.util.concurrent.ScheduledThreadPoolExecutor;

public class BidRequestEvaluatorOnRuleBasedModelFactory extends BidRequestEvaluatorFactory {

    final String sspIdentifier;
    final DefaultTaskInitializerFactory defaultTaskInitializerFactory;

    public BidRequestEvaluatorOnRuleBasedModelFactory(String supplierName, AwsCredentialsProvider credentialsProvider, String region, String bucket) {
        this.sspIdentifier = supplierName;
        this.defaultTaskInitializerFactory = new DefaultTaskInitializerFactory(supplierName, credentialsProvider, region, bucket);
    }

    public BidRequestEvaluatorOnRuleBasedModelFactory(String supplierName, AwsCredentialsProvider credentialsProvider, String region, String bucket, ScheduledThreadPoolExecutor executor) {
        this.sspIdentifier = supplierName;
        this.defaultTaskInitializerFactory = new DefaultTaskInitializerFactory(supplierName, credentialsProvider, region, bucket, executor);
    }

    public TaskInitializer getTaskInitializer() {
        return defaultTaskInitializerFactory.getTaskInitializer();
    }

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
        LocalCacheRegistry localCacheRegistry = DefaultLocalCacheRegistryFactory.getInstance().getDefaultLocalCacheRegistrySingleton();
        Dao<String, Double> ruleBasedModelResultDao = new LocalCacheDao<>(localCacheRegistry);
        ModelResultProvider ruleBasedmodelResultProvider = new RuleBasedModelResultProvider(ruleBasedModelResultDao);
        return new RuleBasedModelEvaluator(extraction, transformation, ruleBasedmodelResultProvider);
    }

    ModelEvaluationResultsAggregator provideModelEvaluationResultsAggregator() {
        return new ModelEvaluationResultsMaxAggregator();
    }
}
