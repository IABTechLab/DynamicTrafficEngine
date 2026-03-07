// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature;

import com.amazon.demanddriventrafficevaluator.repository.entity.FeatureConfiguration;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Builder
@Getter
@ToString
public class ModelFeature {
    private final FeatureConfiguration configuration;
    private final List<String> values;
}


