// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task.dataloading;

import com.amazon.demanddriventrafficevaluator.repository.loader.DefaultLoader;
import com.amazon.demanddriventrafficevaluator.repository.loader.configuration.ConfigurationLoaderInput;
import com.amazon.demanddriventrafficevaluator.util.PropertiesUtil;
import org.apache.commons.configuration2.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationPeriodicLoadingTaskTest {

    @Mock
    private ScheduledThreadPoolExecutor mockExecutor;

    @Mock
    private DefaultLoader<ConfigurationLoaderInput> mockConfigurationLoader;

    @Mock
    private Configuration mockFileSharingS3BucketProperties;

    private ConfigurationPeriodicLoadingTask task;

    @BeforeEach
    void setUp() {
        task = new ConfigurationPeriodicLoadingTask(
                "testSSP",
                "TestTask",
                5000,
                mockExecutor,
                mockConfigurationLoader,
                "testConfigType",
                "test-bucket"
        );
    }

    @Test
    void testConstructor() {
        assertEquals("testSSP", task.getSspIdentifier());
    }

    @Test
    void testExecuteTask() {
        // Arrange
        try (MockedStatic<PropertiesUtil> mockPropertiesUtil = mockStatic(PropertiesUtil.class)) {
            mockPropertiesUtil.when(PropertiesUtil::getFileSharingS3BucketProperties).thenReturn(mockFileSharingS3BucketProperties);
            when(mockFileSharingS3BucketProperties.getString("adsp", "test-bucket"))
                    .thenReturn("test-bucket");
            ArgumentCaptor<ConfigurationLoaderInput> configurationLoaderInputCaptor = ArgumentCaptor.forClass(ConfigurationLoaderInput.class);
            when(mockConfigurationLoader.load(configurationLoaderInputCaptor.capture())).thenReturn(true);
            // Act
            task.executeTask();

            // Assert
            verify(mockConfigurationLoader).load(any(ConfigurationLoaderInput.class));
            ConfigurationLoaderInput capturedInput = configurationLoaderInputCaptor.getValue();
            assertEquals("test-bucket", capturedInput.getS3Bucket());
            assertEquals("testConfigType", capturedInput.getConfigurationType());
            assertEquals("testSSP", capturedInput.getVendor());
        }
    }

    @Test
    void testInitialize() {
        // Act
        task.initialize();
        // Assert
        verify(mockConfigurationLoader).load(any(ConfigurationLoaderInput.class));
        verify(mockExecutor).scheduleAtFixedRate(any(Runnable.class), anyLong(), eq(5000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void testSchedulePeriodically() {
        // Act
        task.schedulePeriodically();

        // Assert
        verify(mockExecutor).scheduleAtFixedRate(any(Runnable.class), anyLong(), eq(5000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void testSchedulePeriodically_WithTooShortPeriod() {
        // Arrange
        ConfigurationPeriodicLoadingTask shortPeriodTask = new ConfigurationPeriodicLoadingTask(
                "testSSP", "TestTask", 500, mockExecutor, mockConfigurationLoader, "testConfigType", "test-bucket"
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, shortPeriodTask::schedulePeriodically);
    }

    @Test
    void testSchedulePeriodically_WithTooLongPeriod() {
        // Arrange
        ConfigurationPeriodicLoadingTask longPeriodTask = new ConfigurationPeriodicLoadingTask(
                "testSSP", "TestTask", Integer.MAX_VALUE + 1L, mockExecutor, mockConfigurationLoader, "testConfigType", "test-bucket"
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, longPeriodTask::schedulePeriodically);
    }

    @Test
    void testExecuteTask_WithException() {
        // Arrange
        doThrow(new RuntimeException("Test exception")).when(mockConfigurationLoader).load(any());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> task.executeTask());
    }
}
