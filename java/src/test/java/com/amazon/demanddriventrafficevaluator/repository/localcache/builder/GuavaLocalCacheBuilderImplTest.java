// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.localcache.builder;

import com.google.common.cache.Cache;
import com.google.common.cache.RemovalListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuavaLocalCacheBuilderImplTest {

    @Mock
    private RemovalListener<Object, Object> mockRemovalListener;

    @Mock
    private LocalCacheBuilderConfig mockConfig;

    private GuavaLocalCacheBuilderImpl cacheBuilder;

    @BeforeEach
    void setUp() {
        cacheBuilder = new GuavaLocalCacheBuilderImpl(mockRemovalListener);
    }

    @Test
    void testBuild_WithExpireAfterWrite() {
        // Arrange
        when(mockConfig.getMaximumSize()).thenReturn(100);
        when(mockConfig.getConcurrencyLevel()).thenReturn(4);
        when(mockConfig.getExpireAfterWriteSecs()).thenReturn(10);

        // Act
        Cache cache = cacheBuilder.build(mockConfig);

        // Assert
        assertNotNull(cache);
        verify(mockConfig).getMaximumSize();
        verify(mockConfig).getConcurrencyLevel();
        verify(mockConfig, times(2)).getExpireAfterWriteSecs();
        verify(mockConfig, never()).getExpireAfterAccessSecs();
    }

    @Test
    void testBuild_WithExpireAfterAccess() {
        // Arrange
        when(mockConfig.getMaximumSize()).thenReturn(200);
        when(mockConfig.getConcurrencyLevel()).thenReturn(8);
        when(mockConfig.getExpireAfterAccessSecs()).thenReturn(5);
        when(mockConfig.getExpireAfterWriteSecs()).thenReturn(null);

        // Act
        Cache<Object, Object> cache = cacheBuilder.build(mockConfig);

        // Assert
        assertNotNull(cache);
        verify(mockConfig).getMaximumSize();
        verify(mockConfig).getConcurrencyLevel();
        verify(mockConfig, times(2)).getExpireAfterAccessSecs();
        verify(mockConfig, times(1)).getExpireAfterWriteSecs();
    }

    @Test
    void testBuild_WithBothExpirations() {
        // Arrange
        when(mockConfig.getMaximumSize()).thenReturn(150);
        when(mockConfig.getConcurrencyLevel()).thenReturn(6);
        when(mockConfig.getExpireAfterWriteSecs()).thenReturn(15);

        // Act
        Cache<Object, Object> cache = cacheBuilder.build(mockConfig);

        // Assert
        assertNotNull(cache);
        verify(mockConfig).getMaximumSize();
        verify(mockConfig).getConcurrencyLevel();
        verify(mockConfig, times(2)).getExpireAfterWriteSecs();
        verify(mockConfig, times(0)).getExpireAfterAccessSecs();
    }

    @Test
    void testBuild_WithNoExpirations() {
        // Arrange
        when(mockConfig.getMaximumSize()).thenReturn(50);
        when(mockConfig.getConcurrencyLevel()).thenReturn(2);
        when(mockConfig.getExpireAfterWriteSecs()).thenReturn(null);
        when(mockConfig.getExpireAfterAccessSecs()).thenReturn(null);

        // Act
        Cache<Object, Object> cache = cacheBuilder.build(mockConfig);

        // Assert
        assertNotNull(cache);
        verify(mockConfig).getMaximumSize();
        verify(mockConfig).getConcurrencyLevel();
        verify(mockConfig).getExpireAfterWriteSecs();
        verify(mockConfig).getExpireAfterAccessSecs();
    }

    @Test
    void testBuild_WithNullConfig() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> cacheBuilder.build(null));
    }
}
