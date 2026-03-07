// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.loader.configuration;

import com.amazon.demanddriventrafficevaluator.evaluation.experiment.TreatmentAllocator;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ConfigurationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperimentConfigurationLoaderTest {

    @Mock
    private DefaultConfigurationLoader<ExperimentConfiguration> mockConfigurationLoader;
    @Mock
    private ConfigurationProvider<ExperimentConfiguration> mockConfigurationProvider;
    @Mock
    private TreatmentAllocator mockTreatmentAllocator;
    @Mock
    private ExperimentConfiguration mockExperimentConfiguration;

    private ExperimentConfigurationLoader loader;

    @BeforeEach
    void setUp() {
        loader = new ExperimentConfigurationLoader(mockConfigurationLoader, mockConfigurationProvider, mockTreatmentAllocator);
    }

    @Test
    void testLoad_Successful() {
        // Arrange
        ConfigurationLoaderInput input = new ConfigurationLoaderInput("testBucket", "testKey", "testVendor", "testType");
        when(mockConfigurationLoader.load(input)).thenReturn(true);
        when(mockConfigurationProvider.provide()).thenReturn(mockExperimentConfiguration);

        // Act
        boolean result = loader.load(input);

        // Assert
        assertTrue(result);
        verify(mockConfigurationLoader).load(input);
        verify(mockConfigurationProvider).provide();
        verify(mockTreatmentAllocator).updateConfiguration(mockExperimentConfiguration);
    }

    @Test
    void testLoad_ConfigurationNotLoaded() {
        // Arrange
        ConfigurationLoaderInput input = new ConfigurationLoaderInput("testBucket", "testKey", "testVendor", "testType");
        when(mockConfigurationLoader.load(input)).thenReturn(false);

        // Act
        boolean result = loader.load(input);

        // Assert
        assertFalse(result);
        verify(mockConfigurationLoader).load(input);
        verify(mockConfigurationProvider, never()).provide();
        verify(mockTreatmentAllocator, never()).updateConfiguration(any());
    }

    @Test
    void testLoad_ExceptionThrown() {
        // Arrange
        ConfigurationLoaderInput input = new ConfigurationLoaderInput("testBucket", "testKey", "testVendor", "testType");
        when(mockConfigurationLoader.load(input)).thenThrow(new RuntimeException("Test exception"));

        // Act & Assert
        Exception exception = assertThrows(IllegalStateException.class, () -> loader.load(input));
        assertEquals("Fail to load Experiment Configuration due to ", exception.getMessage());
        assertNotNull(exception.getCause());
        assertEquals("Test exception", exception.getCause().getMessage());
    }

    @Test
    void testGetS3ObjectKey() {
        // Arrange
        ConfigurationLoaderInput input = new ConfigurationLoaderInput("testBucket", "testKey", "testVendor", "testType");
        String expectedKey = "testKey";
        when(mockConfigurationLoader.getS3ObjectKey(input)).thenReturn(expectedKey);

        // Act
        String result = loader.getS3ObjectKey(input);

        // Assert
        assertEquals(expectedKey, result);
        verify(mockConfigurationLoader).getS3ObjectKey(input);
    }
}
