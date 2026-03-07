// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task;

import lombok.Getter;

import java.util.Random;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;

/**
 * An abstract base class for periodic tasks with a randomized initial delay.
 * <p>
 * This class provides a framework for creating tasks that execute periodically,
 * with the first execution occurring after a random delay. This randomization
 * helps to distribute the load when multiple tasks are started simultaneously.
 * </p>
 */

@Log4j2
@Getter
public abstract class PeriodicTaskWithRandomizedStart {

    private final String sspIdentifier;
    private final String taskName;
    private final long periodMs;
    private final ScheduledThreadPoolExecutor executor;

    private final Random random = new Random(System.currentTimeMillis());

    protected PeriodicTaskWithRandomizedStart(String sspIdentifier, String taskName, long periodMs, ScheduledThreadPoolExecutor executor) {
        this.sspIdentifier = sspIdentifier;
        this.taskName = taskName;
        this.periodMs = periodMs;
        this.executor = executor;
    }

    /**
     * Executes the task logic. This method should be implemented by subclasses
     * to define the specific behavior of the task.
     */
    abstract public void executeTask();

    /**
     * Initializes the task. This method should be implemented by subclasses
     * to perform any necessary setup before the task starts executing periodically.
     */
    abstract public void initialize();

    /**
     * Schedules the task to run periodically with a specified initial delay.
     *
     * @param initialDelay The delay in milliseconds before the first execution of the task.
     */
    protected void schedulePeriodically(long initialDelay) {
        executor.scheduleAtFixedRate(() -> {
            try {
                executeTask();
            } catch (Exception e) {
                log.error("{}: task failed", taskName, e);
                // Optionally rethrow or handle the exception
            }
        }, initialDelay, periodMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Schedules the task to run periodically with a randomized initial delay.
     * <p>
     * The initial delay is randomly chosen between 0 and the specified period.
     * This method ensures that the period is within acceptable bounds before scheduling.
     * </p>
     *
     * @throws IllegalArgumentException if the period is less than 1 second or greater than Integer.MAX_VALUE.
     */
    public void schedulePeriodically() {
        if (periodMs < 1000) {
            throw new IllegalArgumentException("periods of less than one second not supported");
        } else if (periodMs > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("periods of over " + Integer.MAX_VALUE + " not supported");
        }

        long initialDelay = random.nextInt((int) periodMs);
        log.debug("{}: scheduling every {} ms starting with delay: {}", taskName, periodMs, initialDelay);
        schedulePeriodically(initialDelay);
    }
}
