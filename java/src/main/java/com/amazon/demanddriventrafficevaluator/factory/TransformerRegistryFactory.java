// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import com.amazon.demanddriventrafficevaluator.modelfeature.transformer.TransformerRegistry;

import java.util.HashMap;

/**
 * A singleton factory class for creating and managing the TransformerRegistry.
 * <p>
 * This class provides centralized access to a single instance of TransformerRegistry.
 * It uses lazy initialization and synchronization to ensure thread-safe creation
 * of the TransformerRegistry instance.
 * </p>
 */
public class TransformerRegistryFactory {

    private static final TransformerRegistryFactory INSTANCE = new TransformerRegistryFactory();
    private TransformerRegistry transformerRegistry;

    private TransformerRegistryFactory() {
    }

    public static TransformerRegistryFactory getInstance() {
        return INSTANCE;
    }

    public TransformerRegistry getSingleton() {
        if (transformerRegistry != null) {
            return transformerRegistry;
        }
        synchronized (this) {
            transformerRegistry = new TransformerRegistry(new HashMap<>());
            return transformerRegistry;
        }
    }
}
