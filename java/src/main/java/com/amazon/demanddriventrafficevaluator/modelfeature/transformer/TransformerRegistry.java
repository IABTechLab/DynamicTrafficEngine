// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature.transformer;

import com.amazon.demanddriventrafficevaluator.modelfeature.Registry;

import java.util.Map;

public class TransformerRegistry extends Registry<Transformer> {
    public TransformerRegistry(Map<String, Transformer> records) {
        super(records);
    }

    @Override
    protected String getRegistryType() {
        return "Transformer";
    }
}
