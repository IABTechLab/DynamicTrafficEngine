// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelDefinition;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelResult;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Builder
@Getter
@ToString
public class ModelEvaluatorOutput {
    private final ModelEvaluationContext context;
    private final ModelEvaluationStatus status;
    private final ModelResult modelResult;
    private final List<ModelFeature> modelFeatures;
    private final ModelDefinition modelDefinition;
}
