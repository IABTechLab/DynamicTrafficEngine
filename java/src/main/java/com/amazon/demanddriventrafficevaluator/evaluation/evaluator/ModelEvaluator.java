// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;

import java.util.List;

/**
 * An interface for evaluating bid requests using specific models.
 * <p>
 * This interface provides methods to extract model features from input data
 * and to evaluate OpenRTB requests using a defined model.
 * </p>
 */
public interface ModelEvaluator {

    /**
     * Extracts and transforms model features based on the provided input.
     * <p>
     * This method processes the ModelDefinition contained in the input to
     * generate a list of relevant features for the model.
     * </p>
     *
     * @param input The ModelEvaluatorInput containing necessary data for feature extraction.
     * @return A list of ModelFeature objects representing the extracted features.
     */
    List<ModelFeature> getFeatures(ModelEvaluatorInput input);

    /**
     * Evaluates an OpenRTB request using the specified model.
     * <p>
     * This method applies the model defined in the input to evaluate
     * the given OpenRTB request and produce an output.
     * </p>
     *
     * @param request The ModelEvaluatorInput containing the ModelEvaluationContext
     *                and ModelDefinition required for evaluation.
     * @return A ModelEvaluatorOutput object containing the results of the evaluation.
     */
    ModelEvaluatorOutput evaluate(ModelEvaluatorInput request);
}
