// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature.extractor;

import com.amazon.demanddriventrafficevaluator.modelfeature.Registry;

import java.util.Map;

public class ExtractorRegistry extends Registry<Extractor> {
    public ExtractorRegistry(Map<String, Extractor> records) {
        super(records);
    }

    @Override
    protected String getRegistryType() {
        return "Extractor";
    }
}
