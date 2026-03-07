// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.experiment;

import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExperimentContext {

    private static final String TREATMENT_GROUP_T = "T";
    private final Map<String, String> experimentArrangement;
    private final Map<String, ExperimentDefinition> experimentDefinitionByName;
    private final Map<String, String> modelToExperiment;
    private final Map<String, List<String>> modelsByExperiment;

    public ExperimentContext(Map<String, String> experimentArrangement, ExperimentConfiguration configuration) {
        this.experimentArrangement = experimentArrangement;
        this.experimentDefinitionByName = configuration.getExperimentDefinitionByName();
        this.modelToExperiment = configuration.getModelToExperiment();
        this.modelsByExperiment = new HashMap<>();

        for (Map.Entry<String, String> entry : modelToExperiment.entrySet()) {
            String model = entry.getKey();
            String experiment = entry.getValue();

            // If this experiment isn't in our result map yet, create a new list for it
            if (!this.modelsByExperiment.containsKey(experiment)) {
                this.modelsByExperiment.put(experiment, new ArrayList<>());
            }

            // Add the model to the list for this experiment
            this.modelsByExperiment.get(experiment).add(model);
        }
    }

    public List<String> getModelIdentifiers() {
        return new ArrayList<>(modelToExperiment.keySet());
    }

    public String getTreatmentCode(String experimentName) {
        return experimentArrangement.getOrDefault(experimentName, null);
    }

    public int getTreatmentCodeInInt(String experimentName) {
        if (TREATMENT_GROUP_T.equals(experimentArrangement.getOrDefault(experimentName, null))) {
            return 0;
        }
        return 1;
    }

    public ExperimentDefinition getExperimentDefinition(String experimentName) {
        return experimentDefinitionByName.get(experimentName);
    }

    public ExperimentDefinition getExperimentDefinitionByModel(String model) {
        String experimentName = modelToExperiment.get(model);
        return experimentDefinitionByName.get(experimentName);
    }

    public ExperimentDefinition getExperimentDefinitionByType(String type) {
        for (ExperimentDefinition experiment : experimentDefinitionByName.values()) {
            if (experiment.getType().equals(type)) {
                return experiment;
            }
        }
        throw new IllegalStateException("ExperimentDefinition with type [" + type + "] not found");
    }

    public Map<String, List<String>> getModelsByExperiment() {
        return this.modelsByExperiment;
    }
}
