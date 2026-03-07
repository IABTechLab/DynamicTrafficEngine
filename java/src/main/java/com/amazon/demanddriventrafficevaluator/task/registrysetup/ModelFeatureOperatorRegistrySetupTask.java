// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task.registrysetup;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeatureOperator;
import com.amazon.demanddriventrafficevaluator.modelfeature.Registry;
import com.amazon.demanddriventrafficevaluator.task.OneShotTaskWithRandomizedStart;

import java.util.ServiceLoader;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * A one-shot task for setting up a registry of ModelFeatureOperators.
 * <p>
 * This class extends OneShotTaskWithRandomizedStart to provide a mechanism for
 * loading and registering ModelFeatureOperator implementations using Java's
 * ServiceLoader. It's designed to run once with a randomized initial delay.
 * </p>
 *
 * @param <T> The type of ModelFeatureOperator this task will register.
 */
public class ModelFeatureOperatorRegistrySetupTask<T extends ModelFeatureOperator> extends OneShotTaskWithRandomizedStart {

    private final Registry<T> registry;
    private final Class<T> type;

    public ModelFeatureOperatorRegistrySetupTask(
            String taskName,
            ScheduledThreadPoolExecutor executor,
            Registry<T> registry,
            Class<T> type
    ) {
        super(taskName, executor);
        this.registry = registry;
        this.type = type;
    }

    /**
     * Executes the task of loading and registering ModelFeatureOperator implementations.
     * <p>
     * This method uses Java's ServiceLoader to discover implementations of the
     * specified ModelFeatureOperator type. Each discovered implementation is
     * then registered in the provided Registry using its simple class name as the key.
     * </p>
     */
    @Override
    public void executeTask() {
        ServiceLoader<T> serviceLoader = ServiceLoader.load(type);
        for (T implementation : serviceLoader) {
            registry.register(implementation.getClass().getSimpleName(), (Class<? extends T>) implementation.getClass());
        }
    }

    /**
     * Initializes the task by scheduling it for a single execution with a randomized delay.
     * <p>
     * This method triggers the scheduling of the task, which will execute once
     * after a random initial delay.
     * </p>
     */
    @Override
    public void initialize() {
        schedule();
    }
}
