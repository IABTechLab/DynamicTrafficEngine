// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents the aggregated result of multiple model evaluations within an experiment.
 * <p>
 * This class encapsulates various pieces of information about the experiment and its outcome,
 * including experiment metadata, treatment information, and aggregated scores.
 * </p>
 */
@Builder
@Getter
@ToString
public class AggregatedModelEvaluationResult {
    /**
     * The name of the experiment.
     */
    private final String experimentName;

    /**
     * The type of the experiment (e.g., "soft-filter").
     */
    private final String experimentType;

    /**
     * The treatment code as a string (e.g., "T", "C").
     */
    private final String treatmentCode;

    /**
     * The treatment code as an integer (e.g., "0", "1").
     */
    private final int treatmentCodeInInt;

    /**
     * The aggregated score from model evaluations.
     */
    private final double score;

    /**
     * The aggregated score after applying treatment effects.
     */
    private final double scoreWithTreatment;

    /**
     * The type of aggregation used (e.g., "max", "average").
     */
    private final String aggregationType;
}
