// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task.dataloading;

import com.amazon.demanddriventrafficevaluator.task.InitializerTask;

public class ModelResultPeriodicLoadingInitializerTask extends InitializerTask {

    private final ModelResultPeriodicLoadingTask task;

    public ModelResultPeriodicLoadingInitializerTask(String taskName, int maximumAttempts, long minDelayBeforeAttemptMs, long maxDelayBeforeAttemptMs, ModelResultPeriodicLoadingTask task) {
        super(taskName, maximumAttempts, minDelayBeforeAttemptMs, maxDelayBeforeAttemptMs);
        this.task = task;
    }

    /**
     * When an object implementing interface {@code Runnable} is used
     * to create a thread, starting the thread causes the object's
     * {@code run} method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method {@code run} is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        task.initialize();
    }
}
