// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Getter
public class BidRequestEvaluatorOutput {
    private final Response response;

    /**
     * Immutable {@link ModelEvaluation}s of the evaluated models, in evaluation order. Populated only
     * when the input sets {@code includeDetails(true)}; otherwise, and on the default response, an
     * empty list.
     */
    private final List<ModelEvaluation> modelEvaluations;

    // Package-private like the Lombok-generated constructor it replaces; copies the list, drops nulls.
    @Builder
    BidRequestEvaluatorOutput(Response response, List<ModelEvaluation> modelEvaluations) {
        this.response = response;
        this.modelEvaluations = modelEvaluations == null || modelEvaluations.isEmpty()
                ? List.of()
                : modelEvaluations.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableList());
    }
}
