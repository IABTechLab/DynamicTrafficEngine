// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OneShotTaskWithRandomizedStartTest {

    @Mock
    private ScheduledThreadPoolExecutor mockExecutor;

    private TestOneShotTask task;

    @BeforeEach
    void setUp() {
        task = new TestOneShotTask("TestTask", mockExecutor);
    }

    @Test
    void testConstructor() {
        assertEquals("TestTask", task.getTaskName());
        assertSame(mockExecutor, task.getExecutor());
    }

    @Test
    void testSchedule() {
        // Act
        task.schedule();

        // Assert
        verify(mockExecutor).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void testSchedule_ExecuteTaskException() {
        // Arrange
        task.setShouldThrowException(true);

        // Act
        task.schedule();

        // Assert
        verify(mockExecutor).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        // Note: We can't easily verify that the log.error was called due to it being a static method
    }

    private static class TestOneShotTask extends OneShotTaskWithRandomizedStart {
        private boolean shouldThrowException = false;

        TestOneShotTask(String taskName, ScheduledThreadPoolExecutor executor) {
            super(taskName, executor);
        }

        @Override
        public void executeTask() {
            if (shouldThrowException) {
                throw new RuntimeException("Test exception");
            }
        }

        @Override
        public void initialize() {
            schedule();
        }

        public void setShouldThrowException(boolean shouldThrowException) {
            this.shouldThrowException = shouldThrowException;
        }

        public String getTaskName() {
            return super.taskName;
        }

        public ScheduledThreadPoolExecutor getExecutor() {
            return super.executor;
        }
    }
}

