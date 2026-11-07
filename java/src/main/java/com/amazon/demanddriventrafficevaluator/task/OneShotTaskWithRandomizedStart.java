// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task;

import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
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
    /**
     * The executor passed to the constructor if it is a {@link ScheduledThreadPoolExecutor}, otherwise null.
     *
     * @deprecated null for any other {@link ScheduledExecutorService}; use {@link #getScheduledExecutorService()}.
     */
    @Deprecated
    final ScheduledThreadPoolExecutor executor;
    private final ScheduledExecutorService scheduler;

    private final Random random = new Random(System.currentTimeMillis());

    protected OneShotTaskWithRandomizedStart(String taskName, ScheduledThreadPoolExecutor executor) {
        this(taskName, (ScheduledExecutorService) executor);
    }

    protected OneShotTaskWithRandomizedStart(String taskName, ScheduledExecutorService executor) {
        this.taskName = taskName;
        this.executor = executor instanceof ScheduledThreadPoolExecutor ? (ScheduledThreadPoolExecutor) executor : null;
        this.scheduler = executor;
    }

    /**
     * @return the executor passed to the constructor, never null unless null was passed.
     */
    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduler;
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

        scheduler.schedule(() -> {
            try {
                executeTask();
            } catch (Exception e) {
                if (scheduler.isShutdown()) {
                    // Expected when the factory is closed during the run.
                    log.debug("{}: task failed after its executor was shut down", taskName, e);
                } else {
                    log.error("{}: task failed", taskName, e);
                }
                // Optionally rethrow or handle the exception
            }
        }, initialDelay, TimeUnit.MILLISECONDS);
    }
}
