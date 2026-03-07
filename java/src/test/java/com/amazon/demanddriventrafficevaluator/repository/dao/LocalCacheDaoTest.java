// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.dao;

import com.amazon.demanddriventrafficevaluator.repository.localcache.LocalCacheRegistry;
import com.amazon.demanddriventrafficevaluator.repository.localcache.exception.LocalCacheNotFoundException;
import com.google.common.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalCacheDaoTest {

    @Mock
    private LocalCacheRegistry mockLocalCacheRegistry;

    @Mock
    private Cache<String, Integer> mockCache;

    private LocalCacheDao<String, Integer> localCacheDao;

    @BeforeEach
    void setUp() {
        localCacheDao = new LocalCacheDao<>(mockLocalCacheRegistry);
    }

    @Test
    void testGet_WhenValuePresent() throws LocalCacheNotFoundException {
        // Arrange
        String cacheIdentifier = "testCache";
        String key = "testKey";
        Integer value = 42;
        when(mockLocalCacheRegistry.getCache(cacheIdentifier)).thenReturn(mockCache);
        when(mockCache.getIfPresent(key)).thenReturn(value);

        // Act
        Optional<Integer> result = localCacheDao.get(cacheIdentifier, key);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(value, result.get());
        verify(mockLocalCacheRegistry).getCache(cacheIdentifier);
        verify(mockCache).getIfPresent(key);
    }

    @Test
    void testGet_WhenValueNotPresent() throws LocalCacheNotFoundException {
        // Arrange
        String cacheIdentifier = "testCache";
        String key = "testKey";
        when(mockLocalCacheRegistry.getCache(cacheIdentifier)).thenReturn(mockCache);
        when(mockCache.getIfPresent(key)).thenReturn(null);

        // Act
        Optional<Integer> result = localCacheDao.get(cacheIdentifier, key);

        // Assert
        assertFalse(result.isPresent());
        verify(mockLocalCacheRegistry).getCache(cacheIdentifier);
        verify(mockCache).getIfPresent(key);
    }

    @Test
    void testGet_WhenExceptionThrown() throws LocalCacheNotFoundException {
        // Arrange
        String cacheIdentifier = "testCache";
        String key = "testKey";
        when(mockLocalCacheRegistry.getCache(cacheIdentifier)).thenThrow(new RuntimeException("Test exception"));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> localCacheDao.get(cacheIdentifier, key));
        assertEquals("Error while getting data from the cache", exception.getMessage());
        assertNotNull(exception.getCause());
        assertEquals("Test exception", exception.getCause().getMessage());
    }

    @Test
    void testPut_Successful() throws LocalCacheNotFoundException {
        // Arrange
        String cacheIdentifier = "testCache";
        String key = "testKey";
        Integer value = 42;
        when(mockLocalCacheRegistry.getCache(cacheIdentifier)).thenReturn(mockCache);

        // Act
        localCacheDao.put(cacheIdentifier, key, value);

        // Assert
        verify(mockLocalCacheRegistry).getCache(cacheIdentifier);
        verify(mockCache).put(key, value);
    }

    @Test
    void testPut_WhenExceptionThrown() throws LocalCacheNotFoundException {
        // Arrange
        String cacheIdentifier = "testCache";
        String key = "testKey";
        Integer value = 42;
        when(mockLocalCacheRegistry.getCache(cacheIdentifier)).thenThrow(new RuntimeException("Test exception"));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> localCacheDao.put(cacheIdentifier, key, value));
        assertEquals("Error while putting data from the cache", exception.getMessage());
        assertNotNull(exception.getCause());
        assertEquals("Test exception", exception.getCause().getMessage());
    }

    @Test
    void testCacheIdentifierConstants() {
        assertEquals("configuration", LocalCacheDao.CACHE_IDENTIFIER_CONFIGURATION);
        assertEquals("model-results-identifier", LocalCacheDao.CACHE_IDENTIFIER_FILE_IDENTIFIER);
    }

    @Test
    void testCacheKeyConstants() {
        assertEquals("experiment-configuration", LocalCacheDao.CACHE_KEY_EXPERIMENT_CONFIGURATION);
        assertEquals("model-configuration", LocalCacheDao.CACHE_KEY_MODEL_CONFIGURATION);
    }
}
