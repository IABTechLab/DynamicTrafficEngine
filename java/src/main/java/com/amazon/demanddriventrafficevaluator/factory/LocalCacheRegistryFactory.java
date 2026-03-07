// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import com.amazon.demanddriventrafficevaluator.repository.localcache.LocalCacheRegistry;
import com.amazon.demanddriventrafficevaluator.repository.localcache.LocalCacheRegistryImpl;
import com.amazon.demanddriventrafficevaluator.repository.localcache.builder.LocalCacheBuilder;
import com.amazon.demanddriventrafficevaluator.repository.localcache.builder.LocalCacheFactory;
import com.google.common.cache.Cache;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An abstract factory for creating and managing LocalCacheRegistry instances.
 * <p>
 * This class provides a mechanism to create and retrieve a singleton instance of
 * LocalCacheRegistry. It uses lazy initialization and double-checked locking to
 * ensure thread-safe creation of the singleton instance.
 * </p>
 */
public abstract class LocalCacheRegistryFactory {

    LocalCacheRegistry defaultLocalCacheRegistry;

    public LocalCacheRegistry getDefaultLocalCacheRegistrySingleton() {
        if (defaultLocalCacheRegistry != null) {
            return defaultLocalCacheRegistry;
        }
        synchronized (this) {
            boolean useDefaultLocalCacheBuilderConfig = true;
            ConcurrentMap<String, ImmutablePair<Integer, Cache>> cacheMap = new ConcurrentHashMap<>();
            Map<String, LocalCacheBuilder> localCacheBuilderMap = getLocalCacheBuilderMap();
            LocalCacheFactory localCacheFactory = new LocalCacheFactory(localCacheBuilderMap);
            defaultLocalCacheRegistry = new LocalCacheRegistryImpl(useDefaultLocalCacheBuilderConfig, cacheMap, localCacheFactory);
            return defaultLocalCacheRegistry;
        }
    }

    abstract Map<String, LocalCacheBuilder> getLocalCacheBuilderMap();
}
