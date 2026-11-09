// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import com.amazon.demanddriventrafficevaluator.task.OneShotTaskWithRandomizedStart;
import com.amazon.demanddriventrafficevaluator.task.PeriodicTaskWithRandomizedStart;
import com.amazon.demanddriventrafficevaluator.task.TaskInitializer;
import com.amazon.demanddriventrafficevaluator.task.dataloading.BloomFilterPeriodicLoadingTask;
import com.amazon.demanddriventrafficevaluator.task.dataloading.ConfigurationPeriodicLoadingTask;
import com.amazon.demanddriventrafficevaluator.task.dataloading.ModelResultPeriodicLoadingTask;
import com.amazon.demanddriventrafficevaluator.task.registrysetup.ModelFeatureOperatorRegistrySetupTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DefaultBidRequestEvaluatorFactoryScheduledExecutorServiceTest {

    private static final List<Class<?>> EXPECTED_TASK_TYPES = List.of(
            ConfigurationPeriodicLoadingTask.class,
            ConfigurationPeriodicLoadingTask.class,
            ModelFeatureOperatorRegistrySetupTask.class,
            ModelFeatureOperatorRegistrySetupTask.class,
            ModelResultPeriodicLoadingTask.class,
            BloomFilterPeriodicLoadingTask.class
    );

    @Mock
    private AwsCredentialsProvider mockCredentialsProvider;

    @Test
    void testEveryTaskIsScheduledOnCallerScheduledExecutorService() throws Exception {
        // Arrange: a ScheduledExecutorService that is not a ScheduledThreadPoolExecutor
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        // Build the shared S3 client first: the AWS SDK creates its own ScheduledThreadPoolExecutor in build(),
        // which is not a DTE pool, and another test may have reset the singleton.
        AWSServiceClientFactory.getInstance().getS3Client(mockCredentialsProvider, "us-west-2");

        try (MockedConstruction<ScheduledThreadPoolExecutor> pools = mockConstruction(ScheduledThreadPoolExecutor.class)) {
            BidRequestEvaluatorFactory factory = BidRequestEvaluatorFactory.create(
                    "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket", scheduler);
            assertSame(scheduler, getExecutor(factory));
            assertSame(scheduler, getExecutor(((DefaultBidRequestEvaluatorFactory) factory).defaultTaskInitializerFactory));
            List<Object> tasks = getTasks(factory.getTaskInitializer());
            assertEquals(EXPECTED_TASK_TYPES, tasks.stream().map(Object::getClass).toList());

            // Act & Assert: each task schedules itself on the caller's scheduler
            for (Object task : tasks) {
                int before = mockingDetails(scheduler).getInvocations().size();
                schedule(task);
                assertEquals(before + 1, mockingDetails(scheduler).getInvocations().size(), task.getClass().getSimpleName());
                if (task instanceof PeriodicTaskWithRandomizedStart periodicTask) {
                    assertNull(periodicTask.getExecutor());
                }
            }
            verify(scheduler, org.mockito.Mockito.times(4))
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
            verify(scheduler, org.mockito.Mockito.times(2))
                    .schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));

            // Assert: DTE created no pool of its own
            assertTrue(pools.constructed().isEmpty());
        }
    }

    @Test
    void testEveryTaskIsScheduledOnCallerScheduledThreadPoolExecutor() throws Exception {
        // Arrange: the existing ScheduledThreadPoolExecutor path
        ScheduledThreadPoolExecutor pool = mock(ScheduledThreadPoolExecutor.class);
        BidRequestEvaluatorFactory factory = BidRequestEvaluatorFactory.create(
                "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket", pool);
        assertSame(pool, getExecutor(factory));
        assertSame(pool, getExecutor(((DefaultBidRequestEvaluatorFactory) factory).defaultTaskInitializerFactory));
        List<Object> tasks = getTasks(factory.getTaskInitializer());
        assertEquals(EXPECTED_TASK_TYPES, tasks.stream().map(Object::getClass).toList());

        // Act & Assert: each task schedules itself on the pool and the getter still returns it
        for (Object task : tasks) {
            int before = mockingDetails(pool).getInvocations().size();
            schedule(task);
            assertEquals(before + 1, mockingDetails(pool).getInvocations().size(), task.getClass().getSimpleName());
            if (task instanceof PeriodicTaskWithRandomizedStart periodicTask) {
                assertSame(pool, periodicTask.getExecutor());
            }
        }
        verify(pool, org.mockito.Mockito.times(4))
                .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(pool, org.mockito.Mockito.times(2))
                .schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));

        // Act: close never shuts down a caller-supplied pool
        factory.close();
        verify(pool, never()).shutdown();
        verify(pool, never()).shutdownNow();
    }

    @Test
    void testCloseNeverShutsDownCallerScheduledExecutorService() {
        // Arrange
        ScheduledExecutorService fromCreate = mock(ScheduledExecutorService.class);
        ScheduledExecutorService fromConstructor = mock(ScheduledExecutorService.class);
        BidRequestEvaluatorFactory created = BidRequestEvaluatorFactory.create(
                "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket", fromCreate);
        DefaultBidRequestEvaluatorFactory constructed = new DefaultBidRequestEvaluatorFactory(
                "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket", fromConstructor);

        // Act
        created.close();
        created.close();
        constructed.close();

        // Assert: nothing was scheduled, so close() must not have touched either executor at all
        verifyNoInteractions(fromCreate, fromConstructor);
    }

    @Test
    void testScheduledThreadPoolExecutorSignaturesAreKept() throws Exception {
        Class<?>[] factoryArgs = {String.class, AwsCredentialsProvider.class, String.class, String.class, ScheduledThreadPoolExecutor.class};
        assertEquals(BidRequestEvaluatorFactory.class,
                BidRequestEvaluatorFactory.class.getMethod("create", factoryArgs).getReturnType());
        DefaultBidRequestEvaluatorFactory.class.getConstructor(factoryArgs);
        DefaultTaskInitializerFactory.class.getConstructor(factoryArgs);
        assertEquals(ScheduledThreadPoolExecutor.class,
                PeriodicTaskWithRandomizedStart.class.getMethod("getExecutor").getReturnType());
        PeriodicTaskWithRandomizedStart.class.getDeclaredConstructor(
                String.class, String.class, long.class, ScheduledThreadPoolExecutor.class);
        OneShotTaskWithRandomizedStart.class.getDeclaredConstructor(String.class, ScheduledThreadPoolExecutor.class);
        assertEquals(ScheduledThreadPoolExecutor.class,
                OneShotTaskWithRandomizedStart.class.getDeclaredField("executor").getType());
        for (Class<?> type : List.of(ConfigurationPeriodicLoadingTask.class, ModelResultPeriodicLoadingTask.class,
                BloomFilterPeriodicLoadingTask.class, ModelFeatureOperatorRegistrySetupTask.class)) {
            assertEquals(2, java.util.Arrays.stream(type.getConstructors())
                    .filter(c -> List.of(c.getParameterTypes()).contains(ScheduledThreadPoolExecutor.class)
                            || List.of(c.getParameterTypes()).contains(ScheduledExecutorService.class))
                    .count(), type.getSimpleName());
            assertTrue(java.util.Arrays.stream(type.getConstructors())
                    .anyMatch(c -> List.of(c.getParameterTypes()).contains(ScheduledThreadPoolExecutor.class)), type.getSimpleName());
        }
    }

    private static void schedule(Object task) {
        if (task instanceof PeriodicTaskWithRandomizedStart periodicTask) {
            periodicTask.schedulePeriodically();
        } else {
            // The registry setup tasks only schedule themselves in initialize()
            ((OneShotTaskWithRandomizedStart) task).initialize();
        }
    }

    // Helper method to unwrap the scheduled tasks of both stages using reflection
    private static List<Object> getTasks(TaskInitializer taskInitializer) throws Exception {
        List<Object> tasks = new ArrayList<>();
        for (String stage : List.of("stageOneTasks", "stageTwoTasks")) {
            java.lang.reflect.Field stageField = TaskInitializer.class.getDeclaredField(stage);
            stageField.setAccessible(true);
            for (Object initializerTask : (List<?>) stageField.get(taskInitializer)) {
                // each task is a RunUntilSucceeded around the initializer task around the scheduled task
                assertTrue(initializerTask instanceof RunUntilSucceeded);
                tasks.add(getField(getField(initializerTask, "task"), "task"));
            }
        }
        return tasks;
    }

    private static Object getField(Object target, String name) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    // Helper method to read the private executor field using reflection
    private static Object getExecutor(Object target) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField("executor");
        field.setAccessible(true);
        return field.get(target);
    }
}
