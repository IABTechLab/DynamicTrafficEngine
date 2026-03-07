// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskInitializerTest {

    @Mock
    private InitializerTask mockTask1;
    @Mock
    private InitializerTask mockTask2;
    @Mock
    private InitializerTask mockTask3;

    private TaskInitializer taskInitializer;

    @Test
    void testInit_WithStageOneTasksOnly() {
        // Arrange
        taskInitializer = new TaskInitializer(Arrays.asList(mockTask1, mockTask2), null, 10000);
        when(mockTask1.getName()).thenReturn("Task1");
        when(mockTask1.getMaximumAttempts()).thenReturn(2);
        when(mockTask2.getName()).thenReturn("Task2");
        when(mockTask2.getMaximumAttempts()).thenReturn(2);

        // Act
        taskInitializer.init();

        // Assert
        verify(mockTask1).run();
        verify(mockTask2).run();
    }

    @Test
    void testInit_WithStageTwoTasks() {
        // Arrange
        taskInitializer = new TaskInitializer(Collections.singletonList(mockTask1),
                Collections.singletonList(mockTask2), 10000);
        when(mockTask1.getName()).thenReturn("Task1");
        when(mockTask1.getMaximumAttempts()).thenReturn(2);
        when(mockTask2.getName()).thenReturn("Task2");
        when(mockTask2.getMaximumAttempts()).thenReturn(2);

        // Act
        taskInitializer.init();

        // Assert
        verify(mockTask1).run();
        verify(mockTask2).run();
    }

    @Test
    void testInit_WithNoTasks() {
        // Arrange
        taskInitializer = new TaskInitializer(null, null, 10000);

        // Act
        taskInitializer.init();

        // Assert
        // Verify that no exceptions are thrown
    }

    @Test
    void testInit_TaskExecutionFailure() {
        // Arrange
        taskInitializer = new TaskInitializer(Collections.singletonList(mockTask1), null, 10000);
        when(mockTask1.getName()).thenReturn("Task1");
        when(mockTask1.getMaximumAttempts()).thenReturn(1);
        doThrow(new RuntimeException("Task failed")).when(mockTask1).run();

        // Act & Assert
        assertThrows(RuntimeException.class, () -> taskInitializer.init());
    }

    @Test
    void testInit_Timeout() {
        // Arrange
        taskInitializer = new TaskInitializer(Collections.singletonList(mockTask1), null, 100);
        when(mockTask1.getName()).thenReturn("Task1");
        when(mockTask1.getMaximumAttempts()).thenReturn(1);
        doAnswer(invocation -> {
            Thread.sleep(200);
            return null;
        }).when(mockTask1).run();

        // Act & Assert
        assertThrows(RuntimeException.class, () -> taskInitializer.init());
    }

    @Test
    void testSubmitTask_SuccessfulExecution() {
        // Arrange
        taskInitializer = new TaskInitializer(null, null, 10000);
        when(mockTask1.getName()).thenReturn("Task1");
        when(mockTask1.getMaximumAttempts()).thenReturn(1);

        // Act
        taskInitializer.submitTask(System.currentTimeMillis(), mockTask1);

        // Assert
        verify(mockTask1).run();
    }

    @Test
    void testSubmitTask_RetryAndSuccess() {
        // Arrange
        taskInitializer = new TaskInitializer(null, null, 10000);
        when(mockTask1.getName()).thenReturn("Task1");
        when(mockTask1.getMaximumAttempts()).thenReturn(3);
        when(mockTask1.getMinDelayBeforeAttemptMs()).thenReturn(10L);
        when(mockTask1.getMaxDelayBeforeAttemptMs()).thenReturn(100L);
        doThrow(new RuntimeException("Fail")).doThrow(new RuntimeException("Fail")).doNothing().when(mockTask1).run();

        // Act
        taskInitializer.submitTask(System.currentTimeMillis(), mockTask1);

        // Assert
        verify(mockTask1, times(3)).run();
    }

    @Test
    void testSubmitTask_MaxRetriesExceeded() {
        // Arrange
        taskInitializer = new TaskInitializer(null, null, 10000);
        when(mockTask1.getName()).thenReturn("Task1");
        when(mockTask1.getMaximumAttempts()).thenReturn(3);
        when(mockTask1.getMinDelayBeforeAttemptMs()).thenReturn(10L);
        when(mockTask1.getMaxDelayBeforeAttemptMs()).thenReturn(100L);
        doThrow(new RuntimeException("Fail")).when(mockTask1).run();

        // Act & Assert
        assertThrows(RuntimeException.class, () -> taskInitializer.submitTask(System.currentTimeMillis(), mockTask1));
        verify(mockTask1, times(3)).run();
    }

    @Test
    void testCalculateDelay() {
        assertEquals(0, TaskInitializer.calculateDelay(1, 100, 10, 1000));
        assertEquals(10, TaskInitializer.calculateDelay(2, 100, 10, 1000));
        assertEquals(200, TaskInitializer.calculateDelay(3, 100, 10, 1000));
        assertEquals(400, TaskInitializer.calculateDelay(4, 200, 10, 1000));
        assertEquals(1000, TaskInitializer.calculateDelay(5, 800, 10, 1000));
    }
}
