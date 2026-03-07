// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.provider.configuration;

import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration;

import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_IDENTIFIER_CONFIGURATION;
import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_KEY_MODEL_CONFIGURATION;

/**
 * A provider class for retrieving ModelConfiguration from a cache.
 * <p>
 * This class implements the ConfigurationProvider interface specifically for
 * ModelConfiguration objects. It uses a Dao to retrieve the configuration
 * from a cache.
 * </p>
 */
public class ModelConfigurationProvider implements ConfigurationProvider<ModelConfiguration> {

    private final Dao<String, ModelConfiguration> cacheDao;

    public ModelConfigurationProvider(Dao<String, ModelConfiguration> cacheDao) {
        this.cacheDao = cacheDao;
    }

    /**
     * Provides the ModelConfiguration by retrieving it from the cache.
     * <p>
     * This method attempts to fetch the ModelConfiguration from the cache
     * using predefined cache identifier and key constants. If the configuration
     * is not found in the cache, it throws an IllegalStateException.
     * </p>
     *
     * @return The ModelConfiguration retrieved from the cache.
     * @throws IllegalStateException if the ModelConfiguration cannot be retrieved from the cache.
     */
    @Override
    public ModelConfiguration provide() {
        return cacheDao.get(CACHE_IDENTIFIER_CONFIGURATION, CACHE_KEY_MODEL_CONFIGURATION)
                .orElseThrow(() -> new IllegalStateException("Cannot get Model Configuration from the cache"));
    }
}
