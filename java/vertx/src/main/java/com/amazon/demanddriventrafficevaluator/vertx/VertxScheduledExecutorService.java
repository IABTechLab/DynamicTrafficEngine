// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.vertx;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Delayed;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link ScheduledExecutorService} that runs DTE's tasks on Vert.x, for
 * {@code BidRequestEvaluatorFactory.create(..., ScheduledExecutorService)}.
 * <p>
 * Delays are Vert.x timers ({@code setTimer}, {@code setPeriodic}), and every run is
 * {@code executeBlocking} on a {@link WorkerExecutor}, so DTE's blocking S3 loads never run on an
 * event loop. A periodic run is skipped while the previous run of the same task is still in flight,
 * so runs of one task never overlap. A run still waiting for a worker thread is dropped when its
 * task is cancelled or the adapter is shut down.
 * </p>
 * <p>
 * This adapter is only for DTE's use: it implements {@code schedule(Runnable, ...)},
 * {@code scheduleAtFixedRate}, {@code shutdown} and {@code isShutdown}, which is what DTE calls.
 * The other methods throw {@link UnsupportedOperationException}. {@link #shutdown()} cancels every
 * timer and closes the worker executor only if this adapter created it; it never closes {@link Vertx}.
 * </p>
 */
final class VertxScheduledExecutorService implements ScheduledExecutorService {

    private static final Logger LOG = LogManager.getLogger(VertxScheduledExecutorService.class);

    private final Vertx vertx;
    private final WorkerExecutor worker;
    private final boolean ownsWorker;
    private static final long DRAIN_TIMEOUT_MS = 60_000L;

    // A context of the adapter's own, created where the adapter is (off any deployment in VertxDte), for the drain
    // timer: a timer set on a verticle's context, for example from its stop(), is cancelled when it is undeployed.
    private final Context ownContext;
    private volatile long drainTimeoutMs = DRAIN_TIMEOUT_MS;

    private final Set<Task> tasks = ConcurrentHashMap.newKeySet();
    // Runs handed to the worker and not yet complete; shutdown waits for them before closing the worker.
    private final Set<Future<Void>> inFlight = ConcurrentHashMap.newKeySet();
    private volatile Future<Void> shutdownFuture;
    // Set first in shutdownAndClose(), so a queued run that checks it without the lock sees the shutdown at once.
    private volatile boolean shuttingDown;

    /**
     * Runs tasks on the given worker executor, which stays owned by the caller and is never closed here.
     */
    VertxScheduledExecutorService(Vertx vertx, WorkerExecutor worker) {
        this(vertx, worker, false);
    }

    /**
     * Runs tasks on a shared worker executor this adapter creates and closes on {@link #shutdown()}.
     */
    VertxScheduledExecutorService(Vertx vertx, String poolName, int poolSize, long maxExecuteTime, TimeUnit unit) {
        this(vertx, vertx.createSharedWorkerExecutor(poolName, poolSize, maxExecuteTime, unit), true);
    }

    private VertxScheduledExecutorService(Vertx vertx, WorkerExecutor worker, boolean ownsWorker) {
        this.vertx = Objects.requireNonNull(vertx, "vertx");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.ownsWorker = ownsWorker;
        this.ownContext = vertx.getOrCreateContext();
    }

    // Tests only: a shorter bound than 60 s.
    void setDrainTimeoutMs(long drainTimeoutMs) {
        this.drainTimeoutMs = drainTimeoutMs;
    }

    WorkerExecutor worker() {
        return worker;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return start(new Task(command, false, 0L), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        if (period <= 0L) {
            throw new IllegalArgumentException("period must be positive: " + period);
        }
        return start(new Task(command, true, toMillis(period, unit)), initialDelay, unit);
    }

    private synchronized Task start(Task task, long delay, TimeUnit unit) {
        if (shuttingDown) {
            throw new RejectedExecutionException("VertxScheduledExecutorService is shut down");
        }
        long delayMs = toMillis(delay, unit);
        tasks.add(task);
        task.timerId = task.periodic
                ? vertx.setPeriodic(delayMs, task.periodMs, ignored -> task.fire())
                : vertx.setTimer(delayMs, ignored -> task.fire());
        return task;
    }

    // Vert.x timers take whole milliseconds of at least 1.
    private static long toMillis(long delay, TimeUnit unit) {
        return Math.max(1L, unit.toMillis(delay));
    }

    @Override
    public void shutdown() {
        shutdownAndClose();
    }

    /**
     * Shuts down as {@link #shutdown()} does and returns a future completed when the worker executor
     * this adapter created is closed. Runs still waiting for a worker thread do not start. A worker
     * this adapter created is closed only after the runs in progress have finished, or after 60 s,
     * because closing it can interrupt them, which would cut a model load off half way. Idempotent;
     * never blocks.
     */
    synchronized Future<Void> shutdownAndClose() {
        if (shutdownFuture == null) {
            shuttingDown = true;
            tasks.forEach(task -> vertx.cancelTimer(task.timerId));
            tasks.clear();
            if (!ownsWorker) {
                shutdownFuture = Future.succeededFuture();
            } else if (inFlight.isEmpty()) {
                shutdownFuture = worker.close();
            } else {
                Promise<Void> drained = Promise.promise();
                List<Future<Void>> pending = List.copyOf(inFlight);
                long timeoutMs = drainTimeoutMs;
                ownContext.runOnContext(started -> {
                    long timer = vertx.setTimer(timeoutMs, ignored -> {
                        LOG.warn("Closing the DTE worker pool with a run still in progress after {} ms", timeoutMs);
                        drained.tryComplete();
                    });
                    Future.join(pending).onComplete(ignored -> {
                        vertx.cancelTimer(timer);
                        drained.tryComplete();
                    });
                });
                shutdownFuture = drained.future().compose(ignored -> worker.close());
            }
        }
        return shutdownFuture;
    }

    /**
     * Same as {@link #shutdown()}. Defined here because from Java 19 {@code ExecutorService.close()} waits with
     * {@link #awaitTermination}, which this adapter does not support.
     */
    public void close() {
        shutdown();
    }

    @Override
    public boolean isShutdown() {
        return shuttingDown;
    }

    private final class Task implements ScheduledFuture<Object> {

        private final Runnable command;
        private final boolean periodic;
        private final long periodMs;
        private final AtomicBoolean running = new AtomicBoolean();
        // One-shot only: set by the run that starts or by a cancel(), whichever comes first.
        private final AtomicBoolean claimed = new AtomicBoolean();
        private volatile long timerId;
        private volatile boolean cancelled;

        Task(Runnable command, boolean periodic, long periodMs) {
            this.command = Objects.requireNonNull(command, "command");
            this.periodic = periodic;
            this.periodMs = periodMs;
        }

        // Runs on an event loop: hand the run to the worker and return at once.
        void fire() {
            if (cancelled || isShutdown()) {
                return;
            }
            if (!periodic) {
                tasks.remove(this);
            }
            if (!running.compareAndSet(false, true)) {
                LOG.debug("Skipping a periodic run: the previous run is still in flight");
                return;
            }
            Future<Void> run;
            // Under the adapter's lock, so a shutdown either sees this run in flight or rejects it.
            synchronized (VertxScheduledExecutorService.this) {
                if (isShutdown()) {
                    running.set(false);
                    return;
                }
                try {
                    run = worker.executeBlocking(() -> {
                        // The run may have waited in the worker queue; do not start it once cancelled or shut down.
                        if (!cancelled && !isShutdown() && (periodic || claimed.compareAndSet(false, true))) {
                            command.run();
                        }
                        return null;
                    }, false);
                } catch (RuntimeException e) {
                    // for example a worker closed under a Vert.x that is closing: later ticks may still run
                    running.set(false);
                    LOG.warn("Could not hand a scheduled DTE task to the worker pool", e);
                    return;
                }
                inFlight.add(run);
            }
            run.onComplete(result -> {
                inFlight.remove(run);
                running.set(false);
                if (result.failed()) {
                    if (isShutdown()) {
                        // Expected when a run races shutdown(), for example the worker being closed under it.
                        LOG.debug("Scheduled DTE task failed after shutdown", result.cause());
                    } else {
                        LOG.error("Scheduled DTE task failed", result.cause());
                    }
                }
            });
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (cancelled || (!periodic && !claimed.compareAndSet(false, true))) {
                return false;
            }
            cancelled = true;
            tasks.remove(this);
            vertx.cancelTimer(timerId);
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled || (!periodic && claimed.get());
        }

        @Override
        public long getDelay(TimeUnit unit) {
            throw unsupported("ScheduledFuture.getDelay");
        }

        @Override
        public int compareTo(Delayed other) {
            throw unsupported("ScheduledFuture.compareTo");
        }

        @Override
        public Object get() {
            throw unsupported("ScheduledFuture.get");
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            throw unsupported("ScheduledFuture.get");
        }
    }

    private static UnsupportedOperationException unsupported(String method) {
        return new UnsupportedOperationException(method + " is not supported: VertxScheduledExecutorService "
                + "only implements what DTE calls (schedule(Runnable), scheduleAtFixedRate, shutdown)");
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw unsupported("schedule(Callable)");
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        throw unsupported("scheduleWithFixedDelay");
    }

    @Override
    public List<Runnable> shutdownNow() {
        throw unsupported("shutdownNow");
    }

    @Override
    public boolean isTerminated() {
        throw unsupported("isTerminated");
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        throw unsupported("awaitTermination");
    }

    @Override
    public <T> java.util.concurrent.Future<T> submit(Callable<T> task) {
        throw unsupported("submit");
    }

    @Override
    public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) {
        throw unsupported("submit");
    }

    @Override
    public java.util.concurrent.Future<?> submit(Runnable task) {
        throw unsupported("submit");
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> callables) {
        throw unsupported("invokeAll");
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> callables,
                                                              long timeout, TimeUnit unit) {
        throw unsupported("invokeAll");
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> callables) {
        throw unsupported("invokeAny");
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> callables, long timeout, TimeUnit unit) {
        throw unsupported("invokeAny");
    }

    @Override
    public void execute(Runnable command) {
        throw unsupported("execute");
    }
}
