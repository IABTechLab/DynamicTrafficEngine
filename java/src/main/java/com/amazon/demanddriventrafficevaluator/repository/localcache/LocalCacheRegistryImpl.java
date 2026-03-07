// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.localcache;

import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_IDENTIFIER_CONFIGURATION;

import com.amazon.demanddriventrafficevaluator.repository.localcache.builder.LocalCacheBuilderConfig;
import com.amazon.demanddriventrafficevaluator.repository.localcache.builder.LocalCacheFactory;
import com.amazon.demanddriventrafficevaluator.repository.localcache.exception.LocalCacheNotFoundException;
import com.amazon.demanddriventrafficevaluator.repository.localcache.exception.LocalCacheRegistrationException;
import com.google.common.cache.Cache;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.concurrent.ConcurrentMap;

/**
 * An implementation of the LocalCacheRegistry interface that manages local caches.
 * <p>
 * This class provides functionality to register, retrieve, and manage local caches.
 * It supports using a default configuration for caches and handles cache creation
 * and updates based on provided configurations.
 * </p>
 */
public class LocalCacheRegistryImpl implements LocalCacheRegistry {

    public static final String DEFAULT_GUAVA_LOCAL_CACHE_BUILDER_VERSION = "GUAVA_LOCAL_CACHE";
    /**
     * The local cache for model rules has a TTL (expireAfterWriteSecs) of 4200 seconds, or 70 minutes.
     * This was chosen to ensure that stale model rules are not applied to future hours.
     * This ensures that we minimize the rate of incorrect filtering decisions, lowering our FPR.
     * We apply a 10 minute buffer since refreshes are staggered over 5 minutes, so we want to
     * avoid invalidating the cache prematurely.
     */
    public static final LocalCacheBuilderConfig.LocalCacheBuilderConfigBuilder LOCAL_CACHE_BUILDER_CONFIG_BUILDER =
            LocalCacheBuilderConfig.builder()
                    .maximumSize(2500000)
                    .expireAfterWriteSecs(4200)
                    .concurrencyLevel(50);
    /**
     * The local cache for configurations has no expiration.
     * This was chosen to ensure that configuration entries do not expire, so there can be no gap between
     * the configuration entries expiring and the configurations being reloaded by the periodic task.
     */
    public static final LocalCacheBuilderConfig.LocalCacheBuilderConfigBuilder CONFIGURATION_LOCAL_CACHE_BUILDER_CONFIG_BUILDER =
            LocalCacheBuilderConfig.builder()
                    .maximumSize(2500000)
                    .concurrencyLevel(50);

    private final boolean useDefaultLocalCacheBuilderConfig;
    private final LocalCacheFactory localCacheFactory;
    private final ConcurrentMap<String, ImmutablePair<Integer, Cache>> cacheMap;

    public LocalCacheRegistryImpl(boolean useDefaultLocalCacheBuilderConfig,
                                  ConcurrentMap<String, ImmutablePair<Integer, Cache>> cacheMap,
                                  LocalCacheFactory localCacheFactory
    ) {
        this.useDefaultLocalCacheBuilderConfig = useDefaultLocalCacheBuilderConfig;
        this.cacheMap = cacheMap;
        this.localCacheFactory = localCacheFactory;
    }

    /**
     * Retrieves a cache by its name.
     * <p>
     * If the cache doesn't exist and default configuration is enabled, it creates
     * a new cache with default settings. Otherwise, it throws an exception.
     * </p>
     *
     * @param name The name of the cache to retrieve.
     * @return The Cache instance associated with the given name.
     * @throws LocalCacheNotFoundException if the cache is not found and cannot be created.
     */
    @Override
    public Cache getCache(String name) throws LocalCacheNotFoundException {
        if (cacheMap.containsKey(name)) {
            return cacheMap.get(name).getRight();
        }
        if (useDefaultLocalCacheBuilderConfig) {
            try {
                if (CACHE_IDENTIFIER_CONFIGURATION.equals(name)) {
                    registerCache(
                            CONFIGURATION_LOCAL_CACHE_BUILDER_CONFIG_BUILDER
                                    .localCacheBuilderVersion(DEFAULT_GUAVA_LOCAL_CACHE_BUILDER_VERSION)
                                    .cacheName(name)
                                    .build()
                    );
                } else {
                    registerCache(
                            LOCAL_CACHE_BUILDER_CONFIG_BUILDER
                                    .localCacheBuilderVersion(DEFAULT_GUAVA_LOCAL_CACHE_BUILDER_VERSION)
                                    .cacheName(name)
                                    .build()
                    );
                }
            } catch (LocalCacheRegistrationException e) {
                throw new LocalCacheNotFoundException(String.format("Cache with name %s not found", name), e);
            }
            return cacheMap.get(name).getRight();
        }
        throw new LocalCacheNotFoundException(String.format("Cache with name %s not found", name));
    }

    /**
     * Registers a new cache or updates an existing one with the provided configuration.
     * <p>
     * If a cache with the same name already exists, it compares the configurations.
     * If they differ, it creates a new cache with the new configuration and transfers
     * the existing data to it.
     * </p>
     *
     * @param localCacheBuilderConfig The configuration for the cache to register or update.
     * @throws LocalCacheRegistrationException if there's an error during cache registration or update.
     */
    @Override
    public void registerCache(LocalCacheBuilderConfig localCacheBuilderConfig) throws LocalCacheRegistrationException {
        String cacheName = localCacheBuilderConfig.getCacheName();
        ImmutablePair<Integer, Cache> existingLocalCache = cacheMap.get(cacheName);
        try {
            if (existingLocalCache == null) {
                cacheMap.put(cacheName, ImmutablePair.of(localCacheBuilderConfig.hashCode(),
                        localCacheFactory.getLocalCache(localCacheBuilderConfig)));
            } else if (!existingLocalCache.getLeft().equals(localCacheBuilderConfig.hashCode())) {
                ConcurrentMap existingCache = existingLocalCache.getRight().asMap();
                Cache newCache = localCacheFactory.getLocalCache(localCacheBuilderConfig);
                newCache.putAll(existingCache);
                cacheMap.put(cacheName, ImmutablePair.of(localCacheBuilderConfig.hashCode(), newCache));
                existingLocalCache.getRight().cleanUp();
            }
        } catch (Exception e) {
            throw new LocalCacheRegistrationException(
                    String.format("Error registering cache with name %s", cacheName), e);
        }
    }
}
