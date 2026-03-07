// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.localcache;

import com.amazon.demanddriventrafficevaluator.repository.localcache.builder.LocalCacheBuilderConfig;
import com.amazon.demanddriventrafficevaluator.repository.localcache.exception.LocalCacheNotFoundException;
import com.amazon.demanddriventrafficevaluator.repository.localcache.exception.LocalCacheRegistrationException;
import com.google.common.cache.Cache;

/**
 * An interface defining operations for managing local caches.
 * <p>
 * This interface provides methods for retrieving and registering caches.
 * Implementations of this interface are responsible for maintaining a registry
 * of caches and handling their lifecycle.
 * </p>
 */
public interface LocalCacheRegistry {

    /**
     * Retrieves a cache by its name.
     * <p>
     * This method should return an existing cache if one is found with the given name.
     * The behavior for non-existent caches may vary by implementation (e.g., creating
     * a new cache or throwing an exception).
     * </p>
     *
     * @param name The name of the cache to retrieve.
     * @return The Cache instance associated with the given name.
     * @throws LocalCacheNotFoundException if the cache is not found and cannot be created or retrieved.
     */
    Cache getCache(String name) throws LocalCacheNotFoundException;

    /**
     * Registers a new cache or updates an existing one with the provided configuration.
     * <p>
     * This method should create a new cache if one doesn't exist with the given name,
     * or update an existing cache if the name already exists in the registry. The exact
     * behavior for updating existing caches may vary by implementation.
     * </p>
     *
     * @param localCacheBuilderConfig The configuration for the cache to register or update.
     * @throws LocalCacheRegistrationException if there's an error during cache registration or update.
     *                                         This could include configuration errors, resource allocation issues, etc.
     */
    void registerCache(LocalCacheBuilderConfig localCacheBuilderConfig) throws LocalCacheRegistrationException;
}
