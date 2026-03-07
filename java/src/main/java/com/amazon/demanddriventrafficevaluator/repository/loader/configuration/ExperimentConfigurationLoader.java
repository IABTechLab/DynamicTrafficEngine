// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.loader.configuration;

import com.amazon.demanddriventrafficevaluator.evaluation.experiment.TreatmentAllocator;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.loader.DefaultLoader;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ConfigurationProvider;
import lombok.extern.log4j.Log4j2;

/**
 * A loader class responsible for loading and updating experiment configurations.
 * <p>
 * This class extends DefaultLoader and specializes in loading experiment configurations.
 * It not only loads the configuration but also updates the treatment allocator with the new configuration.
 * </p>
 */
@Log4j2
public class ExperimentConfigurationLoader extends DefaultLoader<ConfigurationLoaderInput> {

    private final DefaultConfigurationLoader<ExperimentConfiguration> configurationLoader;
    private final ConfigurationProvider<ExperimentConfiguration> configurationProvider;
    private final TreatmentAllocator treatmentAllocator;

    public ExperimentConfigurationLoader(DefaultConfigurationLoader<ExperimentConfiguration> configurationLoader, ConfigurationProvider<ExperimentConfiguration> configurationProvider, TreatmentAllocator treatmentAllocator) {
        this.configurationLoader = configurationLoader;
        this.configurationProvider = configurationProvider;
        this.treatmentAllocator = treatmentAllocator;
    }

    /**
     * Loads the experiment configuration and updates the treatment allocator.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Attempts to load the configuration using the configurationLoader</li>
     *   <li>If successful, retrieves the loaded configuration from the configurationProvider</li>
     *   <li>Updates the treatmentAllocator with the new configuration</li>
     * </ol>
     * </p>
     *
     * @param input The input required for loading the configuration.
     * @return true if the configuration was successfully loaded and the allocator updated, false otherwise.
     * @throws IllegalStateException if an error occurs during the loading or updating process.
     */
    @Override
    public boolean load(ConfigurationLoaderInput input) {
        try {
            boolean isLoaded = this.configurationLoader.load(input);
            if (isLoaded) {
                log.debug("Experiment Configuration is loaded and start update the configuration for the treatment allocator.");
                ExperimentConfiguration configuration = this.configurationProvider.provide();
                this.treatmentAllocator.updateConfiguration(configuration);
                return true;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Fail to load Experiment Configuration due to ", e);
        }
        return false;
    }

    /**
     * Retrieves the S3 object key for the configuration.
     * <p>
     * This method delegates to the underlying configurationLoader to get the S3 object key.
     * </p>
     *
     * @param input The input required for determining the S3 object key.
     * @return The S3 object key as a String.
     */
    @Override
    public String getS3ObjectKey(ConfigurationLoaderInput input) {
        return this.configurationLoader.getS3ObjectKey(input);
    }
}
