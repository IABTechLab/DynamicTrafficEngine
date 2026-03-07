// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class InitializerTaskOnOneShotTaskTest {

    @Mock
    private OneShotTaskWithRandomizedStart mockTask;

    private InitializerTaskOnOneShotTask initializerTask;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        initializerTask = new InitializerTaskOnOneShotTask(
                "TestTask",
                3,
                100,
                1000,
                mockTask
        );
    }

    @Test
    void testConstructor() {
        assertEquals("TestTask", initializerTask.getName());
        assertEquals(3, initializerTask.getMaximumAttempts());
        assertEquals(100, initializerTask.getMinDelayBeforeAttemptMs());
        assertEquals(1000, initializerTask.getMaxDelayBeforeAttemptMs());
    }

    @Test
    void testRun() {
        // Act
        initializerTask.run();

        // Assert
        verify(mockTask, times(1)).initialize();
    }

    @Test
    void testRun_WithException() {
        // Arrange
        doThrow(new RuntimeException("Initialization failed")).when(mockTask).initialize();

        // Act & Assert
        assertThrows(RuntimeException.class, () -> initializerTask.run());
        verify(mockTask, times(1)).initialize();
    }
}
