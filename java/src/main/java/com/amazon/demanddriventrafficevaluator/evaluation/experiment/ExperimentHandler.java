// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.experiment;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the assignment of treatments to experiments.
 * <p>
 * This class provides a mechanism to assign specific treatments to named experiments
 * and retrieve the overall experiment arrangement. It ensures thread-safe access to
 * the experiment-treatment mappings.
 * </p>
 */
public class ExperimentHandler {

    private final Map<String, String> experimentArrangement;

    public ExperimentHandler() {
        this.experimentArrangement = new HashMap<>();
    }

    /**
     * Assigns a treatment code to a specific experiment.
     * <p>
     * This method associates the given treatment code with the specified experiment name.
     * If an assignment already exists for the experiment, it will be overwritten.
     * </p>
     *
     * @param experimentName The name of the experiment to assign a treatment to.
     * @param treatmentCode  The treatment code to assign to the experiment.
     */
    public void assignTreatmentOnExperiment(
            String experimentName,
            String treatmentCode
    ) {
        experimentArrangement.put(experimentName, treatmentCode);
    }

    /**
     * Retrieves the current experiment-treatment assignments.
     * <p>
     * This method returns an unmodifiable view of the internal map containing
     * all current experiment-treatment assignments. This ensures that the
     * returned map cannot be modified by the caller, preserving the integrity
     * of the experiment arrangements.
     * </p>
     *
     * @return An unmodifiable Map where keys are experiment names and values are
     * the assigned treatment codes.
     */
    public Map<String, String> getExperimentArrangement() {
        return Collections.unmodifiableMap(experimentArrangement);
    }
}
