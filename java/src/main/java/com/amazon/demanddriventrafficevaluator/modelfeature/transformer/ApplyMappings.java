// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature.transformer;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;
import com.amazon.demanddriventrafficevaluator.repository.entity.FeatureConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A transformer that applies predefined mappings to model feature values.
 * <p>
 * This class implements the Transformer interface and is responsible for
 * transforming the values of a ModelFeature based on a mapping defined in
 * the feature's configuration. If a value doesn't have a defined mapping,
 * a default value is used.
 * </p>
 */
public class ApplyMappings implements Transformer {

    public ApplyMappings() {
    }

    /**
     * Transforms the values of a ModelFeature by applying predefined mappings.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Retrieves the mapping and default value from the feature's configuration</li>
     *   <li>Iterates through each value in the input ModelFeature</li>
     *   <li>Applies the mapping to each value, using the default if no mapping exists</li>
     *   <li>Creates a new ModelFeature with the transformed values</li>
     * </ol>
     * </p>
     *
     * @param modelFeature The input ModelFeature to be transformed.
     * @return A new ModelFeature with the same configuration as the input,
     * but with values transformed according to the mapping.
     */
    @Override
    public ModelFeature transform(ModelFeature modelFeature) {
        FeatureConfiguration configuration = modelFeature.getConfiguration();
        Map<String, String> mapping = configuration.getMapping();
        String mappingDefaultValue = configuration.getMappingDefaultValue();
        List<String> values = modelFeature.getValues();

        List<String> transformedValues = new ArrayList<>(values.size());
        for (String value: values) {
            transformedValues.add(mapping.getOrDefault(value, mappingDefaultValue));
        }
        return ModelFeature.builder()
                .configuration(modelFeature.getConfiguration())
                .values(transformedValues)
                .build();
    }
}
