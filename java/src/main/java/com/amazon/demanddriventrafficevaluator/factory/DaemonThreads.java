// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The scheduled thread pool a default factory creates when the caller passes no executor. Its threads are daemon
 * threads named {@code dte-scheduler-N}, so they never keep the JVM from exiting, whether or not the factory is
 * closed.
 */
final class DaemonThreads {
    static final String THREAD_NAME_PREFIX = "dte-scheduler-";
    private static final int POOL_SIZE = 5;
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    private static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, THREAD_NAME_PREFIX + THREAD_NUMBER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    };

    private DaemonThreads() {
    }

    static ScheduledThreadPoolExecutor newScheduledThreadPool() {
        return new ScheduledThreadPoolExecutor(POOL_SIZE, THREAD_FACTORY);
    }
}
