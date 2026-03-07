// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task.dataloading;

import com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelDefinition;
import com.amazon.demanddriventrafficevaluator.repository.loader.DefaultLoader;
import com.amazon.demanddriventrafficevaluator.repository.loader.model.ModelResultLoaderInput;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ConfigurationProvider;
import com.amazon.demanddriventrafficevaluator.util.PropertiesUtil;
import org.apache.commons.configuration2.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelResultPeriodicLoadingTaskTest {

    @Mock
    private ScheduledThreadPoolExecutor mockExecutor;

    @Mock
    private ConfigurationProvider<ModelConfiguration> mockModelConfigurationProvider;

    @Mock
    private DefaultLoader<ModelResultLoaderInput> mockModelResultLoader;

    @Mock
    private ModelConfiguration mockModelConfiguration;

    @Mock
    private Configuration mockFileSharingS3BucketProperties;

    private ModelResultPeriodicLoadingTask task;

    @BeforeEach
    void setUp() {
        task = new ModelResultPeriodicLoadingTask(
                "testSSP",
                "TestTask",
                5000,
                mockExecutor,
                mockModelConfigurationProvider,
                mockModelResultLoader,
                "test-bucket"
        );
        try (MockedStatic<PropertiesUtil> mockPropertiesUtil = mockStatic(PropertiesUtil.class)) {
            mockPropertiesUtil.when(PropertiesUtil::getFileSharingS3BucketProperties).thenReturn(mockFileSharingS3BucketProperties);
        }
    }

    @Test
    void testConstructor() {
        assertEquals("testSSP", task.getSspIdentifier());
    }

    @Test
    void testExecuteTask() {
        try (MockedStatic<PropertiesUtil> mockPropertiesUtil = mockStatic(PropertiesUtil.class)) {
            mockPropertiesUtil.when(PropertiesUtil::getFileSharingS3BucketProperties).thenReturn(mockFileSharingS3BucketProperties);
            // Arrange
            Map<String, ModelDefinition> modelDefinitions = new HashMap<>();
            ModelDefinition modelDef1 = new ModelDefinition();
            modelDef1.setIdentifier("model1");
            ModelDefinition modelDef2 = new ModelDefinition();
            modelDef2.setIdentifier("model2");
            modelDefinitions.put("model1", modelDef1);
            modelDefinitions.put("model2", modelDef2);

            when(mockModelConfigurationProvider.provide()).thenReturn(mockModelConfiguration);
            when(mockModelConfiguration.getModelDefinitionByIdentifier()).thenReturn(modelDefinitions);
            when(mockFileSharingS3BucketProperties.getString("adsp", "test-bucket"))
                    .thenReturn("testBucket");

            // Act
            task.executeTask();

            // Assert
            ArgumentCaptor<ModelResultLoaderInput> modelResultLoaderInputCaptor = ArgumentCaptor.forClass(ModelResultLoaderInput.class);
            verify(mockModelResultLoader, times(2)).load(modelResultLoaderInputCaptor.capture());

            List<ModelResultLoaderInput> inputs = modelResultLoaderInputCaptor.getAllValues();
            assertEquals(2, inputs.size());

            ModelResultLoaderInput input1 = inputs.stream().filter(i -> i.getModelIdentifier().equals("model1")).findFirst().get();
            assertEquals("testBucket", input1.getS3Bucket());
            assertEquals("model1.csv", input1.getS3ObjectKey());
            assertEquals("model1", input1.getModelIdentifier());
            assertEquals("testSSP", input1.getVendor());
            ModelResultLoaderInput input2 = inputs.stream().filter(i -> i.getModelIdentifier().equals("model2")).findFirst().get();
            assertEquals("testBucket", input2.getS3Bucket());
            assertEquals("model2.csv", input2.getS3ObjectKey());
            assertEquals("model2", input2.getModelIdentifier());
            assertEquals("testSSP", input2.getVendor());
        }
    }

    @Test
    void testExecuteTask_WithEmptyModelDefinitions() {
        try (MockedStatic<PropertiesUtil> mockPropertiesUtil = mockStatic(PropertiesUtil.class)) {
            mockPropertiesUtil.when(PropertiesUtil::getFileSharingS3BucketProperties).thenReturn(mockFileSharingS3BucketProperties);
            // Arrange
            when(mockModelConfigurationProvider.provide()).thenReturn(mockModelConfiguration);
            when(mockModelConfiguration.getModelDefinitionByIdentifier()).thenReturn(new HashMap<>());
            when(mockFileSharingS3BucketProperties.getString("adsp", "test-bucket"))
                    .thenReturn("testBucket");

            // Act
            task.executeTask();

            // Assert
            verify(mockModelResultLoader, never()).load(any());
        }
    }

    @Test
    void testExecuteTask_WithException() {
        // Arrange
        when(mockModelConfigurationProvider.provide()).thenThrow(new RuntimeException("Test exception"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> task.executeTask());
    }

    @Test
    void testInitialize() {
        // Act
        task.initialize();

        // Assert
        verify(mockExecutor).scheduleAtFixedRate(any(Runnable.class), anyLong(), eq(5000L), eq(TimeUnit.MILLISECONDS));
    }
}
