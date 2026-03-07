// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task.registrysetup;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeatureOperator;
import com.amazon.demanddriventrafficevaluator.modelfeature.Registry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.ServiceLoader;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelFeatureOperatorRegistrySetupTaskTest {

    @Mock
    private ScheduledThreadPoolExecutor mockExecutor;

    @Mock
    private Registry<TestOperator> mockRegistry;

    @Mock
    private ServiceLoader<TestOperator> mockServiceLoader;

    private ModelFeatureOperatorRegistrySetupTask<TestOperator> task;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        task = new ModelFeatureOperatorRegistrySetupTask<>(
                "TestTask",
                mockExecutor,
                mockRegistry,
                TestOperator.class
        );
    }

    @Test
    void testExecuteTask() {
        // Arrange
        TestOperator operator1 = new TestOperator1();
        TestOperator operator2 = new TestOperator2();

        try (MockedStatic<ServiceLoader> mockedServiceLoader = mockStatic(ServiceLoader.class)) {
            mockedServiceLoader.when(() -> ServiceLoader.load(TestOperator.class))
                    .thenReturn(mockServiceLoader);
            when(mockServiceLoader.iterator())
                    .thenReturn(Arrays.asList(operator1, operator2).iterator());

            // Act
            task.executeTask();

            // Assert
            verify(mockRegistry).register("TestOperator1", TestOperator1.class);
            verify(mockRegistry).register("TestOperator2", TestOperator2.class);
        }
    }

    @Test
    void testExecuteTask_NoImplementations() {
        // Arrange
        try (MockedStatic<ServiceLoader> mockedServiceLoader = mockStatic(ServiceLoader.class)) {
            mockedServiceLoader.when(() -> ServiceLoader.load(TestOperator.class))
                    .thenReturn(mockServiceLoader);
            when(mockServiceLoader.iterator())
                    .thenReturn(Collections.emptyIterator());

            // Act
            task.executeTask();

            // Assert
            verify(mockRegistry, never()).register(anyString(), any(TestOperator.class));
        }
    }

    @Test
    void testInitialize() {
        // Act
        task.initialize();

        // Assert
        verify(mockExecutor).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    // Test interfaces and classes
    interface TestOperator extends ModelFeatureOperator {
    }

    static class TestOperator1 implements TestOperator {
    }

    static class TestOperator2 implements TestOperator {
    }
}
