// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.localcache.builder;

import com.amazon.demanddriventrafficevaluator.repository.localcache.exception.LocalCacheCreationException;
import com.google.common.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalCacheFactoryTest {

    @Mock
    private LocalCacheBuilder mockCacheBuilder;

    @Mock
    private LocalCacheBuilderConfig mockConfig;

    @Mock
    private Cache mockCache;

    private LocalCacheFactory cacheFactory;
    private Map<String, LocalCacheBuilder> builderMap;

    @BeforeEach
    void setUp() {
        builderMap = new HashMap<>();
        builderMap.put("v1", mockCacheBuilder);
        cacheFactory = new LocalCacheFactory(builderMap);
    }

    @Test
    void testGetLocalCache_SuccessfulCreation() throws LocalCacheCreationException {
        // Arrange
        when(mockConfig.getLocalCacheBuilderVersion()).thenReturn("v1");
        when(mockCacheBuilder.build(mockConfig)).thenReturn(mockCache);

        // Act
        Cache result = cacheFactory.getLocalCache(mockConfig);

        // Assert
        assertSame(mockCache, result);
        verify(mockCacheBuilder).build(mockConfig);
    }

    @Test
    void testGetLocalCache_UnknownVersion() {
        // Arrange
        when(mockConfig.getLocalCacheBuilderVersion()).thenReturn("v2");
        when(mockConfig.getCacheName()).thenReturn("testCache");

        // Act & Assert
        LocalCacheCreationException exception = assertThrows(LocalCacheCreationException.class,
                () -> cacheFactory.getLocalCache(mockConfig));
        assertEquals("Unknown local cache builder version: v2 for cache: testCache", exception.getMessage());
    }

    @Test
    void testGetLocalCache_NullConfig() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> cacheFactory.getLocalCache(null));
    }

    @Test
    void testGetLocalCache_BuilderThrowsException() {
        // Arrange
        when(mockConfig.getLocalCacheBuilderVersion()).thenReturn("v1");
        when(mockCacheBuilder.build(mockConfig)).thenThrow(new RuntimeException("Build failed"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> cacheFactory.getLocalCache(mockConfig));
    }

    @Test
    void testConstructor_WithEmptyMap() {
        // Arrange
        Map<String, LocalCacheBuilder> emptyMap = new HashMap<>();

        // Act
        LocalCacheFactory factory = new LocalCacheFactory(emptyMap);

        // Assert
        assertNotNull(factory);
    }

    @Test
    void testGetLocalCache_MultipleBuilders() throws LocalCacheCreationException {
        // Arrange
        LocalCacheBuilder mockBuilder2 = mock(LocalCacheBuilder.class);
        builderMap.put("v2", mockBuilder2);
        when(mockConfig.getLocalCacheBuilderVersion()).thenReturn("v2");
        when(mockBuilder2.build(mockConfig)).thenReturn(mockCache);

        // Act
        Cache result = cacheFactory.getLocalCache(mockConfig);

        // Assert
        assertSame(mockCache, result);
        verify(mockBuilder2).build(mockConfig);
        verify(mockCacheBuilder, never()).build(any());
    }
}
