// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledExecutorServiceTaskTest {

    @Test
    void testTasksRunOnScheduledExecutorServiceDecorator() throws Exception {
        // Arrange: a decorator that hides the ScheduledThreadPoolExecutor behind it
        ScheduledExecutorService scheduler = Executors.unconfigurableScheduledExecutorService(
                Executors.newSingleThreadScheduledExecutor());
        assertFalse(scheduler instanceof ScheduledThreadPoolExecutor);
        CountDownLatch periodicRan = new CountDownLatch(2);
        CountDownLatch oneShotRan = new CountDownLatch(1);
        PeriodicTaskWithRandomizedStart periodicTask = new PeriodicTaskWithRandomizedStart("ssp", "Periodic", 1000, scheduler) {
            @Override
            public void executeTask() {
                periodicRan.countDown();
            }

            @Override
            public void initialize() {
            }
        };
        OneShotTaskWithRandomizedStart oneShotTask = new OneShotTaskWithRandomizedStart("OneShot", scheduler) {
            @Override
            public void executeTask() {
                oneShotRan.countDown();
            }

            @Override
            public void initialize() {
                schedule();
            }
        };

        try {
            // Act
            periodicTask.schedulePeriodically(0);
            oneShotTask.initialize();

            // Assert
            assertTrue(periodicRan.await(5, TimeUnit.SECONDS));
            assertTrue(oneShotRan.await(5, TimeUnit.SECONDS));
            assertNull(periodicTask.getExecutor());
            assertNull(oneShotTask.executor);
            assertSame(scheduler, periodicTask.getScheduledExecutorService());
            assertSame(scheduler, oneShotTask.getScheduledExecutorService());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void testARunFailingAfterShutdownIsNotLoggedAsAnError() throws Exception {
        // Arrange: capture the task classes' log events at every level
        List<LogEvent> events = new CopyOnWriteArrayList<>();
        AbstractAppender appender = new AbstractAppender("capture-" + System.nanoTime(), null, null, true,
                Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                events.add(event.toImmutable());
            }
        };
        appender.start();
        LoggerContext context = (LoggerContext) LogManager.getContext(PeriodicTaskWithRandomizedStart.class.getClassLoader(), false);
        List<String> names = List.of(PeriodicTaskWithRandomizedStart.class.getName(),
                OneShotTaskWithRandomizedStart.class.getName());
        for (String name : names) {
            LoggerConfig loggerConfig = new LoggerConfig(name, Level.DEBUG, false);
            loggerConfig.addAppender(appender, null, null);
            context.getConfiguration().addLogger(name, loggerConfig);
        }
        context.updateLoggers();
        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(2);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try {
            PeriodicTaskWithRandomizedStart periodicTask = new PeriodicTaskWithRandomizedStart("ssp", "Periodic", 1000, pool) {
                @Override
                public void executeTask() {
                    failAfter(started, release);
                }

                @Override
                public void initialize() {
                }
            };
            OneShotTaskWithRandomizedStart oneShotTask = new OneShotTaskWithRandomizedStart("OneShot", pool) {
                @Override
                public void executeTask() {
                    failAfter(started, release);
                }

                @Override
                public void initialize() {
                    schedule();
                }
            };
            periodicTask.schedulePeriodically(0);
            oneShotTask.initialize();
            assertTrue(started.await(5, TimeUnit.SECONDS));

            // Act: shut the executor down while both runs are in flight, then let them fail
            pool.shutdown();
            release.countDown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

            // Assert: both failures are logged, below ERROR
            assertEquals(List.of(Level.DEBUG, Level.DEBUG), events.stream().filter(e -> e.getThrown() != null)
                    .map(LogEvent::getLevel).toList(), events.stream().map(e -> e.getLoggerName() + " " + e.getLevel()
                    + " " + e.getMessage().getFormattedMessage() + " " + e.getThrown()).toList().toString());
        } finally {
            names.forEach(context.getConfiguration()::removeLogger);
            context.updateLoggers();
            appender.stop();
            pool.shutdownNow();
        }
    }

    @Test
    void testARunSkippedAfterCancelIsNotAFirstRun() {
        // Arrange: an executor that only captures the periodic command, so the test runs it after cancel()
        ScheduledExecutorService captor = org.mockito.Mockito.mock(ScheduledExecutorService.class);
        java.util.concurrent.atomic.AtomicInteger executions = new java.util.concurrent.atomic.AtomicInteger();
        PeriodicTaskWithRandomizedStart task = new PeriodicTaskWithRandomizedStart("ssp", "Periodic", 1000, captor) {
            @Override
            public void executeTask() {
                executions.incrementAndGet();
            }

            @Override
            public void initialize() {
            }
        };
        task.schedulePeriodically(0);
        org.mockito.ArgumentCaptor<Runnable> command = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        org.mockito.Mockito.verify(captor).scheduleAtFixedRate(command.capture(), org.mockito.ArgumentMatchers.eq(0L),
                org.mockito.ArgumentMatchers.eq(1000L), org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS));

        // Act: the run the executor had already started when cancel() came in
        task.cancel();
        command.getValue().run();

        // Assert
        assertEquals(0, executions.get());
        assertFalse(task.hasFinishedFirstRun(), "a skipped run is not a first run");
    }

    @Test
    void testCancelStopsThePeriodicScheduleAndIsSafeBeforeScheduling() throws Exception {
        // Arrange: a pool that drops a cancelled task from its queue at once, so the queue shows what is scheduled
        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1);
        pool.setRemoveOnCancelPolicy(true);
        try {
            CountDownLatch ran = new CountDownLatch(1);
            PeriodicTaskWithRandomizedStart scheduled = periodic(pool, ran);
            PeriodicTaskWithRandomizedStart cancelledFirst = periodic(pool, new CountDownLatch(1));
            scheduled.schedulePeriodically(0);
            assertTrue(ran.await(5, TimeUnit.SECONDS));
            // the pool queues the next run only once the first has returned
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (pool.getQueue().size() != 1 && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertEquals(1, pool.getQueue().size(), "the next periodic run is queued");

            // Act
            scheduled.cancel();
            cancelledFirst.cancel();
            cancelledFirst.schedulePeriodically(0);

            // Assert: neither task has a run left in the queue
            assertEquals(0, pool.getQueue().size());
        } finally {
            pool.shutdownNow();
        }
    }

    private static PeriodicTaskWithRandomizedStart periodic(ScheduledExecutorService executor, CountDownLatch ran) {
        return new PeriodicTaskWithRandomizedStart("ssp", "Periodic", 1000, executor) {
            @Override
            public void executeTask() {
                ran.countDown();
            }

            @Override
            public void initialize() {
            }
        };
    }

    private static void failAfter(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        throw new IllegalStateException("run failed after shutdown");
    }

    @Test
    void testGettersReturnScheduledThreadPoolExecutor() {
        // Arrange: a ScheduledThreadPoolExecutor passed through the ScheduledExecutorService overload
        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1);
        try {
            ScheduledExecutorService scheduler = pool;

            // Act
            PeriodicTaskWithRandomizedStart periodicTask = new PeriodicTaskWithRandomizedStart("ssp", "Periodic", 1000, scheduler) {
                @Override
                public void executeTask() {
                }

                @Override
                public void initialize() {
                }
            };
            OneShotTaskWithRandomizedStart oneShotTask = new OneShotTaskWithRandomizedStart("OneShot", scheduler) {
                @Override
                public void executeTask() {
                }

                @Override
                public void initialize() {
                }
            };

            // Assert
            assertSame(pool, periodicTask.getExecutor());
            assertSame(pool, oneShotTask.executor);
            assertSame(pool, periodicTask.getScheduledExecutorService());
            assertSame(pool, oneShotTask.getScheduledExecutorService());
        } finally {
            pool.shutdownNow();
        }
    }
}
