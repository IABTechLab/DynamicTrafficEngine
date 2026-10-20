// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Concurrent first-call tests for the lazy singletons (issue #16): every caller
 * racing on a fresh factory must receive the same instance.
 */
class LazySingletonConcurrencyTest {

    private static final int THREADS = 16;
    private static final int ROUNDS = 50;

    @Test
    void extractorRegistryFactory_concurrentFirstCalls_returnSameInstance() throws Exception {
        assertSameInstanceUnderRace(() -> newViaPrivateConstructor(ExtractorRegistryFactory.class),
                ExtractorRegistryFactory::getSingleton);
    }

    @Test
    void transformerRegistryFactory_concurrentFirstCalls_returnSameInstance() throws Exception {
        assertSameInstanceUnderRace(() -> newViaPrivateConstructor(TransformerRegistryFactory.class),
                TransformerRegistryFactory::getSingleton);
    }

    @Test
    void localCacheRegistryFactory_concurrentFirstCalls_returnSameInstance() throws Exception {
        assertSameInstanceUnderRace(DefaultLocalCacheRegistryFactory::new,
                LocalCacheRegistryFactory::getDefaultLocalCacheRegistrySingleton);
    }

    @Test
    void experimentManagerFactory_concurrentFirstCalls_returnSameExperimentManager() throws Exception {
        assertSameInstanceUnderRace(ExperimentManagerFactory::new,
                ExperimentManagerFactory::provideExperimentManager);
    }

    @Test
    void experimentManagerFactory_concurrentFirstCalls_returnSameConfigurationProvider() throws Exception {
        assertSameInstanceUnderRace(ExperimentManagerFactory::new,
                ExperimentManagerFactory::provideExperimentConfigurationProvider);
    }

    @Test
    void experimentManagerFactory_concurrentFirstCalls_returnSameTreatmentAllocator() throws Exception {
        assertSameInstanceUnderRace(ExperimentManagerFactory::new,
                ExperimentManagerFactory::provideTreatmentAllocator);
    }

    private static <F> F newViaPrivateConstructor(Class<F> type) {
        try {
            Constructor<F> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <F, T> void assertSameInstanceUnderRace(Supplier<F> freshFactory, Function<F, T> getter)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                F factory = freshFactory.get();
                CountDownLatch ready = new CountDownLatch(THREADS);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<T>> futures = new ArrayList<>();
                for (int i = 0; i < THREADS; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return getter.apply(factory);
                    }));
                }
                ready.await();
                start.countDown();
                T first = futures.get(0).get(10, TimeUnit.SECONDS);
                assertNotNull(first);
                for (Future<T> future : futures) {
                    assertSame(first, future.get(10, TimeUnit.SECONDS),
                            "concurrent first callers received different instances in round " + round);
                }
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
