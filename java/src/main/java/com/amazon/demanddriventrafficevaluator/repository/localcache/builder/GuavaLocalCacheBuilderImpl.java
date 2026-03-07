// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.localcache.builder;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;

/**
 * An implementation of LocalCacheBuilder that uses Guava's CacheBuilder to construct caches.
 * <p>
 * This class provides a way to build local caches with configurable properties such as
 * expiration times, maximum size, and concurrency level. It uses Guava's CacheBuilder
 * as the underlying mechanism for cache construction.
 * </p>
 */
public class GuavaLocalCacheBuilderImpl implements LocalCacheBuilder {

    private final RemovalListener removalListener;

    public GuavaLocalCacheBuilderImpl(RemovalListener removalListener) {
        this.removalListener = removalListener;
    }

    /**
     * Builds a Cache based on the provided configuration.
     * <p>
     * This method creates a new Guava Cache with the following properties:
     * <ul>
     *   <li>Expiration times (write and/or access based) as specified in the config</li>
     *   <li>Maximum size as specified in the config</li>
     *   <li>Concurrency level as specified in the config</li>
     *   <li>Removal listener as provided in the constructor</li>
     * </ul>
     * </p>
     *
     * @param localCacheBuilderConfig The configuration specifying the cache properties.
     * @return A new Cache instance configured according to the provided specifications.
     */
    @Override
    public Cache build(LocalCacheBuilderConfig localCacheBuilderConfig) {
        CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder();
        populateBuilder(localCacheBuilderConfig, cacheBuilder::expireAfterWrite, cacheBuilder::expireAfterAccess);
        return cacheBuilder
                .removalListener(removalListener)
                .maximumSize((long) localCacheBuilderConfig.getMaximumSize())
                .concurrencyLevel(localCacheBuilderConfig.getConcurrencyLevel())
                .build();
    }
}
