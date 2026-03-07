// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import com.amazon.demanddriventrafficevaluator.modelfeature.extractor.ExtractorRegistry;

import java.util.HashMap;

/**
 * A singleton factory class for creating and managing the ExtractorRegistry.
 * <p>
 * This class provides centralized access to a single instance of ExtractorRegistry.
 * It uses lazy initialization and synchronization to ensure thread-safe creation
 * of the ExtractorRegistry instance.
 * </p>
 */
public class ExtractorRegistryFactory {

    private static final ExtractorRegistryFactory INSTANCE = new ExtractorRegistryFactory();
    private ExtractorRegistry extractorRegistry;

    private ExtractorRegistryFactory() {
    }

    public static ExtractorRegistryFactory getInstance() {
        return INSTANCE;
    }

    public ExtractorRegistry getSingleton() {
        if (extractorRegistry != null) {
            return extractorRegistry;
        }
        synchronized (this) {
            extractorRegistry = new ExtractorRegistry(new HashMap<>());
            return extractorRegistry;
        }
    }
}
