// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import com.amazon.demanddriventrafficevaluator.repository.entity.ModelDefinition;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ModelEvaluatorInput {
    private final ModelEvaluationContext context;
    private final ModelDefinition modelDefinition;
}
