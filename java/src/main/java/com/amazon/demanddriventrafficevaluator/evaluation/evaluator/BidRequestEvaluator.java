// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

/**
 * An interface for evaluating bid requests using multiple experimental models.
 * <p>
 * Implementations of this interface should process OpenRTB requests by applying
 * all relevant models within the experiment. The results from these
 * individual model evaluations are then aggregated to produce a single,
 * comprehensive evaluation output.
 * </p>
 */
public interface BidRequestEvaluator {

    /**
     * Processes a bid request using all applicable experimental models and
     * aggregates their results into a final evaluation.
     *
     * @param request The input containing the OpenRTB request and any additional
     *                data required for evaluation.
     * @return An object encapsulating the aggregated results of all model
     * evaluations performed on the input request.
     */
    BidRequestEvaluatorOutput evaluate(BidRequestEvaluatorInput request);
}
