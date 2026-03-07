// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.experiment;

import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentDefinition;

/**
 * Interface for allocating treatments in experimental scenarios.
 * <p>
 * Implementations of this interface are responsible for determining which treatment
 * should be applied to a given request within the context of an experiment. They also
 * handle updates to the experiment configuration.
 * </p>
 */
public interface TreatmentAllocator {

    /**
     * Updates the allocator's configuration based on the provided experiment configuration.
     * <p>
     * This method should be called whenever there are changes to the experiment setup,
     * such as modifications to treatment weights or the addition/removal of experiments.
     * Implementations should ensure that this method is thread-safe.
     * </p>
     *
     * @param experimentConfiguration The new configuration containing details of all experiments.
     */
    void updateConfiguration(ExperimentConfiguration experimentConfiguration);

    /**
     * Determines the treatment code for a given request within a specific experiment.
     * <p>
     * This method should use the allocator's logic to decide which treatment to apply
     * to the given request. The decision may be based on various factors such as random
     * allocation, user attributes, or other criteria defined in the implementation.
     * </p>
     *
     * @param requestId            The unique identifier for the request. This may be used to ensure
     *                             consistent treatment allocation for the same request.
     * @param experimentDefinition The definition of the experiment for which a treatment
     *                             is being allocated.
     * @return The treatment code assigned to the request.
     */
    String getTreatmentCode(String requestId, ExperimentDefinition experimentDefinition);
}
