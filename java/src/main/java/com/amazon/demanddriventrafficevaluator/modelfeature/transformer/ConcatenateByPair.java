// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature.transformer;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * A transformer that concatenates pairs of values in a ModelFeature.
 * <p>
 * This class implements the Transformer interface and is responsible for
 * transforming the values of a ModelFeature by concatenating adjacent pairs
 * of values. The concatenation is done using 'x' as a separator.
 * </p>
 */
public class ConcatenateByPair implements Transformer {

    public ConcatenateByPair() {
    }

    /**
     * Transforms the values of a ModelFeature by concatenating adjacent pairs.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Retrieves the original values from the input ModelFeature</li>
     *   <li>Iterates through the values, processing them in pairs</li>
     *   <li>For each pair, concatenates the values using 'x' as a separator</li>
     *   <li>Skips pairs where either value is null or empty</li>
     *   <li>Creates a new ModelFeature with the concatenated values</li>
     * </ol>
     * If the input has an odd number of values, the last value is ignored.
     * </p>
     *
     * @param modelFeature The input ModelFeature to be transformed.
     * @return A new ModelFeature with the same configuration as the input,
     * but with values transformed by pair-wise concatenation.
     * The number of values in the output will be at most half
     * the number of values in the input.
     */
    @Override
    public ModelFeature transform(ModelFeature modelFeature) {
        List<String> originalValues = modelFeature.getValues();
        int inputSize = originalValues.size();
        List<String> transformedValues = new ArrayList<>(inputSize / 2);
        for (int i = 0; i < inputSize / 2; i++) {
            String first = originalValues.get(i * 2);
            String second = originalValues.get(i * 2 + 1);
            if (!(first == null || first.isEmpty() || second == null || second.isEmpty())) {
                transformedValues.add(first + "x" + second);
            }
        }
        return ModelFeature.builder()
                .configuration(modelFeature.getConfiguration())
                .values(transformedValues)
                .build();
    }
}
