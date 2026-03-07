// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InitializerTaskOnPeriodicTaskTest {

    @Mock
    private PeriodicTaskWithRandomizedStart mockTask;

    private InitializerTaskOnPeriodicTask initializerTask;

    @BeforeEach
    void setUp() {
        initializerTask = new InitializerTaskOnPeriodicTask(
                "TestPeriodicTask",
                5,
                200,
                2000,
                mockTask
        );
    }

    @Test
    void testConstructor() {
        assertEquals("TestPeriodicTask", initializerTask.getName());
        assertEquals(5, initializerTask.getMaximumAttempts());
        assertEquals(200, initializerTask.getMinDelayBeforeAttemptMs());
        assertEquals(2000, initializerTask.getMaxDelayBeforeAttemptMs());
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
