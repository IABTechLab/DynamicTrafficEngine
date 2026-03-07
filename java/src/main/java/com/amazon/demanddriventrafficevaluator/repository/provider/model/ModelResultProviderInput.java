// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.provider.model;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelDefinition;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class ModelResultProviderInput {
    private final ModelDefinition modelDefinition;
    private final List<ModelFeature> modelFeatures;
}
