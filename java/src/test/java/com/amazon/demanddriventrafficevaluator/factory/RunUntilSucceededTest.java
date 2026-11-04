// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import com.amazon.demanddriventrafficevaluator.task.InitializerTask;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunUntilSucceededTest {

    @Test
    void testRun_runsAgainAfterAFailureAndNotAfterASuccess() {
        // Arrange
        AtomicInteger runs = new AtomicInteger();
        RunUntilSucceeded task = new RunUntilSucceeded(new InitializerTask("Task", 3, 1L, 2L) {
            @Override
            public void run() {
                if (runs.incrementAndGet() == 1) {
                    throw new IllegalStateException("first run fails");
                }
            }
        });

        // Act & Assert
        assertEquals("Task", task.getName());
        assertEquals(3, task.getMaximumAttempts());
        assertThrows(IllegalStateException.class, task::run);
        task.run();
        task.run();
        assertEquals(2, runs.get());
    }

    @Test
    void testRun_runWhileAnotherIsInProgressFailsAtOnceWithoutRunningTheTask() throws Exception {
        // Arrange: a first run blocks until released
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        RunUntilSucceeded task = new RunUntilSucceeded(new InitializerTask("Task", 1, 0L, 0L) {
            @Override
            public void run() {
                runs.incrementAndGet();
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        });
        Thread first = new Thread(task);
        first.setDaemon(true);
        first.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));

        // Act & Assert: a second run does not wait and does not run the task
        assertThrows(IllegalStateException.class, task::run);
        assertEquals(1, runs.get());
        release.countDown();
        first.join(5000);
        assertFalse(first.isAlive());
        task.run();
        assertEquals(1, runs.get(), "the first run succeeded, so no later run repeats it");
    }
}
