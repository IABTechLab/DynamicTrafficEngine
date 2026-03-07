// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.provider.configuration;

import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;

import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_IDENTIFIER_CONFIGURATION;
import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_KEY_EXPERIMENT_CONFIGURATION;

/**
 * A provider class for retrieving ExperimentConfiguration from a cache.
 * <p>
 * This class implements the ConfigurationProvider interface specifically for
 * ExperimentConfiguration objects. It uses a Dao to retrieve the configuration
 * from a cache.
 * </p>
 */
public class ExperimentConfigurationProvider implements ConfigurationProvider<ExperimentConfiguration> {

    private final Dao<String, ExperimentConfiguration> cacheDao;

    public ExperimentConfigurationProvider(Dao<String, ExperimentConfiguration> cacheDao) {
        this.cacheDao = cacheDao;
    }

    /**
     * Provides the ExperimentConfiguration by retrieving it from the cache.
     * <p>
     * This method attempts to fetch the ExperimentConfiguration from the cache
     * using predefined cache identifier and key constants. If the configuration
     * is not found in the cache, it throws an IllegalStateException.
     * </p>
     *
     * @return The ExperimentConfiguration retrieved from the cache.
     * @throws IllegalStateException if the ExperimentConfiguration cannot be retrieved from the cache.
     */
    @Override
    public ExperimentConfiguration provide() {
        return cacheDao.get(CACHE_IDENTIFIER_CONFIGURATION, CACHE_KEY_EXPERIMENT_CONFIGURATION)
                .orElseThrow(() -> new IllegalStateException("Cannot get Experiment Definitions from the cache"));
    }
}
