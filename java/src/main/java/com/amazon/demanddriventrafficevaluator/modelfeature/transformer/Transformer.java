// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature.transformer;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;
import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeatureOperator;

/**
 * An interface for transforming ModelFeature objects.
 * <p>
 * Implementations of this interface are responsible for applying specific
 * transformations to ModelFeature objects. These transformations can include
 * data manipulation, feature engineering, or any other operation that modifies
 * the content or structure of a ModelFeature.
 * </p>
 * <p>
 * This interface extends ModelFeatureOperator, so that the transformer
 * is a type of operator that can be performed on model features.
 * </p>
 */
public interface Transformer extends ModelFeatureOperator {

    /**
     * Transforms a given ModelFeature.
     * <p>
     * This method takes a ModelFeature as input, applies a specific transformation,
     * and returns a new ModelFeature containing the transformed data. The exact
     * nature of the transformation depends on the implementing class.
     * </p>
     * <p>
     * Implementations should ensure that the original ModelFeature is not modified,
     * adhering to the principle of immutability. Instead, a new ModelFeature instance
     * should be created and returned with the transformed data.
     * </p>
     *
     * @param modelFeature The ModelFeature to be transformed.
     * @return A new ModelFeature containing the result of the transformation.
     * The returned ModelFeature may have different values, but typically
     * retains the same configuration as the input ModelFeature.
     */
    ModelFeature transform(ModelFeature modelFeature);
}
