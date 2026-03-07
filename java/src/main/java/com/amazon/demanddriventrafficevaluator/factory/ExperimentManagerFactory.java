// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import com.amazon.demanddriventrafficevaluator.evaluation.experiment.ExperimentHandler;
import com.amazon.demanddriventrafficevaluator.evaluation.experiment.ExperimentManager;
import com.amazon.demanddriventrafficevaluator.evaluation.experiment.TreatmentAllocator;
import com.amazon.demanddriventrafficevaluator.evaluation.experiment.TreatmentAllocatorOnRandom;
import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.localcache.LocalCacheRegistry;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ConfigurationProvider;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ExperimentConfigurationProvider;

/**
 * A singleton factory class for creating and managing components related to experiment management.
 * <p>
 * This class provides centralized access to ExperimentManager, ExperimentConfigurationProvider,
 * and TreatmentAllocator instances. It uses lazy initialization and synchronization to ensure
 * thread-safe creation of these components.
 * </p>
 */
public class ExperimentManagerFactory {

    private static final ExperimentManagerFactory INSTANCE = new ExperimentManagerFactory();

    private ExperimentManager experimentManager;
    private ConfigurationProvider<ExperimentConfiguration> experimentConfigurationProvider;
    private TreatmentAllocator treatmentAllocator;

    public static ExperimentManagerFactory getInstance() {
        return INSTANCE;
    }

    public ExperimentManager provideExperimentManager() {
        if (experimentManager != null) {
            return experimentManager;
        }
        synchronized (this) {
            ConfigurationProvider<ExperimentConfiguration> provider = provideExperimentConfigurationProvider();
            TreatmentAllocator allocator = provideTreatmentAllocator();
            ExperimentHandler handler = new ExperimentHandler();
            experimentManager = new ExperimentManager(provider, allocator, handler);
            return experimentManager;
        }
    }

    public ConfigurationProvider<ExperimentConfiguration> provideExperimentConfigurationProvider() {
        if (experimentConfigurationProvider != null) {
            return experimentConfigurationProvider;
        }
        synchronized (this) {
            LocalCacheRegistry localCacheRegistry = DefaultLocalCacheRegistryFactory.getInstance().getDefaultLocalCacheRegistrySingleton();
            Dao<String, ExperimentConfiguration> cacheDao = new LocalCacheDao<String, ExperimentConfiguration>(localCacheRegistry);
            experimentConfigurationProvider = new ExperimentConfigurationProvider(cacheDao);
            return experimentConfigurationProvider;
        }
    }

    public TreatmentAllocator provideTreatmentAllocator() {
        if (treatmentAllocator != null) {
            return treatmentAllocator;
        }
        synchronized (this) {
            treatmentAllocator = new TreatmentAllocatorOnRandom();
            return treatmentAllocator;
        }
    }
}
