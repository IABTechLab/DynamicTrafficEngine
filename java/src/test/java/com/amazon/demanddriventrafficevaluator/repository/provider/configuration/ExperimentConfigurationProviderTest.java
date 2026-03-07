// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.provider.configuration;

import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_IDENTIFIER_CONFIGURATION;
import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_KEY_EXPERIMENT_CONFIGURATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperimentConfigurationProviderTest {

    @Mock
    private Dao<String, ExperimentConfiguration> mockCacheDao;

    @Mock
    private ExperimentConfiguration mockExperimentConfiguration;

    private ExperimentConfigurationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ExperimentConfigurationProvider(mockCacheDao);
    }

    @Test
    void testProvide_ConfigurationExists() {
        // Arrange
        when(mockCacheDao.get(CACHE_IDENTIFIER_CONFIGURATION, CACHE_KEY_EXPERIMENT_CONFIGURATION))
                .thenReturn(Optional.of(mockExperimentConfiguration));

        // Act
        ExperimentConfiguration result = provider.provide();

        // Assert
        assertSame(mockExperimentConfiguration, result);
        verify(mockCacheDao).get(CACHE_IDENTIFIER_CONFIGURATION,
                CACHE_KEY_EXPERIMENT_CONFIGURATION);
    }

    @Test
    void testProvide_ConfigurationDoesNotExist() {
        // Arrange
        when(mockCacheDao.get(CACHE_IDENTIFIER_CONFIGURATION,
                CACHE_KEY_EXPERIMENT_CONFIGURATION))
                .thenReturn(Optional.empty());

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> provider.provide());
        assertEquals("Cannot get Experiment Definitions from the cache", exception.getMessage());
        verify(mockCacheDao).get(CACHE_IDENTIFIER_CONFIGURATION,
                CACHE_KEY_EXPERIMENT_CONFIGURATION);
    }

    @Test
    void testProvide_CacheDaoThrowsException() {
        // Arrange
        when(mockCacheDao.get(CACHE_IDENTIFIER_CONFIGURATION,
                CACHE_KEY_EXPERIMENT_CONFIGURATION))
                .thenThrow(new RuntimeException("Cache error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> provider.provide());
        verify(mockCacheDao).get(CACHE_IDENTIFIER_CONFIGURATION,
                CACHE_KEY_EXPERIMENT_CONFIGURATION);
    }

    @Test
    void testCacheIdentifierConstant() {
        assertEquals("configuration", CACHE_IDENTIFIER_CONFIGURATION);
    }

    @Test
    void testCacheKeyConstant() {
        assertEquals("experiment-configuration", CACHE_KEY_EXPERIMENT_CONFIGURATION);
    }
}
