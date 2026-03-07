// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.localcache;

import com.amazon.demanddriventrafficevaluator.repository.localcache.builder.LocalCacheBuilderConfig;
import com.amazon.demanddriventrafficevaluator.repository.localcache.builder.LocalCacheFactory;
import com.amazon.demanddriventrafficevaluator.repository.localcache.exception.LocalCacheCreationException;
import com.amazon.demanddriventrafficevaluator.repository.localcache.exception.LocalCacheNotFoundException;
import com.amazon.demanddriventrafficevaluator.repository.localcache.exception.LocalCacheRegistrationException;
import com.google.common.cache.Cache;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalCacheRegistryImplTest {

    @Mock
    private LocalCacheFactory mockLocalCacheFactory;

    @Mock
    private Cache mockCache;

    private ConcurrentMap<String, ImmutablePair<Integer, Cache>> cacheMap;
    private LocalCacheRegistryImpl registry;
    private String cacheName;
    private LocalCacheBuilderConfig mockConfig;
    private LocalCacheBuilderConfig newMockConfig;

    @BeforeEach
    void setUp() {
        cacheMap = new ConcurrentHashMap<>();
        registry = new LocalCacheRegistryImpl(true, cacheMap, mockLocalCacheFactory);
        cacheName = "existingCache";
        mockConfig = LocalCacheBuilderConfig.builder().cacheName(cacheName).build();
        newMockConfig = LocalCacheBuilderConfig.builder().cacheName(cacheName).build();
    }

    @Test
    void testGetCache_ExistingCache() throws LocalCacheNotFoundException {
        // Arrange
        String cacheName = "existingCache";
        cacheMap.put(cacheName, ImmutablePair.of(1, mockCache));

        // Act
        Cache result = registry.getCache(cacheName);

        // Assert
        assertSame(mockCache, result);
    }

    @Test
    void testGetCache_NonExistingCache_WithDefaultConfig() throws Exception {
        // Arrange
        String cacheName = "newCache";
        when(mockLocalCacheFactory.getLocalCache(any())).thenReturn(mockCache);

        // Act
        Cache result = registry.getCache(cacheName);

        // Assert
        assertSame(mockCache, result);
        verify(mockLocalCacheFactory).getLocalCache(argThat(config ->
                config.getCacheName().equals(cacheName)
                        && config.getExpireAfterWriteSecs().equals(4200)
                        && config.getLocalCacheBuilderVersion().equals(LocalCacheRegistryImpl.DEFAULT_GUAVA_LOCAL_CACHE_BUILDER_VERSION)
        ));
    }

    @Test
    void testGetCache_NonExistingCache_WithDefaultModelConfigConfig() throws Exception {
        // Arrange
        String cacheName = "configuration";
        when(mockLocalCacheFactory.getLocalCache(any())).thenReturn(mockCache);

        // Act
        Cache result = registry.getCache(cacheName);

        // Assert
        assertSame(mockCache, result);
        verify(mockLocalCacheFactory).getLocalCache(argThat(config ->
                config.getCacheName().equals(cacheName)
                        && config.getExpireAfterWriteSecs() == null
                        && config.getLocalCacheBuilderVersion().equals(LocalCacheRegistryImpl.DEFAULT_GUAVA_LOCAL_CACHE_BUILDER_VERSION)
        ));
    }

    @Test
    void testGetCache_NonExistingCache_WithoutDefaultConfig() {
        // Arrange
        registry = new LocalCacheRegistryImpl(false, cacheMap, mockLocalCacheFactory);

        // Act & Assert
        assertThrows(LocalCacheNotFoundException.class, () -> registry.getCache("nonExistingCache"));
    }

    @Test
    void testRegisterCache_NewCache() throws Exception {
        // Arrange
        when(mockLocalCacheFactory.getLocalCache(mockConfig)).thenReturn(mockCache);

        // Act
        registry.registerCache(mockConfig);

        // Assert
        assertTrue(cacheMap.containsKey(cacheName));
        assertEquals(ImmutablePair.of(mockConfig.hashCode(), mockCache), cacheMap.get(cacheName));
    }

    @Test
    void testRegisterCache_ExistingCache_SameConfig() throws Exception {
        // Arrange
        String cacheName = "existingCache";
        cacheMap.put(cacheName, ImmutablePair.of(mockCache.hashCode(), mockCache));

        // Act
        assertThrows(LocalCacheRegistrationException.class, () -> registry.registerCache(mockConfig));
    }

    @Test
    void testRegisterCache_ExistingCache_DifferentConfig() throws Exception {
        // Arrange
        String cacheName = "existingCache";
        Cache newMockCache = mock(Cache.class);
        ConcurrentMap<Object, Object> existingCacheMap = mock(ConcurrentMap.class);
        when(mockLocalCacheFactory.getLocalCache(newMockConfig)).thenReturn(newMockCache);
        when(mockCache.asMap()).thenReturn(existingCacheMap);
        cacheMap.put(cacheName, ImmutablePair.of(mockConfig.hashCode(), mockCache));

        // Act
        registry.registerCache(newMockConfig);

        // Assert
        assertEquals(ImmutablePair.of(newMockConfig.hashCode(), newMockCache), cacheMap.get(cacheName));
        verify(newMockCache).putAll(existingCacheMap);
        verify(mockCache).cleanUp();
    }

    @Test
    void testRegisterCache_FactoryThrowsException() throws LocalCacheCreationException {
        // Arrange
        when(mockLocalCacheFactory.getLocalCache(mockConfig)).thenThrow(new RuntimeException("Factory error"));

        // Act & Assert
        assertThrows(LocalCacheRegistrationException.class, () -> registry.registerCache(mockConfig));
    }
}
