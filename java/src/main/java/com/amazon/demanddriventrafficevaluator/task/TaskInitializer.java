// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;

/**
 * A class responsible for initializing and executing tasks in multiple stages.
 * <p>
 * This class manages the execution of initialization tasks in two stages, with
 * support for retries, timeouts, and parallel execution.
 * </p>
 */
@Log4j2
public class TaskInitializer {
    // The cancel signal of the stage whose task the current thread runs; unset when submitTask is called directly.
    private static final ThreadLocal<CountDownLatch> STAGE_CANCELLED = new ThreadLocal<>();

    private final List<InitializerTask> stageOneTasks;
    private final List<InitializerTask> stageTwoTasks;
    private final long overallTimeoutMs;
    private final Runnable onInitialized;
    private final Runnable beforeEachStage;

    public TaskInitializer(List<InitializerTask> stageOneTasks, List<InitializerTask> stageTwoTasks, long overallTimeoutMs) {
        this(stageOneTasks, stageTwoTasks, overallTimeoutMs, () -> { });
    }

    /**
     * @param onInitialized Run by {@link #init()} once the tasks of both stages have completed without an exception;
     *                      not run when there are no stage one tasks.
     */
    public TaskInitializer(List<InitializerTask> stageOneTasks, List<InitializerTask> stageTwoTasks, long overallTimeoutMs,
                           Runnable onInitialized) {
        this(stageOneTasks, stageTwoTasks, overallTimeoutMs, onInitialized, () -> { });
    }

    /**
     * @param onInitialized   Run by {@link #init()} once the tasks of both stages have completed without an exception;
     *                        not run when there are no stage one tasks.
     * @param beforeEachStage Run by {@link #init()} before each stage starts, and before {@code onInitialized}; an
     *                        exception it throws ends {@code init()} with that exception, before any task of the stage
     *                        runs. For example, a factory that has been closed throws {@link IllegalStateException}.
     */
    public TaskInitializer(List<InitializerTask> stageOneTasks, List<InitializerTask> stageTwoTasks, long overallTimeoutMs,
                           Runnable onInitialized, Runnable beforeEachStage) {
        this.stageOneTasks = stageOneTasks;
        this.stageTwoTasks = stageTwoTasks;
        this.overallTimeoutMs = overallTimeoutMs;
        this.onInitialized = Objects.requireNonNull(onInitialized, "onInitialized");
        this.beforeEachStage = Objects.requireNonNull(beforeEachStage, "beforeEachStage");
    }

    /**
     * Calculates the delay before the next retry attempt.
     *
     * @param timesAlreadyAttempted Number of times the task has been attempted.
     * @param currentDelay          Current delay value.
     * @param minDelay              Minimum delay allowed.
     * @param maxDelay              Maximum delay allowed.
     * @return The calculated delay for the next attempt.
     */
    static long calculateDelay(int timesAlreadyAttempted, final long currentDelay, long minDelay, long maxDelay) {
        return switch (timesAlreadyAttempted) {
            case 1 -> 0;
            case 2 -> minDelay;
            default -> Math.min(2 * currentDelay, maxDelay);
        };
    }

    /**
     * Initializes and executes all tasks in both stages.
     * <p>
     * This method executes stage one tasks, followed by stage two tasks if present.
     * It manages the execution pool and handles overall timeout: both stages together must complete within
     * {@code overallTimeoutMs} of the start of this call. Each stage runs its tasks on a thread pool of its own,
     * which is shut down when the stage ends; when a stage fails, its unfinished tasks make no further attempt,
     * and this method waits up to 60 s for an attempt in progress to finish before it throws.
     * </p>
     * <p>
     * With the default factories, stage one loads the model and experiment configurations and
     * schedules the feature extractor and transformer registration, which runs within about one
     * second; stage two only schedules the periodic model-result and bloom-filter loading. The first
     * model load runs on that schedule after a random delay of up to one refresh period (300 s by
     * default), so this method does not wait for the model results. Until they are loaded, each
     * model returns its no-match value: 1.0 (forward) for a {@code LowValue} model and 0.0 (low
     * value) for a {@code HighValue} model. Callers that need to know when the first load has
     * finished use {@code BidRequestEvaluatorFactory.isReady()}.
     * </p>
     *
     * @throws RuntimeException if a task fails after its retries or the overall timeout elapses.
     */
    public void init() {
        if (stageOneTasks != null && !stageOneTasks.isEmpty()) {
            long startTime = System.currentTimeMillis();
            beforeEachStage.run();
            executeTasks(stageOneTasks, "StageOne", startTime);
            if (stageTwoTasks != null && !stageTwoTasks.isEmpty()) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= overallTimeoutMs) {
                    throw new RuntimeException("Timed out after stage one and " + elapsed + " ms, before stage two");
                }
                beforeEachStage.run();
                executeTasks(stageTwoTasks, "StageTwo", startTime);
            }
            beforeEachStage.run();
            onInitialized.run();
        } else {
            log.warn("No initialization tasks defined.");
        }
    }

    /**
     * Iterates through task results, handling timeouts and exceptions.
     *
     * @param completionService The CompletionService managing the tasks.
     * @param tasks             List of tasks being executed.
     * @param startTime         The start time of the {@link #init()} call.
     * @throws RuntimeException if a task times out or fails.
     */
    private void tasksResultIterator(CompletionService<Void> completionService, List<InitializerTask> tasks, long startTime) {
        int executedTaskCount = 0;
        long elapsed;

        while (((elapsed = System.currentTimeMillis() - startTime) < overallTimeoutMs)
                && (executedTaskCount < tasks.size())
        ) {
            try {
                Future<?> completionFuture = completionService.poll(overallTimeoutMs - elapsed,
                        TimeUnit.MILLISECONDS);
                if (completionFuture == null) {
                    throw new RuntimeException("Timed out after " + executedTaskCount + " of " + tasks.size()
                            + " tasks and " + (System.currentTimeMillis() - startTime) + " ms");
                }
                completionFuture.get();
                executedTaskCount++;
            } catch (ExecutionException e) {
                // tasks complete in any order; the cause names the failed task
                throw new RuntimeException("Task execution failed with following exception " + e.getCause(), e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted after " + executedTaskCount + " of " + tasks.size() + " tasks", e);
            }
        }
        if (executedTaskCount < tasks.size()) {
            throw new RuntimeException("Timed out after " + executedTaskCount + " of " + tasks.size() + " tasks and "
                    + (System.currentTimeMillis() - startTime) + " ms");
        }
    }

    /**
     * Executes a list of tasks for a given stage.
     *
     * @param tasks     List of tasks to execute.
     * @param stageName Name of the current stage.
     * @param startTime Start time of the {@link #init()} call; the overall timeout is measured from it.
     * @throws RuntimeException if tasks fail to complete within the overall timeout.
     */
    private void executeTasks(List<InitializerTask> tasks, String stageName, long startTime) {
        ExecutorService executorPool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch cancelled = new CountDownLatch(1);
        boolean completed = false;
        try {
            CompletionService<Void> completionService = new ExecutorCompletionService<>(executorPool);
            for (InitializerTask task : tasks) {
                completionService.submit(() -> {
                    STAGE_CANCELLED.set(cancelled);
                    try {
                        submitTask(startTime, task);
                    } finally {
                        STAGE_CANCELLED.remove();
                    }
                    return null;
                });
            }
            tasksResultIterator(completionService, tasks, startTime);
            completed = true;
        } finally {
            if (!completed) {
                cancelled.countDown();
            }
            shutDown(executorPool, stageName);
        }
        log.debug("{} tasks initialization completed {} seconds after init() started", stageName, ((System.currentTimeMillis() - startTime) / (double) 1000));
    }

    /**
     * Shuts down the thread pool of a stage and waits up to 60 s for its tasks to stop. The pool is not
     * interrupted: an attempt in progress finishes, so that a load is never cut off half way; after a failure the
     * stage is cancelled first, so its unfinished tasks make no further attempt. The wait is skipped when the
     * calling thread is interrupted.
     */
    private static void shutDown(ExecutorService executorPool, String stageName) {
        executorPool.shutdown();
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        try {
            if (!executorPool.awaitTermination(60000, TimeUnit.MILLISECONDS)) {
                log.warn("{} tasks still running 60 s after the stage ended", stageName);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("{} interrupted awaiting the termination of its tasks", stageName, e);
        }
    }

    /**
     * Submits and executes a single task with retry logic.
     * <p>
     * When {@link #init()} runs it and the task's stage fails, the wait before the next attempt ends at once and
     * no further attempt is made.
     * </p>
     *
     * @param started The start time of the overall initialization process.
     * @param task    The task to be executed.
     * @throws RuntimeException if the maximum number of retries is exceeded, the stage is cancelled or the thread
     *                          is interrupted.
     */
    protected void submitTask(long started, InitializerTask task) {
        CountDownLatch cancelled = STAGE_CANCELLED.get();
        final int maximumAttempts = task.getMaximumAttempts();
        int attemptCount = 0;
        long delay = 0;
        Exception lastFailure = null;
        while (true) {
            if (++attemptCount > maximumAttempts) {
                throw new RuntimeException("Number of retries exceeded for task " + task.getName(), lastFailure);
            }
            try {
                delay = calculateDelay(attemptCount, delay, task.getMinDelayBeforeAttemptMs(), task.getMaxDelayBeforeAttemptMs());
                if (cancelled == null) {
                    Thread.sleep(delay);
                } else if (cancelled.await(delay, TimeUnit.MILLISECONDS)) {
                    throw new RuntimeException("Task " + task.getName() + " cancelled before attempt " + attemptCount
                            + " because its stage failed", lastFailure);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Delay interrupted", e);
            }

            if (cancelled != null && cancelled.getCount() == 0) {
                throw new RuntimeException("Task " + task.getName() + " cancelled before attempt " + attemptCount
                        + " because its stage failed", lastFailure);
            }
            try {
                task.run();
                log.debug("Initialization task {} completed after attempt {}. Elapsed time is {}", task.getName(), attemptCount, ((System.currentTimeMillis() - started) / (double) 1000));
                break;

            } catch (Exception e) {
                log.error("Task {} attempt {} failed", task.getName(), attemptCount, e);
                lastFailure = e;
                if (Thread.currentThread().isInterrupted()) {
                    throw new RuntimeException("Task " + task.getName() + " interrupted after attempt " + attemptCount, e);
                }
            }
        }
    }
}
