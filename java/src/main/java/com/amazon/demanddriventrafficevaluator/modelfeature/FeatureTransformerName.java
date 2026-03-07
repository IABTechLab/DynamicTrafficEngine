// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature;

import com.amazon.demanddriventrafficevaluator.modelfeature.transformer.IncludeDefaultValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FeatureTransformerName {

    ApplyMappings,
    ConcatenateByPair,
    GetFirstNotEmpty,
    Exists,
    IncludeDefaultValue;

    @JsonCreator
    public static FeatureTransformerName fromString(String value) {
        return FeatureTransformerName.valueOf(value);
    }

    @JsonValue
    public String getValue() {
        return this.toString();
    }
}
