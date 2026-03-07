// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.entity;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder
@Getter
@ToString
public class ModelResult {
    /**
     * The overall score of the model, based on the value of the first key in the cache,
     * or the default value of the model, if no hits are found.
     */
    private final double value;
    private final List<Double> values;
    private List<String> keys;
}
