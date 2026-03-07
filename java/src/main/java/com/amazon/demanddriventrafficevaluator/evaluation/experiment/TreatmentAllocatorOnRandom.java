// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.experiment;

import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentDefinition;
import com.amazon.demanddriventrafficevaluator.repository.entity.TreatmentDefinition;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.log4j.Log4j2;

/**
 * A treatment allocator that assigns treatments randomly based on predefined weights.
 * <p>
 * This class implements the TreatmentAllocator interface, providing a mechanism to
 * randomly allocate treatments for experiments. It uses a thread-safe approach to
 * handle concurrent requests and maintains a set of thresholds for each experiment
 * to ensure proper distribution of treatments according to their defined weights.
 * </p>
 */
@Log4j2
public class TreatmentAllocatorOnRandom implements TreatmentAllocator {

    private final AtomicLong seed;
    private ConcurrentHashMap<String, int[]> experimentThresholds;

    public TreatmentAllocatorOnRandom() {
        this.experimentThresholds = new ConcurrentHashMap<>();
        this.seed = new AtomicLong(System.nanoTime());
    }

    /**
     * Determines the treatment code for a given request and experiment.
     *
     * @param requestId            The unique identifier for the request.
     * @param experimentDefinition The definition of the experiment.
     * @return The allocated treatment code.
     * @throws IllegalStateException if no thresholds are found for the experiment
     *                               or if no treatments are configured.
     */
    @Override
    public String getTreatmentCode(String requestId, ExperimentDefinition experimentDefinition) {
        log.debug("getTreatmentCode experimentThresholds: {}", this.experimentThresholds);
        String experimentName = experimentDefinition.getName();
        int[] thresholds = this.experimentThresholds.get(experimentName);
        if (thresholds == null) {
            throw new IllegalStateException("No thresholds found for experiment: " + experimentName);
        }
        List<TreatmentDefinition> treatmentDefinitions = experimentDefinition.getTreatmentDefinitions();
        if (treatmentDefinitions.isEmpty()) {
            throw new IllegalStateException("No treatments configured");
        }

        int r = rand() % 100;

        // Binary search
        int i = 0;
        int j = thresholds.length - 1;
        while (i < j) {
            int h = (i + j) >>> 1;
            if (r >= thresholds[h]) {
                i = h + 1;
            } else {
                j = h;
            }
        }
        return treatmentDefinitions.get(i).getTreatmentCode();
    }

    int rand() {
        return ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    }

    /**
     * Updates the configuration for all experiments.
     * <p>
     * This method recalculates the thresholds for each experiment based on the
     * weights of their treatments. It ensures that the total weight for each
     * experiment sums to 100.
     * </p>
     *
     * @param experimentConfiguration The new configuration for all experiments.
     * @throws IllegalStateException if the total weight of treatments for any
     *                               experiment does not sum to 100.
     */
    @Override
    public void updateConfiguration(ExperimentConfiguration experimentConfiguration) {
        log.debug("Updating experiment configuration: {}", experimentConfiguration);

        // Create a new map for the thresholds
        ConcurrentHashMap<String, int[]> newThresholds = new ConcurrentHashMap<>();

        // Validate and compute all thresholds
        for (Map.Entry<String, ExperimentDefinition> entry: experimentConfiguration.getExperimentDefinitionByName().entrySet()) {
            String experimentName = entry.getKey();
            ExperimentDefinition experimentDefinition = entry.getValue();

            int totalWeight = 0;
            for (TreatmentDefinition t : experimentDefinition.getTreatmentDefinitions()) {
                totalWeight += t.getWeight();
            }

            if (totalWeight != 100) {
                throw new IllegalStateException("total weight must be 100, got " + totalWeight);
            }

            int[] thresholds = new int[experimentDefinition.getTreatmentDefinitions().size()];
            int cumulativeWeight = 0;

            for (int i = 0; i < experimentDefinition.getTreatmentDefinitions().size(); i++) {
                cumulativeWeight += experimentDefinition.getTreatmentDefinitions().get(i).getWeight();
                thresholds[i] = cumulativeWeight;
            }
            newThresholds.put(experimentName, thresholds);
        }

        // Atomic update of both configuration and thresholds
        this.experimentThresholds = newThresholds;
        log.debug("Updated thresholds: {}", experimentThresholds);
    }
}
