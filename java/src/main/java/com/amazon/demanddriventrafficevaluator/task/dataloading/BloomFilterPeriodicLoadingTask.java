// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task.dataloading;

import com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelDefinition;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelFormat;
import com.amazon.demanddriventrafficevaluator.repository.loader.DefaultLoader;
import com.amazon.demanddriventrafficevaluator.repository.loader.model.ModelResultLoaderInput;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ConfigurationProvider;
import com.amazon.demanddriventrafficevaluator.task.PeriodicTaskWithRandomizedStart;
import com.amazon.demanddriventrafficevaluator.util.PropertiesUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.configuration2.Configuration;

import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * A periodic task for loading bloom filter model results at regular intervals.
 * <p>
 * This class extends PeriodicTaskWithRandomizedStart to provide a mechanism for
 * periodically loading bloom filter models from a specified S3 bucket. It iterates
 * over model definitions, filters for models with {@code modelFormat == BLOOM_FILTER},
 * and invokes the BloomFilterModelLoader for each. Rule-based models are skipped,
 * and individual model load failures do not interrupt the refresh cycle.
 * </p>
 */
@Log4j2
public class BloomFilterPeriodicLoadingTask extends PeriodicTaskWithRandomizedStart {

    private final ConfigurationProvider<ModelConfiguration> modelConfigurationProvider;
    private final DefaultLoader<ModelResultLoaderInput> bloomFilterModelLoader;
    private final String s3Bucket;

    public BloomFilterPeriodicLoadingTask(
            String sspIdentifier,
            String taskName,
            long periodMs,
            ScheduledThreadPoolExecutor executor,
            ConfigurationProvider<ModelConfiguration> modelConfigurationProvider,
            DefaultLoader<ModelResultLoaderInput> bloomFilterModelLoader,
            String s3Bucket
    ) {
        super(sspIdentifier, taskName, periodMs, executor);
        this.modelConfigurationProvider = modelConfigurationProvider;
        this.bloomFilterModelLoader = bloomFilterModelLoader;
        this.s3Bucket = s3Bucket;
    }

    /**
     * Executes the bloom filter model loading task.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Retrieves the current model configuration</li>
     *   <li>Obtains the S3 bucket information from properties</li>
     *   <li>Iterates through each model definition in the configuration</li>
     *   <li>Skips models that are not BLOOM_FILTER format</li>
     *   <li>Creates a ModelResultLoaderInput for each bloom filter model</li>
     *   <li>Triggers the loading process for each bloom filter model</li>
     *   <li>Continues loading remaining models if an individual model fails</li>
     * </ol>
     * </p>
     */
    @Override
    public void executeTask() {
        ModelConfiguration modelConfiguration = modelConfigurationProvider.provide();
        Configuration fileSharingS3BucketProperties = PropertiesUtil.getFileSharingS3BucketProperties();
        String s3Bucket = fileSharingS3BucketProperties.getString("adsp", this.s3Bucket);

        for (ModelDefinition modelDefinition : modelConfiguration.getModelDefinitionByIdentifier().values()) {
            if (modelDefinition.getModelFormat() != ModelFormat.BLOOM_FILTER) {
                continue;
            }

            try {
                ModelResultLoaderInput modelResultLoaderInput = new ModelResultLoaderInput(
                        s3Bucket,
                        modelDefinition.getIdentifier(),
                        getSspIdentifier(),
                        modelDefinition.getIdentifier(),
                        modelDefinition.getType(),
                        modelDefinition.getS3PathMode()
                );
                bloomFilterModelLoader.load(modelResultLoaderInput);
            } catch (Exception e) {
                log.error("Failed to load bloom filter model {}: {}", modelDefinition.getIdentifier(), e.getMessage(), e);
            }
        }
    }

    /**
     * Initializes the task by scheduling it for periodic execution.
     * <p>
     * This method sets up the periodic execution schedule for the task.
     * Unlike some other tasks, this does not perform an immediate execution
     * upon initialization: the first execution runs after a random delay of
     * up to one period, and {@link #hasFinishedFirstRun()} tells when it has finished.
     * </p>
     */
    @Override
    public void initialize() {
        schedulePeriodically();
    }
}
