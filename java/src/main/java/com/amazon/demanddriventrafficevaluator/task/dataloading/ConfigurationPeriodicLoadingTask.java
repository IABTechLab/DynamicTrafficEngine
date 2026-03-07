// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task.dataloading;

import com.amazon.demanddriventrafficevaluator.repository.loader.DefaultLoader;
import com.amazon.demanddriventrafficevaluator.repository.loader.configuration.ConfigurationLoaderInput;
import com.amazon.demanddriventrafficevaluator.task.PeriodicTaskWithRandomizedStart;
import com.amazon.demanddriventrafficevaluator.util.PropertiesUtil;
import org.apache.commons.configuration2.Configuration;

import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * A periodic task for loading configuration data at regular intervals.
 * <p>
 * This class extends PeriodicTaskWithRandomizedStart to provide a mechanism for
 * periodically loading configuration data from a specified S3 bucket. It uses a
 * DefaultLoader to handle the actual loading process.
 * </p>
 */
public class ConfigurationPeriodicLoadingTask extends PeriodicTaskWithRandomizedStart {

    private final DefaultLoader<ConfigurationLoaderInput> configurationLoader;
    private final String configurationType;
    private final String s3Bucket;

    public ConfigurationPeriodicLoadingTask(
            String sspIdentifier,
            String taskName,
            long periodMs,
            ScheduledThreadPoolExecutor executor,
            DefaultLoader<ConfigurationLoaderInput> configurationLoader,
            String configurationType,
            String s3Bucket
    ) {
        super(sspIdentifier, taskName, periodMs, executor);
        this.configurationLoader = configurationLoader;
        this.configurationType = configurationType;
        this.s3Bucket = s3Bucket;
    }

    /**
     * Executes the configuration loading task.
     * <p>
     * This method retrieves the S3 bucket properties, determines the appropriate
     * S3 bucket, and triggers the configuration loading process using the
     * configurationLoader.
     * </p>
     */
    @Override
    public void executeTask() {
        Configuration fileSharingS3BucketProperties = PropertiesUtil.getFileSharingS3BucketProperties();
        String s3Bucket = fileSharingS3BucketProperties.getString("adsp", this.s3Bucket);
        configurationLoader.load(new ConfigurationLoaderInput(s3Bucket, "config.json", getSspIdentifier(), configurationType));
    }

    /**
     * Initializes the task by executing it once and then scheduling it for
     * periodic execution.
     * <p>
     * This method ensures that the configuration is loaded immediately upon
     * initialization and then sets up the periodic execution schedule.
     * </p>
     */
    @Override
    public void initialize() {
        executeTask();
        schedulePeriodically();
    }
}
