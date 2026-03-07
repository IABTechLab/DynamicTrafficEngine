// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature.transformer;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * A transformer that checks for the existence of non-empty values in a ModelFeature.
 * <p>
 * This class implements the Transformer interface and is responsible for
 * transforming the values of a ModelFeature into binary indicators of existence.
 * It converts each value to "1" if it exists and is non-empty, or "0" if it's
 * empty or null.
 * </p>
 */
public class Exists implements Transformer {

    public Exists() {
    }

    /**
     * Transforms the values of a ModelFeature into existence indicators.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Retrieves the original values from the input ModelFeature</li>
     *   <li>Filters out null values</li>
     *   <li>Maps each non-null value to "1" if it's non-empty, or "0" if it's empty</li>
     *   <li>Creates a new ModelFeature with the transformed values</li>
     * </ol>
     * </p>
     *
     * @param modelFeature The input ModelFeature to be transformed.
     * @return A new ModelFeature with the same configuration as the input,
     * but with values transformed to "1" or "0" based on their existence
     * and non-emptiness. The output will not contain any null values.
     */
    @Override
    public ModelFeature transform(ModelFeature modelFeature) {
        List<String> values = modelFeature.getValues();
        List<String> transformedValues = new ArrayList<>(values.size());
        for (String value: values) {
            if (value != null) {
                transformedValues.add(value.isEmpty() ? "0" : "1");
            }
        }
        return ModelFeature.builder()
                .configuration(modelFeature.getConfiguration())
                .values(transformedValues)
                .build();
    }
}
