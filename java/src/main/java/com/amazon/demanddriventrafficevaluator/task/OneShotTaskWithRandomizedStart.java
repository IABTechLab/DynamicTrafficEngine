// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task;

import java.util.Random;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;

/**
 * An abstract base class for one-shot tasks with a randomized initial delay.
 * <p>
 * This class provides a framework for creating tasks that execute once after
 * a random initial delay. The randomization helps to distribute the load when
 * multiple tasks are started simultaneously.
 * </p>
 */
@Log4j2
public abstract class OneShotTaskWithRandomizedStart {
    private static final int DELAY_PERIOD = 1000;
    final String taskName;
    final ScheduledThreadPoolExecutor executor;

    private final Random random = new Random(System.currentTimeMillis());

    protected OneShotTaskWithRandomizedStart(String taskName, ScheduledThreadPoolExecutor executor) {
        this.taskName = taskName;
        this.executor = executor;
    }

    /**
     * Executes the task logic. This method should be implemented by subclasses
     * to define the specific behavior of the task.
     */
    abstract public void executeTask();

    /**
     * Initializes the task. This method should be implemented by subclasses
     * to perform any necessary setup before the task is scheduled for execution.
     */
    abstract public void initialize();

    /**
     * Schedules the task for a single execution after a randomized delay.
     * <p>
     * The initial delay is randomly chosen between 0 and DELAY_PERIOD milliseconds.
     * This method logs the scheduled delay and handles any exceptions that occur
     * during task execution.
     * </p>
     */
    protected void schedule() {
        long initialDelay = random.nextInt(DELAY_PERIOD);

        log.debug("{}: is scheduled and starts with delay: {}", taskName, initialDelay);

        executor.schedule(() -> {
            try {
                executeTask();
            } catch (Exception e) {
                log.error("{}: task failed", taskName, e);
                // Optionally rethrow or handle the exception
            }
        }, initialDelay, TimeUnit.MILLISECONDS);
    }
}
