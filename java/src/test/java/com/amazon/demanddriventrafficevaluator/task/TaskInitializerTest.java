// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void testInit_RunsOnInitializedAfterBothStages() {
        // Arrange
        AtomicBoolean initialized = new AtomicBoolean();
        taskInitializer = new TaskInitializer(Collections.singletonList(mockTask1),
                Collections.singletonList(mockTask2), 10000, () -> initialized.set(true));
        when(mockTask1.getName()).thenReturn("Task1");
        when(mockTask1.getMaximumAttempts()).thenReturn(1);
        when(mockTask2.getName()).thenReturn("Task2");
        when(mockTask2.getMaximumAttempts()).thenReturn(1);

        // Act
        taskInitializer.init();

        // Assert
        assertTrue(initialized.get());
    }

    @Test
    void testInit_DoesNotRunOnInitializedWhenATaskFails() {
        // Arrange
        AtomicBoolean initialized = new AtomicBoolean();
        taskInitializer = new TaskInitializer(Collections.singletonList(mockTask1), null, 10000,
                () -> initialized.set(true));
        when(mockTask1.getName()).thenReturn("Task1");
        when(mockTask1.getMaximumAttempts()).thenReturn(1);
        doThrow(new RuntimeException("Task failed")).when(mockTask1).run();

        // Act & Assert
        assertThrows(RuntimeException.class, () -> taskInitializer.init());
        assertFalse(initialized.get());
    }

    @Test
    void testInit_BeforeEachStageThrowingStopsBeforeAnyTaskOfTheStage() {
        // Arrange: the check passes for stage one, then fails
        AtomicInteger checks = new AtomicInteger();
        AtomicBoolean initialized = new AtomicBoolean();
        taskInitializer = new TaskInitializer(Collections.singletonList(mockTask1), Collections.singletonList(mockTask2),
                10000, () -> initialized.set(true), () -> {
                    if (checks.incrementAndGet() > 1) {
                        throw new IllegalStateException("closed");
                    }
                });
        when(mockTask1.getName()).thenReturn("Task1");
        when(mockTask1.getMaximumAttempts()).thenReturn(1);

        // Act & Assert: stage one runs, stage two does not start, and onInitialized is not run
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> taskInitializer.init());
        assertEquals("closed", e.getMessage());
        verify(mockTask1, times(1)).run();
        verify(mockTask2, times(0)).run();
        assertFalse(initialized.get());
    }

    @Test
    void testInit_BeforeEachStageRunsBeforeEveryStageAndBeforeOnInitialized() {
        AtomicInteger checks = new AtomicInteger();
        taskInitializer = new TaskInitializer(Collections.singletonList(mockTask1), Collections.singletonList(mockTask2),
                10000, () -> assertEquals(3, checks.get()), checks::incrementAndGet);
        when(mockTask1.getName()).thenReturn("Task1");
        when(mockTask1.getMaximumAttempts()).thenReturn(1);
        when(mockTask2.getName()).thenReturn("Task2");
        when(mockTask2.getMaximumAttempts()).thenReturn(1);

        taskInitializer.init();

        assertEquals(3, checks.get());
    }

    @Test
    void testConstructor_RejectsNullOnInitialized() {
        assertThrows(NullPointerException.class,
                () -> new TaskInitializer(Collections.singletonList(mockTask1), null, 10000, null));
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
    void testInit_StageThatFinishesInsideTheTimeoutAfterSeveralPollsSucceeds() {
        // Arrange: five tasks finish at about 0.4, 0.8, 1.2, 1.6 and 2.0 s, well inside the 3.5 s timeout. Adding the
        // elapsed time on every poll would count about 4 s.
        AtomicBoolean initialized = new AtomicBoolean();
        taskInitializer = new TaskInitializer(List.of(sleepingTask("Task1", 400), sleepingTask("Task2", 800),
                sleepingTask("Task3", 1200), sleepingTask("Task4", 1600), sleepingTask("Task5", 2000)),
                null, 3500, () -> initialized.set(true));

        // Act & Assert
        assertDoesNotThrow(() -> taskInitializer.init());
        assertTrue(initialized.get());
    }

    @Test
    void testInit_SecondCallGetsTheFullTimeout() {
        // Arrange: each call takes about 1.8 s of a 3 s timeout. Adding the elapsed time on every poll and never
        // resetting the sum would count about 1.8 s for the first call and 3.6 s by the end of the second.
        taskInitializer = new TaskInitializer(List.of(sleepingTask("Task1", 600), sleepingTask("Task2", 1200),
                sleepingTask("Task3", 1800)), null, 3000);

        // Act & Assert
        assertDoesNotThrow(() -> taskInitializer.init());
        assertDoesNotThrow(() -> taskInitializer.init());
    }

    @Test
    void testInit_TimeoutBoundsBothStagesTogether() {
        // Arrange: each stage fits in the 2 s timeout, both together do not
        AtomicBoolean initialized = new AtomicBoolean();
        taskInitializer = new TaskInitializer(List.of(sleepingTask("Task1", 1200)), List.of(sleepingTask("Task2", 1200)),
                2000, () -> initialized.set(true));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> taskInitializer.init());
        assertFalse(initialized.get());
    }

    @Test
    void testInit_FailedInitStopsTheRetriesOfItsUnfinishedTasks() {
        // Arrange: Task1 fails at once; Task2 fails too but would retry every 50 ms for about 5 s
        AtomicInteger task2Runs = new AtomicInteger();
        InitializerTask task2 = new InitializerTask("Task2", 100, 50L, 50L) {
            @Override
            public void run() {
                task2Runs.incrementAndGet();
                throw new IllegalStateException("still failing");
            }
        };
        InitializerTask task1 = new InitializerTask("Task1", 1, 0L, 0L) {
            @Override
            public void run() {
                throw new IllegalStateException("failed");
            }
        };
        taskInitializer = new TaskInitializer(List.of(task1, task2), null, 10000);

        // Act
        long started = System.currentTimeMillis();
        assertThrows(RuntimeException.class, () -> taskInitializer.init());
        long elapsedMs = System.currentTimeMillis() - started;
        int runsWhenInitThrew = task2Runs.get();

        // Assert: init() cancels Task2's remaining attempts instead of waiting for its retries to run out
        assertTrue(elapsedMs < 2000, "init() returned after " + elapsedMs + " ms");
        assertTrue(runsWhenInitThrew < 100);
        assertDoesNotThrow(() -> Thread.sleep(200));
        assertEquals(runsWhenInitThrew, task2Runs.get(), "Task2 retried after init() threw");
    }

    @Test
    void testInit_FailedInitLetsAnAttemptInProgressFinish() {
        // Arrange: Task1 fails as soon as Task2 is in the middle of a 500 ms attempt
        CountDownLatch task2Started = new CountDownLatch(1);
        AtomicBoolean task2Interrupted = new AtomicBoolean();
        AtomicBoolean task2Finished = new AtomicBoolean();
        InitializerTask task2 = new InitializerTask("Task2", 1, 0L, 0L) {
            @Override
            public void run() {
                task2Started.countDown();
                try {
                    Thread.sleep(500);
                    task2Finished.set(true);
                } catch (InterruptedException e) {
                    task2Interrupted.set(true);
                    Thread.currentThread().interrupt();
                }
            }
        };
        InitializerTask task1 = new InitializerTask("Task1", 1, 0L, 0L) {
            @Override
            public void run() {
                try {
                    task2Started.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException("failed");
            }
        };
        taskInitializer = new TaskInitializer(List.of(task1, task2), null, 10000);

        // Act
        assertThrows(RuntimeException.class, () -> taskInitializer.init());

        // Assert: a load in progress is never cut off half way; init() waits for it before it throws
        assertFalse(task2Interrupted.get(), "Task2 was interrupted");
        assertTrue(task2Finished.get(), "init() threw before Task2's attempt finished");
    }

    @Test
    void testInit_RunsEachTaskThroughSubmitTask() {
        // Arrange: a subclass that overrides the protected submitTask
        AtomicInteger submitted = new AtomicInteger();
        taskInitializer = new TaskInitializer(List.of(sleepingTask("Task1", 0)), List.of(sleepingTask("Task2", 0)),
                10000) {
            @Override
            protected void submitTask(long started, InitializerTask task) {
                submitted.incrementAndGet();
                super.submitTask(started, task);
            }
        };

        // Act
        taskInitializer.init();

        // Assert
        assertEquals(2, submitted.get());
    }

    @Test
    void testSubmitTask_RetriesExceededKeepsTheLastFailureAsTheCause() {
        // Arrange
        taskInitializer = new TaskInitializer(null, null, 10000);
        IllegalStateException failure = new IllegalStateException("S3 unavailable");
        InitializerTask task = new InitializerTask("Task1", 2, 1L, 1L) {
            @Override
            public void run() {
                throw failure;
            }
        };

        // Act
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> taskInitializer.submitTask(System.currentTimeMillis(), task));

        // Assert
        assertEquals(failure, thrown.getCause());
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

    private static InitializerTask sleepingTask(String name, long sleepMs) {
        return new InitializerTask(name, 1, 0L, 0L) {
            @Override
            public void run() {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        };
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
