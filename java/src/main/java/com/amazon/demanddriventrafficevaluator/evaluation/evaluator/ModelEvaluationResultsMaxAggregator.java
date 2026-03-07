// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import com.amazon.demanddriventrafficevaluator.evaluation.experiment.ExperimentContext;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentDefinition;

import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;

/**
 * Aggregates model evaluation results using a maximum-based approach.
 * <p>
 * This aggregator is designed for soft-filter experiments with Control and Treatment groups.
 * It applies the following logic:
 * <ul>
 *     <li>If any model evaluation result has a score of 1.0 (indicating high-value traffic),
 *         the aggregated score will be 1.0.</li>
 *     <li>Otherwise, the aggregated score will be 0.0 (indicating low-value traffic).</li>
 * </ul>
 * </p>
 */
@Log4j2
public class ModelEvaluationResultsMaxAggregator implements ModelEvaluationResultsAggregator {

    private static final String EXPERIMENT_TYPE_SOFT_FILTER = "soft-filter";
    private static final double FALLBACK_AGGREGATED_SCORE = 1.0;
    private static final String AGGREGATION_TYPE_MAX = "max";

    /**
     * Aggregates model evaluation results for a soft-filter experiment.
     * <p>
     * This method processes the evaluation context to determine the maximum score
     * among all successfully evaluated models within the specified experiment.
     * It also applies the treatment code to potentially adjust the final score.
     * </p>
     *
     * @param context The EvaluationContext containing experiment details and model outputs.
     * @return An AggregatedModelEvaluationResult object containing:
     * <ul>
     *     <li>Experiment name and type</li>
     *     <li>Treatment code (as string and integer)</li>
     *     <li>Aggregated score (max of all model scores)</li>
     *     <li>Aggregated score with treatment (max of aggregated score and treatment code)</li>
     *     <li>Aggregation type (always "max")</li>
     * </ul>
     */
    @Override
    public AggregatedModelEvaluationResult aggregate(EvaluationContext context) {
        String experimentName = "UnknownExperiment";
        String treatmentCode = "UnknownTreatmentCode";
        double aggregatedScore = FALLBACK_AGGREGATED_SCORE;
        double aggregatedScoreWithTreatment = FALLBACK_AGGREGATED_SCORE;
        try {
            ExperimentContext experimentContext = context.getExperimentContext();
            ExperimentDefinition experimentDefinition = experimentContext.getExperimentDefinitionByType(EXPERIMENT_TYPE_SOFT_FILTER);
            experimentName = experimentDefinition.getName();
            Map<String, List<String>> modelsByExperiment = experimentContext.getModelsByExperiment();
            List<String> modelsInExperiment = modelsByExperiment.get(experimentName);
            if (modelsInExperiment == null || modelsInExperiment.isEmpty()) {
                throw new IllegalStateException("No models associated with experiment [" + experimentName + "]");
            }
            final String finalExperimentName = experimentName;
            List<ModelEvaluatorOutput> modelEvaluatorOutputs = context.getModelEvaluatorOutputs();

            boolean foundValidModel = false;
            for (ModelEvaluatorOutput output : modelEvaluatorOutputs) {
                if (output.getStatus() == ModelEvaluationStatus.SUCCESS
                        && modelsInExperiment.contains(output.getModelDefinition().getIdentifier())) {

                    // Get the value and update if first valid score, or compare with current max
                    double value = output.getModelResult().getValue();
                    if (!foundValidModel || value > aggregatedScore) {
                        aggregatedScore = value;
                        foundValidModel = true;
                    }
                }
            }
            // Throw exception if no valid model evaluations found
            if (!foundValidModel) {
                throw new IllegalStateException("No models have been evaluated for the experiment [" + finalExperimentName + "]");
            }

            int treatmentCodeInInt = experimentContext.getTreatmentCodeInInt(experimentName);
            log.debug("Treatment code in int: {}", treatmentCodeInInt);
            aggregatedScoreWithTreatment = Math.max(aggregatedScore, treatmentCodeInInt);
            treatmentCode = experimentContext.getTreatmentCode(experimentName);
            return AggregatedModelEvaluationResult.builder()
                    .experimentName(experimentName)
                    .experimentType(EXPERIMENT_TYPE_SOFT_FILTER)
                    .treatmentCode(treatmentCode)
                    .treatmentCodeInInt(treatmentCodeInInt)
                    .score(aggregatedScore)
                    .scoreWithTreatment(aggregatedScoreWithTreatment)
                    .aggregationType(AGGREGATION_TYPE_MAX)
                    .build();
        } catch (Exception e) {
            context.addError("Failed to aggregate model evaluation results.\n" + e.getMessage());
            log.error("Failed to aggregate model evaluation results", e);
            return AggregatedModelEvaluationResult.builder()
                    .experimentName(experimentName)
                    .experimentType(EXPERIMENT_TYPE_SOFT_FILTER)
                    .treatmentCode(treatmentCode)
                    .score(aggregatedScore)
                    .scoreWithTreatment(aggregatedScoreWithTreatment)
                    .aggregationType(AGGREGATION_TYPE_MAX)
                    .build();
        }
    }
}
