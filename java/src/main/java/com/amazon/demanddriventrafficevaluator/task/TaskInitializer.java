// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionService;
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
    private final List<InitializerTask> stageOneTasks;
    private final List<InitializerTask> stageTwoTasks;
    private final long overallTimeoutMs;
    private final Runnable onInitialized;
    private ExecutorService executorPool;
    private long taskElapsedTime = 0L;

    public TaskInitializer(List<InitializerTask> stageOneTasks, List<InitializerTask> stageTwoTasks, long overallTimeoutMs) {
        this(stageOneTasks, stageTwoTasks, overallTimeoutMs, () -> { });
    }

    /**
     * @param onInitialized Run by {@link #init()} once the tasks of both stages have completed without an exception;
     *                      not run when there are no stage one tasks.
     */
    public TaskInitializer(List<InitializerTask> stageOneTasks, List<InitializerTask> stageTwoTasks, long overallTimeoutMs,
                           Runnable onInitialized) {
        this.stageOneTasks = stageOneTasks;
        this.stageTwoTasks = stageTwoTasks;
        this.overallTimeoutMs = overallTimeoutMs;
        this.onInitialized = Objects.requireNonNull(onInitialized, "onInitialized");
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
     * It manages the execution pool and handles overall timeout.
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
            try {
                executeTasks(stageOneTasks, "StageOne", System.currentTimeMillis());
                if (stageTwoTasks != null && !stageTwoTasks.isEmpty()) {
                    executeTasks(stageTwoTasks, "StageTwo", System.currentTimeMillis());
                }
                onInitialized.run();
            } finally {
                executorPool.shutdown();
                try {
                    executorPool.awaitTermination(60000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    log.error("exception awaiting termination", e);
                }
            }
        } else {
            log.warn("No initialization tasks defined.");
        }
    }

    private ExecutorService createExecutorService(int size) {
        executorPool = Executors.newFixedThreadPool(size);
        return executorPool;
    }

    /**
     * Iterates through task results, handling timeouts and exceptions.
     *
     * @param completionService The CompletionService managing the tasks.
     * @param tasks             List of tasks being executed.
     * @param startTime         The start time of task execution.
     * @throws RuntimeException if a task times out or fails.
     */
    private void tasksResultIterator(CompletionService<Void> completionService, List<InitializerTask> tasks, long startTime) {
        int executedTaskCount = 0;
        long elapsed;

        while ((taskElapsedTime < overallTimeoutMs)
                && ((elapsed = System.currentTimeMillis() - startTime) < overallTimeoutMs)
                && (executedTaskCount < tasks.size())
        ) {
            try {
                Future<?> completionFuture = completionService.poll(overallTimeoutMs - elapsed,
                        TimeUnit.MILLISECONDS);
                if (completionFuture == null) {
                    throw new RuntimeException("Timed out after " + executedTaskCount + " tasks and "
                            + ((System.currentTimeMillis() - startTime)) + " ms");
                }
                completionFuture.get();
                executedTaskCount++;
            } catch (ExecutionException e) {
                throw new RuntimeException(String.format("Task %s execution failed with following exception %s ",
                        tasks.get(executedTaskCount).getName(), e.getCause()));
            } catch (InterruptedException e) {
                throw new RuntimeException(String.format("Task %s execution interrupted with following exception %s ",
                        tasks.get(executedTaskCount).getName(), e.getCause()));
            }
            taskElapsedTime += elapsed;
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
     * @param startTime Start time of the stage execution.
     * @throws RuntimeException if tasks fail to complete within the overall timeout.
     */
    private void executeTasks(List<InitializerTask> tasks, String stageName, long startTime) {
        CompletionService<Void> completionService = new ExecutorCompletionService<>(createExecutorService(tasks.size()));
        for (InitializerTask task : tasks) {
            completionService.submit(() -> {
                submitTask(startTime, task);
                return null;
            });
        }
        try {
            tasksResultIterator(completionService, tasks, startTime);
        } finally {
            if (taskElapsedTime > overallTimeoutMs) {
                throw new RuntimeException(String.format(stageName + " tasks Initialization failed to complete in %s ms", overallTimeoutMs));
            }
            log.debug("{} tasks initialization completed in {} seconds", stageName, ((System.currentTimeMillis() - startTime) / (double) 1000));
        }
    }

    /**
     * Submits and executes a single task with retry logic.
     *
     * @param started The start time of the overall initialization process.
     * @param task    The task to be executed.
     * @throws RuntimeException if the maximum number of retries is exceeded or if interrupted.
     */
    protected void submitTask(long started, InitializerTask task) {
        final int maximumAttempts = task.getMaximumAttempts();
        int attemptCount = 0;
        long delay = 0;
        while (true) {
            if (++attemptCount > maximumAttempts) {
                throw new RuntimeException("Number of retries exceeded for task " + task.getName());
            }
            try {
                delay = calculateDelay(attemptCount, delay, task.getMinDelayBeforeAttemptMs(), task.getMaxDelayBeforeAttemptMs());
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                throw new RuntimeException("Delay interrupted", e);
            }

            try {
                task.run();
                log.debug("Initialization task {} completed after attempt {}. Elapsed time is {}", task.getName(), attemptCount, ((System.currentTimeMillis() - started) / (double) 1000));
                break;

            } catch (Exception e) {
                log.error("Task {} attempt {} failed", task.getName(), attemptCount, e);
            }
        }
    }
}
