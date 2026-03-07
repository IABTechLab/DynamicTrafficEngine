// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature.transformer;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class IncludeDefaultValue implements Transformer {

    public IncludeDefaultValue() {
    }

    /**
     * Transforms the ModelFeature by appending the default value, if present.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Retrieves the original values from the input ModelFeature</li>
     *   <li>Filters out null and empty values</li>
     *   <li>Appends the default value as defined in the configuration, if it exists, to the values</li>
     *   <li>Creates a new ModelFeature with the new values</li>
     * </ol>
     * </p>
     *
     * @param modelFeature The input ModelFeature to be transformed.
     * @return A new ModelFeature with the same configuration as the input,
     * but with a list of values with the default value appended, if defined.
     */
    @Override
    public ModelFeature transform(ModelFeature modelFeature) {
        List<String> values = modelFeature.getValues();
        List<String> transformedValues = new ArrayList<>(values.size() + 1);
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                transformedValues.add(value);
            }
        }
        if (!StringUtils.isEmpty(modelFeature.getConfiguration().getMappingDefaultValue())) {
            transformedValues.add(modelFeature.getConfiguration().getMappingDefaultValue());
        }
        return ModelFeature.builder()
                .configuration(modelFeature.getConfiguration())
                .values(transformedValues)
                .build();
    }

}
