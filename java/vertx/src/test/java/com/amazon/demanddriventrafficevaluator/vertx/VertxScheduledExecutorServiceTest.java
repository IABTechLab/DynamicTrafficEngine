// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.vertx;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertxScheduledExecutorServiceTest {

    private final Vertx vertx = Vertx.vertx();
    private final TimerTracker timers = new TimerTracker(vertx);
    private final VertxScheduledExecutorService executor =
            new VertxScheduledExecutorService(timers.vertx(), "ses-test", 2, 30, TimeUnit.SECONDS);

    @AfterEach
    void tearDown() throws Exception {
        executor.shutdown();
        vertx.close().toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    @Test
    void scheduleRunsOnceOnTheWorker() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        AtomicInteger offWorker = new AtomicInteger();

        ScheduledFuture<?> future = executor.schedule(() -> {
            if (!Context.isOnWorkerThread() || !Thread.currentThread().getName().startsWith("ses-test-")) {
                offWorker.incrementAndGet();
            }
            ran.countDown();
        }, 0, TimeUnit.MILLISECONDS);

        assertTrue(ran.await(5, TimeUnit.SECONDS));
        assertEquals(0, offWorker.get());
        assertTrue(future.isDone());
        assertFalse(future.cancel(false), "a one-shot task that already ran cannot be cancelled");
    }

    @Test
    void fixedRateRunsNeverOverlap() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        AtomicInteger runs = new AtomicInteger();

        // Act: each run takes 20 times the period
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            runs.incrementAndGet();
            sleep(200);
            inFlight.decrementAndGet();
        }, 1, 10, TimeUnit.MILLISECONDS);
        Thread.sleep(1000);
        assertTrue(future.cancel(false));
        assertEquals(Set.of(), timers.liveTimers());
        int runsAtCancel = runs.get();
        Thread.sleep(500);

        // Assert
        assertEquals(1, maxInFlight.get(), "runs of one task never overlap");
        assertTrue(runsAtCancel >= 2 && runsAtCancel <= 6, "runs: " + runsAtCancel);
        assertEquals(runsAtCancel, runs.get(), "no run starts after cancel");
        assertTrue(future.isCancelled() && future.isDone());
    }

    @Test
    void shutdownCancelsTimersAndRejectsNewTasks() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        Runnable run = runs::incrementAndGet;
        executor.scheduleAtFixedRate(run, 200, 200, TimeUnit.MILLISECONDS);
        executor.schedule(run, 200, TimeUnit.MILLISECONDS);

        executor.shutdownAndClose().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        Thread.sleep(600);

        assertEquals(0, runs.get());
        assertEquals(Set.of(), timers.liveTimers(), "every timer is cancelled");
        assertTrue(executor.isShutdown());
        assertThrows(RejectedExecutionException.class, () -> executor.schedule(() -> { }, 1, TimeUnit.MILLISECONDS));
        executor.shutdown();
    }

    @Test
    void shutdownLeavesACallerWorkerOpen() throws Exception {
        WorkerExecutor worker = vertx.createSharedWorkerExecutor("caller-worker", 1, 30, TimeUnit.SECONDS);
        VertxScheduledExecutorService adapter = new VertxScheduledExecutorService(vertx, worker);

        adapter.shutdown();

        assertEquals("open", worker.executeBlocking(() -> "open").toCompletionStage().toCompletableFuture()
                .get(5, TimeUnit.SECONDS));
        worker.close();
    }

    @Test
    void aQueuedRunIsDroppedWhenItsTaskIsCancelled() throws Exception {
        // Arrange: a one-thread caller worker held busy, so the next run waits in its queue
        WorkerExecutor worker = vertx.createSharedWorkerExecutor("queued-cancel", 1, 30, TimeUnit.SECONDS);
        VertxScheduledExecutorService adapter = new VertxScheduledExecutorService(timers.vertx(), worker);
        CountDownLatch release = holdBusy(worker);
        AtomicInteger runs = new AtomicInteger();
        ScheduledFuture<?> future = adapter.scheduleAtFixedRate(runs::incrementAndGet, 1, 60_000, TimeUnit.MILLISECONDS);
        Thread.sleep(300);

        // Act: cancel while the first run is queued, then free the worker
        assertTrue(future.cancel(false));
        release.countDown();
        Thread.sleep(300);

        // Assert
        assertEquals(0, runs.get(), "a run still queued when its task is cancelled does not start");
        adapter.shutdown();
        worker.close();
    }

    @Test
    void aQueuedOneShotRunIsDroppedWhenItIsCancelled() throws Exception {
        // Arrange: the one-shot timer fires while the worker is busy, so its run waits in the queue
        WorkerExecutor worker = vertx.createSharedWorkerExecutor("queued-cancel-once", 1, 30, TimeUnit.SECONDS);
        VertxScheduledExecutorService adapter = new VertxScheduledExecutorService(timers.vertx(), worker);
        CountDownLatch release = holdBusy(worker);
        AtomicInteger runs = new AtomicInteger();
        Runnable run = runs::incrementAndGet;
        ScheduledFuture<?> future = adapter.schedule(run, 1, TimeUnit.MILLISECONDS);
        Thread.sleep(300);
        assertFalse(future.isDone(), "a queued run has not started");

        // Act
        assertTrue(future.cancel(false));
        release.countDown();
        Thread.sleep(300);

        // Assert
        assertEquals(0, runs.get(), "a queued one-shot run does not start after cancel");
        assertTrue(future.isCancelled() && future.isDone());
        adapter.shutdown();
        worker.close();
    }

    @Test
    void closeShutsDown() {
        executor.close();
        assertTrue(executor.isShutdown());
        assertThrows(RejectedExecutionException.class, () -> executor.schedule(() -> { }, 1, TimeUnit.MILLISECONDS));
    }

    @Test
    void aQueuedRunIsDroppedWhenTheAdapterShutsDown() throws Exception {
        // Arrange: as above, with a one-shot task
        WorkerExecutor worker = vertx.createSharedWorkerExecutor("queued-shutdown", 1, 30, TimeUnit.SECONDS);
        VertxScheduledExecutorService adapter = new VertxScheduledExecutorService(timers.vertx(), worker);
        CountDownLatch release = holdBusy(worker);
        AtomicInteger runs = new AtomicInteger();
        Runnable run = runs::incrementAndGet;
        adapter.schedule(run, 1, TimeUnit.MILLISECONDS);
        Thread.sleep(300);

        // Act: shut down while the run is queued; the caller's worker stays open
        adapter.shutdown();
        release.countDown();
        Thread.sleep(300);

        // Assert
        assertEquals(0, runs.get(), "a run still queued at shutdown does not start");
        worker.close();
    }

    @Test
    void aRunFailingAfterShutdownIsNotLoggedAsAnError() throws Exception {
        // Arrange: capture this class's log events at every level
        Logger logger = (Logger) LogManager.getLogger(VertxScheduledExecutorService.class);
        Level levelBefore = logger.getLevel();
        List<LogEvent> events = new CopyOnWriteArrayList<>();
        AbstractAppender appender = new AbstractAppender("capture-" + System.nanoTime(), null, null, true,
                Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                events.add(event.toImmutable());
            }
        };
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        WorkerExecutor worker = vertx.createSharedWorkerExecutor("race-shutdown", 1, 30, TimeUnit.SECONDS);
        VertxScheduledExecutorService adapter = new VertxScheduledExecutorService(timers.vertx(), worker);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Runnable failing = () -> {
                started.countDown();
                awaitQuietly(release);
                throw new IllegalStateException("run failed after shutdown");
            };
            adapter.schedule(failing, 1, TimeUnit.MILLISECONDS);
            assertTrue(started.await(5, TimeUnit.SECONDS));

            // Act: shut down while the run is in flight, then let it fail
            adapter.shutdown();
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (events.stream().noneMatch(e -> e.getThrown() != null) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }

            // Assert: the failure is logged, below ERROR
            List<Level> levels = events.stream().filter(e -> e.getThrown() != null).map(LogEvent::getLevel).toList();
            assertEquals(List.of(Level.DEBUG), levels);
        } finally {
            logger.removeAppender(appender);
            logger.setLevel(levelBefore);
            appender.stop();
            worker.close();
        }
    }

    @Test
    void methodsDteDoesNotCallThrow() {
        Runnable noop = () -> { };
        ScheduledFuture<?> future = executor.schedule(noop, 1, TimeUnit.HOURS);
        List<Map.Entry<String, Executable>> calls = List.of(
                Map.entry("schedule(Callable)", () -> executor.schedule(() -> 1, 1, TimeUnit.SECONDS)),
                Map.entry("scheduleWithFixedDelay", () -> executor.scheduleWithFixedDelay(noop, 1, 1, TimeUnit.SECONDS)),
                Map.entry("execute", () -> executor.execute(noop)),
                Map.entry("submit", () -> executor.submit(noop)),
                Map.entry("submit", () -> executor.submit(noop, 1)),
                Map.entry("submit", () -> executor.submit(() -> 1)),
                Map.entry("invokeAll", () -> executor.invokeAll(List.of(() -> 1))),
                Map.entry("invokeAll", () -> executor.invokeAll(List.of(() -> 1), 1, TimeUnit.SECONDS)),
                Map.entry("invokeAny", () -> executor.invokeAny(List.of(() -> 1))),
                Map.entry("invokeAny", () -> executor.invokeAny(List.of(() -> 1), 1, TimeUnit.SECONDS)),
                Map.entry("shutdownNow", executor::shutdownNow),
                Map.entry("isTerminated", executor::isTerminated),
                Map.entry("awaitTermination", () -> executor.awaitTermination(1, TimeUnit.SECONDS)),
                Map.entry("ScheduledFuture.get", future::get),
                Map.entry("ScheduledFuture.getDelay", () -> future.getDelay(TimeUnit.SECONDS)),
                Map.entry("ScheduledFuture.compareTo", () -> future.compareTo(future)));
        for (Map.Entry<String, Executable> call : calls) {
            UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, call.getValue(), call.getKey());
            assertTrue(e.getMessage().startsWith(call.getKey()), e.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> executor.scheduleAtFixedRate(noop, 1, 0, TimeUnit.SECONDS));
    }

    // Occupies the worker's only thread until the returned latch is counted down.
    private static CountDownLatch holdBusy(WorkerExecutor worker) throws InterruptedException {
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        worker.executeBlocking(() -> {
            busy.countDown();
            awaitQuietly(release);
            return null;
        }, false);
        assertTrue(busy.await(5, TimeUnit.SECONDS));
        return release;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
