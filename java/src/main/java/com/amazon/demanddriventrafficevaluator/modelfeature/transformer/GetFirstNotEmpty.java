// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature.transformer;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;

import java.util.List;

/**
 * A transformer that retrieves the first non-empty value from a ModelFeature.
 * <p>
 * This class implements the Transformer interface and is responsible for
 * transforming a ModelFeature by selecting the first non-null and non-empty
 * value from its list of values. If no such value is found, it returns an
 * empty string.
 * </p>
 */
public class GetFirstNotEmpty implements Transformer {

    public GetFirstNotEmpty() {
    }

    /**
     * Transforms the ModelFeature by selecting the first non-empty value.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Retrieves the original values from the input ModelFeature</li>
     *   <li>Filters out null and empty values</li>
     *   <li>Selects the first remaining value, if any</li>
     *   <li>If no non-empty value is found, uses an empty string</li>
     *   <li>Creates a new ModelFeature with a single-element list containing the selected value</li>
     * </ol>
     * </p>
     *
     * @param modelFeature The input ModelFeature to be transformed.
     * @return A new ModelFeature with the same configuration as the input,
     * but with a single-element list of values containing either
     * the first non-empty value found or an empty string.
     */
    @Override
    public ModelFeature transform(ModelFeature modelFeature) {
        String value = "";
        for (String str: modelFeature.getValues()) {
            if (str != null && !str.isEmpty()) {
                value = str;
                break;
            }
        }
        List<String> transformedValues = List.of(value);
        return ModelFeature.builder()
                .configuration(modelFeature.getConfiguration())
                .values(transformedValues)
                .build();
    }
}
