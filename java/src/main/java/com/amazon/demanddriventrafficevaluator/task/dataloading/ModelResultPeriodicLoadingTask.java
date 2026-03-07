// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task.dataloading;

import com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelDefinition;
import com.amazon.demanddriventrafficevaluator.repository.loader.DefaultLoader;
import com.amazon.demanddriventrafficevaluator.repository.loader.model.ModelResultLoaderInput;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ConfigurationProvider;
import com.amazon.demanddriventrafficevaluator.task.PeriodicTaskWithRandomizedStart;
import com.amazon.demanddriventrafficevaluator.util.PropertiesUtil;
import org.apache.commons.configuration2.Configuration;

import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * A periodic task for loading model results at regular intervals.
 * <p>
 * This class extends PeriodicTaskWithRandomizedStart to provide a mechanism for
 * periodically loading model results from a specified S3 bucket. It uses a
 * ConfigurationProvider to obtain model configurations and a DefaultLoader to
 * handle the actual loading process for each model.
 * </p>
 */
public class ModelResultPeriodicLoadingTask extends PeriodicTaskWithRandomizedStart {

    private final ConfigurationProvider<ModelConfiguration> modelConfigurationProvider;
    private final DefaultLoader<ModelResultLoaderInput> modelResultLoader;
    private final String s3Bucket;


    public ModelResultPeriodicLoadingTask(
            String sspIdentifier,
            String taskName,
            long periodMs,
            ScheduledThreadPoolExecutor executor,
            ConfigurationProvider<ModelConfiguration> modelConfigurationProvider,
            DefaultLoader<ModelResultLoaderInput> modelResultLoader,
            String s3Bucket
    ) {
        super(sspIdentifier, taskName, periodMs, executor);
        this.modelConfigurationProvider = modelConfigurationProvider;
        this.modelResultLoader = modelResultLoader;
        this.s3Bucket = s3Bucket;
    }

    /**
     * Executes the model result loading task.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Retrieves the current model configuration</li>
     *   <li>Obtains the S3 bucket information from properties</li>
     *   <li>Iterates through each model definition in the configuration</li>
     *   <li>Creates a ModelResultLoaderInput for each model</li>
     *   <li>Triggers the loading process for each model's results</li>
     * </ol>
     * </p>
     */
    @Override
    public void executeTask() {
        ModelConfiguration modelConfiguration = modelConfigurationProvider.provide();
        Configuration fileSharingS3BucketProperties = PropertiesUtil.getFileSharingS3BucketProperties();
        String s3Bucket = fileSharingS3BucketProperties.getString("adsp", this.s3Bucket);

        for (ModelDefinition modelDefinition : modelConfiguration.getModelDefinitionByIdentifier().values()) {
            ModelResultLoaderInput modelResultLoaderInput = new ModelResultLoaderInput(
                    s3Bucket,
                    modelDefinition.getIdentifier() + ".csv",
                    getSspIdentifier(),
                    modelDefinition.getIdentifier(),
                    modelDefinition.getType()
            );
            modelResultLoader.load(modelResultLoaderInput);
        }
    }

    /**
     * Initializes the task by scheduling it for periodic execution.
     * <p>
     * This method sets up the periodic execution schedule for the task.
     * Unlike some other tasks, this does not perform an immediate execution
     * upon initialization.
     * </p>
     */
    @Override
    public void initialize() {
        schedulePeriodically();
    }
}
