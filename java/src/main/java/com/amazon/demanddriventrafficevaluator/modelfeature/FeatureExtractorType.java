// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FeatureExtractorType {
    JsonExtractor,
    ProtobufExtractor;

    @JsonCreator
    public static FeatureExtractorType fromString(String value) {
        return FeatureExtractorType.valueOf(value);
    }

    @JsonValue
    public String getValue() {
        return this.toString();
    }
}
