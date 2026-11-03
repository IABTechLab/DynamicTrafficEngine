// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PeriodicTaskWithRandomizedStartTest {

    private static final long PERIOD_MS = 300000L;

    @Mock
    private ScheduledThreadPoolExecutor mockExecutor;

    @Test
    void testHasFinishedFirstRun_falseUntilTheFirstScheduledRunFinishes() {
        // Arrange
        TestTask task = new TestTask(null);

        // Act
        task.schedulePeriodically();
        Runnable scheduledRun = captureScheduledRun();

        // Assert
        assertFalse(task.hasFinishedFirstRun(), "scheduling alone does not run the task");
        scheduledRun.run();
        assertTrue(task.hasFinishedFirstRun());
        assertTrue(task.executed);
    }

    @Test
    void testHasFinishedFirstRun_trueWhenTheFirstRunThrowsAnException() {
        // Arrange
        TestTask task = new TestTask(new IllegalStateException("load failed"));
        task.schedulePeriodically();
        Runnable scheduledRun = captureScheduledRun();

        // Act: the exception is logged by the scheduled wrapper, as before
        assertDoesNotThrow(scheduledRun::run);

        // Assert
        assertTrue(task.hasFinishedFirstRun());
    }

    @Test
    void testHasFinishedFirstRun_trueWhenTheFirstRunThrowsAnError() {
        // Arrange
        TestTask task = new TestTask(new AssertionError("fatal"));
        task.schedulePeriodically();
        Runnable scheduledRun = captureScheduledRun();

        // Act: an Error is not caught and ends the schedule, but the run has still finished
        assertThrows(AssertionError.class, scheduledRun::run);

        // Assert
        assertTrue(task.hasFinishedFirstRun());
    }

    @Test
    void testSchedulePeriodically_firstRunDelayIsWithinOnePeriod() {
        // Arrange
        TestTask task = new TestTask(null);

        // Act
        task.schedulePeriodically();

        // Assert
        ArgumentCaptor<Long> initialDelay = ArgumentCaptor.forClass(Long.class);
        verify(mockExecutor).scheduleAtFixedRate(any(Runnable.class),
                initialDelay.capture(), eq(PERIOD_MS), eq(TimeUnit.MILLISECONDS));
        assertTrue(initialDelay.getValue() >= 0 && initialDelay.getValue() < PERIOD_MS);
    }

    private Runnable captureScheduledRun() {
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        verify(mockExecutor).scheduleAtFixedRate(runnable.capture(), anyLong(), eq(PERIOD_MS),
                eq(TimeUnit.MILLISECONDS));
        return runnable.getValue();
    }

    private final class TestTask extends PeriodicTaskWithRandomizedStart {
        private final Throwable failure;
        private boolean executed;

        TestTask(Throwable failure) {
            super("ssp", "TestTask", PERIOD_MS, mockExecutor);
            this.failure = failure;
        }

        @Override
        public void executeTask() {
            executed = true;
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            } else if (failure instanceof Error) {
                throw (Error) failure;
            }
        }

        @Override
        public void initialize() {
            schedulePeriodically();
        }
    }
}
