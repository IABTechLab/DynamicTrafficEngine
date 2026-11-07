// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluator;
import com.amazon.demanddriventrafficevaluator.task.TaskInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DefaultBidRequestEvaluatorFactoryCloseTest {

    @Mock
    private AwsCredentialsProvider mockCredentialsProvider;

    private ScheduledThreadPoolExecutor pool;

    @AfterEach
    void tearDown() {
        // Stop any pool a failed test left running, so its threads do not leak into other tests
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    @Test
    void testCloseShutsDownOwnedExecutor() throws Exception {
        // Arrange
        DefaultBidRequestEvaluatorFactory factory = new DefaultBidRequestEvaluatorFactory(
                "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket");
        pool = getExecutor(factory);
        CountDownLatch ran = new CountDownLatch(1);
        pool.scheduleAtFixedRate(ran::countDown, 0, 1, TimeUnit.SECONDS);
        assertTrue(ran.await(5, TimeUnit.SECONDS));
        assertFalse(pool.isShutdown());

        // Act
        factory.close();

        // Assert
        assertTrue(pool.isShutdown());
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void testCloseLetsRunningTaskFinish() throws Exception {
        // Arrange
        DefaultBidRequestEvaluatorFactory factory = new DefaultBidRequestEvaluatorFactory(
                "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket");
        pool = getExecutor(factory);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        pool.execute(() -> {
            started.countDown();
            try {
                release.await();
                finished.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));

            // Act
            factory.close();
        } finally {
            release.countDown();
        }

        // Assert: the running task was not interrupted and ran to completion
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void testCloseRunsQueuedOneShotTask() throws Exception {
        // Arrange: a delayed one-shot task, like the registry setup tasks
        DefaultBidRequestEvaluatorFactory factory = new DefaultBidRequestEvaluatorFactory(
                "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket");
        pool = getExecutor(factory);
        CountDownLatch ran = new CountDownLatch(1);
        pool.schedule(ran::countDown, 300, TimeUnit.MILLISECONDS);

        // Act
        factory.close();

        // Assert
        assertTrue(ran.await(5, TimeUnit.SECONDS));
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void testCloseLeavesCallerExecutorRunning() throws Exception {
        // Arrange
        pool = new ScheduledThreadPoolExecutor(1);
        BidRequestEvaluatorFactory factory = BidRequestEvaluatorFactory.create(
                "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket", pool);
        assertSame(pool, getExecutor(factory));

        // Act
        factory.close();
        factory.close();

        // Assert
        assertFalse(pool.isShutdown());
        CountDownLatch ran = new CountDownLatch(1);
        pool.execute(ran::countDown);
        assertTrue(ran.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testCloseIsIdempotent() throws Exception {
        // Arrange
        DefaultBidRequestEvaluatorFactory factory = new DefaultBidRequestEvaluatorFactory(
                "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket");
        pool = getExecutor(factory);

        // Act & Assert: close twice, with no init() in between
        assertDoesNotThrow(factory::close);
        assertDoesNotThrow(factory::close);
        assertTrue(pool.isShutdown());
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void testTryWithResourcesClosesFactoryFromCreateFourParameters() throws Exception {
        // Act
        try (BidRequestEvaluatorFactory factory = BidRequestEvaluatorFactory.create(
                "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket")) {
            pool = getExecutor(factory);
            assertFalse(pool.isShutdown());
        }

        // Assert
        assertTrue(pool.isShutdown());
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void testOwnedExecutorsUseNamedDaemonThreads() throws Exception {
        // Arrange: both default factories, each creating its own pool
        DefaultBidRequestEvaluatorFactory factory = new DefaultBidRequestEvaluatorFactory(
                "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket");
        DefaultTaskInitializerFactory taskInitializerFactory = new DefaultTaskInitializerFactory(
                "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket");
        java.lang.reflect.Field field = DefaultTaskInitializerFactory.class.getDeclaredField("executor");
        field.setAccessible(true);
        ScheduledThreadPoolExecutor other = (ScheduledThreadPoolExecutor) field.get(taskInitializerFactory);
        pool = getExecutor(factory);
        try {
            for (ScheduledThreadPoolExecutor executor : java.util.List.of(pool, other)) {
                // Act
                java.util.concurrent.atomic.AtomicReference<Thread> ran = new java.util.concurrent.atomic.AtomicReference<>();
                CountDownLatch done = new CountDownLatch(1);
                executor.execute(() -> {
                    ran.set(Thread.currentThread());
                    done.countDown();
                });
                assertTrue(done.await(5, TimeUnit.SECONDS));

                // Assert: the JVM can exit without close()
                assertTrue(ran.get().isDaemon(), ran.get().getName());
                assertTrue(ran.get().getName().startsWith("dte-scheduler-"), ran.get().getName());
            }
        } finally {
            other.shutdownNow();
        }
    }

    @Test
    void testCallerExecutorThreadsAreUntouched() throws Exception {
        pool = new ScheduledThreadPoolExecutor(1);
        BidRequestEvaluatorFactory factory = BidRequestEvaluatorFactory.create(
                "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket", pool);
        java.util.concurrent.atomic.AtomicReference<Thread> ran = new java.util.concurrent.atomic.AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        getExecutor(factory).execute(() -> {
            ran.set(Thread.currentThread());
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertFalse(ran.get().isDaemon());
        assertFalse(ran.get().getName().startsWith("dte-scheduler-"));
    }

    @Test
    void testBaseClassCloseIsNoOp() {
        BidRequestEvaluatorFactory factory = new BidRequestEvaluatorFactory() {
            @Override
            public TaskInitializer getTaskInitializer() {
                return null;
            }

            @Override
            public BidRequestEvaluator getEvaluator() {
                return null;
            }
        };
        assertDoesNotThrow(factory::close);
        assertDoesNotThrow(factory::close);
    }

    // Helper method to read the private executor field using reflection
    private ScheduledThreadPoolExecutor getExecutor(Object target) throws Exception {
        java.lang.reflect.Field field = DefaultBidRequestEvaluatorFactory.class.getDeclaredField("executor");
        field.setAccessible(true);
        return (ScheduledThreadPoolExecutor) field.get(target);
    }
}
