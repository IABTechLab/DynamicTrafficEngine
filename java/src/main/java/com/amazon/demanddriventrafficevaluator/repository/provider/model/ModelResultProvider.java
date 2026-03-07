// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.provider.model;

import com.amazon.demanddriventrafficevaluator.repository.entity.ModelResult;

/**
 * An interface for providing model results based on input data.
 * <p>
 * This interface defines a contract for classes that are responsible for
 * retrieving or generating model results. Implementations of this interface
 * should take a ModelResultProviderInput and return a corresponding ModelResult.
 * </p>
 */
public interface ModelResultProvider {
    /**
     * Provides a model result based on the given input.
     * <p>
     * This method is responsible for processing the input data and producing
     * a ModelResult. The exact behavior of this method depends on the
     * implementing class. It may involve:
     * <ul>
     *   <li>Retrieving pre-computed results from a cache or database</li>
     *   <li>Executing a model in real-time to generate results</li>
     *   <li>Applying business rules to determine the appropriate result</li>
     *   <li>Any combination of the above or other relevant strategies</li>
     * </ul>
     * </p>
     *
     * @param input The ModelResultProviderInput containing necessary data
     *              for generating or retrieving the model result. This typically
     *              includes model features and model definition.
     * @return A ModelResult object containing the result of the model evaluation.
     */
    ModelResult provide(ModelResultProviderInput input);
}
