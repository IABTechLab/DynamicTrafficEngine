// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import com.amazon.demanddriventrafficevaluator.task.InitializerTask;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Runs its task until one run returns normally, then does nothing.
 * <p>
 * The default initializer tasks load a configuration and schedule its periodic refresh, schedule a periodic model
 * loader, or schedule a one-shot registry setup. Each of those must happen once per factory however often
 * {@code init()} is called, so the factories build their tasks once, wrap each one in this class and share them
 * between every {@code TaskInitializer} they return: a retry after a failed {@code init()} runs only the tasks that
 * did not succeed.
 * </p>
 * <p>
 * A run never waits for another: when a run is still in progress, for example one left over from an attempt that
 * timed out, {@link #run()} throws at once and the caller counts a failed attempt. The task never runs twice at
 * the same time. With the default retry settings the attempts of one {@code init()} are spent in about 1.5 s, so an
 * {@code init()} that overlaps a run still in progress usually fails; the next retry, after the earlier run has
 * ended, proceeds.
 * </p>
 * <p>
 * Success means that {@link InitializerTask#run()} returned normally. For a task that only schedules work, such
 * as the registry setup or a model loader, that is the scheduling, so the scheduled work is not repeated by a
 * retry even when it later fails; it logs its failure, and the model loaders retry on their periodic schedule.
 * </p>
 */
final class RunUntilSucceeded extends InitializerTask {
    private final InitializerTask task;
    private final ReentrantLock lock = new ReentrantLock();
    // guarded by lock
    private boolean succeeded;

    RunUntilSucceeded(InitializerTask task) {
        super(task.getName(), task.getMaximumAttempts(), task.getMinDelayBeforeAttemptMs(),
                task.getMaxDelayBeforeAttemptMs());
        this.task = task;
    }

    /**
     * @return the wrapped task.
     */
    InitializerTask getTask() {
        return task;
    }

    @Override
    public void run() {
        if (!lock.tryLock()) {
            throw new IllegalStateException("An earlier run of " + getName() + " is still in progress");
        }
        try {
            if (!succeeded) {
                task.run();
                succeeded = true;
            }
        } finally {
            lock.unlock();
        }
    }
}
