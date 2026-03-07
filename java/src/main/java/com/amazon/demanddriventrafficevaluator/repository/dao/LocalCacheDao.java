// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.dao;

import com.amazon.demanddriventrafficevaluator.repository.localcache.LocalCacheRegistry;
import com.google.common.cache.Cache;

import java.util.Optional;

/**
 * A Data Access Object (DAO) implementation for interacting with a local cache.
 * <p>
 * This class provides methods to retrieve and store objects in a local cache.
 * It implements the Dao interface, using generic types T for keys and R for values.
 * The class uses a LocalCacheRegistry to manage multiple caches identified by string identifiers.
 * </p>
 *
 * @param <T> The type of the keys used in the cache.
 * @param <R> The type of the values stored in the cache.
 */
public class LocalCacheDao<T, R> implements Dao<T, R> {

    public static final String CACHE_IDENTIFIER_CONFIGURATION = "configuration";
    public static final String CACHE_IDENTIFIER_FILE_IDENTIFIER = "model-results-identifier";

    public static final String CACHE_KEY_EXPERIMENT_CONFIGURATION = "experiment-configuration";
    public static final String CACHE_KEY_MODEL_CONFIGURATION = "model-configuration";

    private final LocalCacheRegistry localCacheRegistry;

    public LocalCacheDao(LocalCacheRegistry localCacheRegistry) {
        this.localCacheRegistry = localCacheRegistry;
    }

    /**
     * Retrieves a value from the specified cache using the provided key.
     * <p>
     * This method attempts to fetch the value associated with the given key from the cache
     * identified by the cacheIdentifier. If the value is found, it is returned wrapped in an Optional.
     * If not found or if an error occurs, an empty Optional is returned.
     * </p>
     *
     * @param cacheIdentifier The identifier of the cache to query.
     * @param key             The key to look up in the cache.
     * @return An Optional containing the value if found, or an empty Optional if not found or if an error occurs.
     * @throws IllegalStateException if an error occurs while accessing the cache.
     */
    @Override
    public Optional<R> get(String cacheIdentifier, T key) {
        try {
            Cache<T, R> cache = localCacheRegistry.getCache(cacheIdentifier);
            return Optional.ofNullable(cache.getIfPresent(key));
        } catch (Exception e) {
            throw new IllegalStateException("Error while getting data from the cache", e);
        }
    }

    /**
     * Stores a value in the specified cache using the provided key.
     * <p>
     * This method attempts to put the given value into the cache identified by the cacheIdentifier,
     * associating it with the provided key.
     * </p>
     *
     * @param cacheIdentifier The identifier of the cache to use.
     * @param key             The key to associate with the value.
     * @param value           The value to store in the cache.
     * @throws IllegalStateException if an error occurs while accessing or updating the cache.
     */
    @Override
    public void put(String cacheIdentifier, T key, R value) {
        try {
            Cache<T, R> cache = localCacheRegistry.getCache(cacheIdentifier);
            cache.put(key, value);
        } catch (Exception e) {
            throw new IllegalStateException("Error while putting data from the cache", e);
        }
    }

    /**
     * Clears the entire specified cache.
     * <p>
     * This method attempts to delete/expire all items in the cache identified by the
     * cacheIdentifier.
     * </p>
     *
     * @param cacheIdentifier The identifier of the cache to use.
     */
    @Override
    public void clear(String cacheIdentifier) {
        try {
            Cache<T, R> cache = localCacheRegistry.getCache(cacheIdentifier);
            cache.invalidateAll();
        } catch (Exception e) {
            throw new IllegalStateException("Error while clearing data from the cache", e);
        }
    }
}
