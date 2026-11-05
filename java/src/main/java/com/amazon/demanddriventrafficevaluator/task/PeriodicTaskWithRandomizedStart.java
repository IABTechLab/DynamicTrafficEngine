// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task;

import lombok.AccessLevel;
import lombok.Getter;

import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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
    private final ScheduledExecutorService executor;

    private final Random random = new Random(System.currentTimeMillis());

    // Written on the scheduler thread, read by callers of hasFinishedFirstRun().
    @Getter(AccessLevel.NONE)
    private volatile boolean firstRunFinished;

    // The periodic schedule, once set; null before, or when the executor returned none.
    @Getter(AccessLevel.NONE)
    private volatile ScheduledFuture<?> scheduled;
    @Getter(AccessLevel.NONE)
    private volatile boolean cancelled;

    protected PeriodicTaskWithRandomizedStart(String sspIdentifier, String taskName, long periodMs, ScheduledThreadPoolExecutor executor) {
        this(sspIdentifier, taskName, periodMs, (ScheduledExecutorService) executor);
    }

    protected PeriodicTaskWithRandomizedStart(String sspIdentifier, String taskName, long periodMs, ScheduledExecutorService executor) {
        this.sspIdentifier = sspIdentifier;
        this.taskName = taskName;
        this.periodMs = periodMs;
        this.executor = executor;
    }

    /**
     * Returns the executor passed to the constructor if it is a {@link ScheduledThreadPoolExecutor},
     * otherwise null.
     *
     * @deprecated returns null for any other {@link ScheduledExecutorService}; use
     *             {@link #getScheduledExecutorService()}, which always returns the executor.
     */
    @Deprecated
    public ScheduledThreadPoolExecutor getExecutor() {
        return executor instanceof ScheduledThreadPoolExecutor ? (ScheduledThreadPoolExecutor) executor : null;
    }

    /**
     * @return the executor passed to the constructor, never null unless null was passed.
     */
    public ScheduledExecutorService getScheduledExecutorService() {
        return executor;
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
     * Tells whether the first scheduled execution of this task has finished.
     * <p>
     * True once the first execution scheduled by {@link #schedulePeriodically(long)} has returned or thrown,
     * whatever its outcome. An execution run directly through {@link #executeTask()}, for example by
     * {@link #initialize()}, does not count. Once true it stays true.
     * </p>
     *
     * @return true when the first scheduled execution has finished.
     */
    public boolean hasFinishedFirstRun() {
        return firstRunFinished;
    }

    /**
     * Schedules the task to run periodically with a specified initial delay.
     *
     * @param initialDelay The delay in milliseconds before the first execution of the task.
     */
    protected void schedulePeriodically(long initialDelay) {
        scheduled = executor.scheduleAtFixedRate(() -> {
            if (cancelled) {
                // A run skipped after cancel() did not load anything, so it does not count as a first run.
                return;
            }
            try {
                executeTask();
            } catch (Exception e) {
                if (cancelled || executor.isShutdown()) {
                    // Expected when the factory is closed during a run, for example an interrupted load.
                    log.debug("{}: task failed after it was stopped", taskName, e);
                } else {
                    log.error("{}: task failed", taskName, e);
                }
            } finally {
                firstRunFinished = true;
            }
        }, initialDelay, periodMs, TimeUnit.MILLISECONDS);
        if (cancelled) {
            cancel();
        }
    }

    /**
     * Stops the periodic schedule: no further run starts, and a run in progress is not interrupted. Safe to call
     * before the task is scheduled, in which case a later schedule is cancelled at once, and more than once.
     */
    public void cancel() {
        cancelled = true;
        ScheduledFuture<?> future = scheduled;
        if (future != null) {
            future.cancel(false);
        }
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
