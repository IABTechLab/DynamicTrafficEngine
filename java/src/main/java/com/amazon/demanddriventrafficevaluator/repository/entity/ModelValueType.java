// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ModelValueType {
    HighValue("HighValue", 1.0, 0.0),  // no match, tuple is low value
    LowValue("LowValue", 0.0, 1.0);  // no match, tuple is high value

    private final String type;
    private final double cacheValue;
    private final double defaultValue;

    ModelValueType(String type, double cacheValue, double defaultValue) {
        this.type = type;
        this.cacheValue = cacheValue;
        this.defaultValue = defaultValue;
    }

    @JsonCreator
    public static ModelValueType fromString(String value) {
        return ModelValueType.valueOf(value);
    }

    @JsonValue
    public String getValue() {
        return this.toString();
    }
}
