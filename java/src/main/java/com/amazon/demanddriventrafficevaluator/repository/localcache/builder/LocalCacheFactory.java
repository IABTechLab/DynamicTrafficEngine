// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.localcache.builder;

import com.amazon.demanddriventrafficevaluator.repository.localcache.exception.LocalCacheCreationException;
import com.google.common.cache.Cache;

import java.util.Map;

/**
 * A factory class for creating local cache instances based on provided configurations.
 * <p>
 * This class manages multiple versions of LocalCacheBuilders and uses them to create
 * cache instances as requested. It allows for flexibility in cache creation by supporting
 * different builder versions.
 * </p>
 */
public class LocalCacheFactory {

    private final Map<String, LocalCacheBuilder> localCacheBuilderMap;

    public LocalCacheFactory(Map<String, LocalCacheBuilder> localCacheBuilderMap) {
        this.localCacheBuilderMap = localCacheBuilderMap;
    }

    /**
     * Creates and returns a Cache instance based on the provided configuration.
     * <p>
     * This method selects the appropriate LocalCacheBuilder based on the version
     * specified in the configuration and uses it to build the cache. If no builder
     * is found for the specified version, it throws an exception.
     * </p>
     *
     * @param localCacheBuilderConfig The configuration specifying the cache properties
     *                                and the required builder version.
     * @return A new Cache instance configured according to the provided specifications.
     * @throws LocalCacheCreationException if no builder is found for the specified version
     *                                     or if there's an error during cache creation.
     */
    public Cache getLocalCache(LocalCacheBuilderConfig localCacheBuilderConfig) throws LocalCacheCreationException {
        if (!localCacheBuilderMap.containsKey(localCacheBuilderConfig.getLocalCacheBuilderVersion())) {
            throw new LocalCacheCreationException("Unknown local cache builder version: "
                    + localCacheBuilderConfig.getLocalCacheBuilderVersion() + " for cache: "
                    + localCacheBuilderConfig.getCacheName());
        }
        return localCacheBuilderMap.get(localCacheBuilderConfig.getLocalCacheBuilderVersion())
                .build(localCacheBuilderConfig);
    }
}
