// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.provider.configuration;

import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_IDENTIFIER_CONFIGURATION;
import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_KEY_MODEL_CONFIGURATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelConfigurationProviderTest {

    @Mock
    private Dao<String, ModelConfiguration> mockCacheDao;

    @Mock
    private ModelConfiguration mockModelConfiguration;

    private ModelConfigurationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ModelConfigurationProvider(mockCacheDao);
    }

    @Test
    void testProvide_ConfigurationExists() {
        // Arrange
        when(mockCacheDao.get(CACHE_IDENTIFIER_CONFIGURATION,
                CACHE_KEY_MODEL_CONFIGURATION))
                .thenReturn(Optional.of(mockModelConfiguration));

        // Act
        ModelConfiguration result = provider.provide();

        // Assert
        assertSame(mockModelConfiguration, result);
        verify(mockCacheDao).get(CACHE_IDENTIFIER_CONFIGURATION,
                CACHE_KEY_MODEL_CONFIGURATION);
    }

    @Test
    void testProvide_ConfigurationDoesNotExist() {
        // Arrange
        when(mockCacheDao.get(CACHE_IDENTIFIER_CONFIGURATION,
                CACHE_KEY_MODEL_CONFIGURATION))
                .thenReturn(Optional.empty());

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> provider.provide());
        assertEquals("Cannot get Model Configuration from the cache", exception.getMessage());
        verify(mockCacheDao).get(CACHE_IDENTIFIER_CONFIGURATION,
                CACHE_KEY_MODEL_CONFIGURATION);
    }

    @Test
    void testProvide_CacheDaoThrowsException() {
        // Arrange
        when(mockCacheDao.get(CACHE_IDENTIFIER_CONFIGURATION,
                CACHE_KEY_MODEL_CONFIGURATION))
                .thenThrow(new RuntimeException("Cache error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> provider.provide());
        verify(mockCacheDao).get(CACHE_IDENTIFIER_CONFIGURATION,
                CACHE_KEY_MODEL_CONFIGURATION);
    }

    @Test
    void testCacheIdentifierConstant() {
        assertEquals("configuration", CACHE_IDENTIFIER_CONFIGURATION);
    }

    @Test
    void testCacheKeyConstant() {
        assertEquals("model-configuration", CACHE_KEY_MODEL_CONFIGURATION);
    }
}
