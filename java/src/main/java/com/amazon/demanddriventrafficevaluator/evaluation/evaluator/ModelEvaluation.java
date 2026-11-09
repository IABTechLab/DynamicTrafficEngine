// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import com.amazon.demanddriventrafficevaluator.repository.entity.ModelDefinition;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelResult;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelValueType;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable copy of one model's evaluation result for a bid request.
 * Returned by {@link BidRequestEvaluatorOutput#getModelEvaluations()} when the input sets
 * {@code includeDetails(true)}.
 */
@Getter
@ToString
@EqualsAndHashCode
public final class ModelEvaluation {

    private final String identifier;
    private final String name;
    private final String version;
    private final ModelValueType type;
    private final ModelEvaluationStatus status;
    /** The model's score; null when the model produced no result. */
    private final Double value;
    @ToString.Exclude
    private final List<Double> values;
    @ToString.Exclude
    private final List<String> keys;

    @Builder
    private ModelEvaluation(
            String identifier,
            String name,
            String version,
            ModelValueType type,
            ModelEvaluationStatus status,
            Double value,
            List<Double> values,
            List<String> keys
    ) {
        this.identifier = identifier;
        this.name = name;
        this.version = version;
        this.type = type;
        this.status = status;
        this.value = value;
        this.values = copyOf(values);
        this.keys = copyOf(keys);
    }

    static ModelEvaluation from(ModelEvaluatorOutput output) {
        ModelEvaluationBuilder builder = ModelEvaluation.builder().status(output.getStatus());

        ModelDefinition definition = output.getModelDefinition();
        if (definition != null) {
            builder.identifier(definition.getIdentifier())
                    .name(definition.getName())
                    .version(definition.getVersion())
                    .type(definition.getType());
        }

        ModelResult result = output.getModelResult();
        if (result != null) {
            builder.value(result.getValue())
                    .values(result.getValues())
                    .keys(result.getKeys());
        }
        return builder.build();
    }

    // Null-tolerant, unlike List.copyOf.
    private static <T> List<T> copyOf(List<T> source) {
        return source == null || source.isEmpty()
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(source));
    }
}
