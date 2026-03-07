// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.localcache.builder;

import com.google.common.cache.Cache;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * An interface for building local cache instances with configurable properties.
 * <p>
 * This interface defines methods for constructing cache objects based on provided
 * configurations. It allows for flexible cache creation with various expiration
 * policies and other settings.
 * </p>
 */
public interface LocalCacheBuilder {

    /**
     * Builds a Cache instance based on the provided configuration.
     *
     * @param localCacheBuilderConfig The configuration specifying cache properties
     *                                such as expiration times, size limits, etc.
     * @return A new Cache instance configured according to the provided specifications.
     */
    Cache build(LocalCacheBuilderConfig localCacheBuilderConfig);

    /**
     * A default method to populate a cache builder with expiration settings.
     * <p>
     * This method sets either the expire-after-write or expire-after-access time
     * based on the provided configuration. If both are specified in the configuration,
     * expire-after-write takes precedence.
     * </p>
     *
     * @param localCacheBuilderConfig The configuration containing expiration settings.
     * @param expireAfterWrite        A BiConsumer to set the expire-after-write time.
     * @param expireAfterAccess       A BiConsumer to set the expire-after-access time.
     */
    default void populateBuilder(LocalCacheBuilderConfig localCacheBuilderConfig,
                                 BiConsumer<Integer, TimeUnit> expireAfterWrite,
                                 BiConsumer<Integer, TimeUnit> expireAfterAccess
    ) {
        if (localCacheBuilderConfig.getExpireAfterWriteSecs() != null) {
            expireAfterWrite.accept(localCacheBuilderConfig.getExpireAfterWriteSecs(), TimeUnit.SECONDS);
        } else if (localCacheBuilderConfig.getExpireAfterAccessSecs() != null) {
            expireAfterAccess.accept(localCacheBuilderConfig.getExpireAfterAccessSecs(), TimeUnit.SECONDS);
        }
    }
}
