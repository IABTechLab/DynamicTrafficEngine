// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

/**
 * Interface for aggregating model evaluation results.
 * <p>
 * Implementations of this interface are responsible for combining the results
 * of multiple model evaluations into a single, aggregated result. This is typically
 * used in the context of experiments where multiple models or treatments are being
 * compared.
 * </p>
 */
public interface ModelEvaluationResultsAggregator {

    /**
     * Aggregates the results of multiple model evaluations.
     * <p>
     * This method takes an evaluation context, which contains information about
     * the experiment setup and individual model evaluation results, and produces
     * a single aggregated result.
     * </p>
     *
     * @param context The EvaluationContext containing experiment details, model
     *                outputs, and other relevant information for aggregation.
     * @return An AggregatedModelEvaluationResult representing the combined outcome
     * of all model evaluations. This typically includes an overall score,
     * experiment metadata, and any other relevant aggregated metrics.
     */
    AggregatedModelEvaluationResult aggregate(EvaluationContext context);
}
