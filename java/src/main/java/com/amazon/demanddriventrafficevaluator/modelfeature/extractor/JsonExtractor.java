// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature.extractor;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.OpenRtbRequestContext;
import com.amazon.demanddriventrafficevaluator.modelfeature.FeatureExtractorType;
import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;
import com.amazon.demanddriventrafficevaluator.repository.entity.FeatureConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * An implementation of the Extractor interface specialized for extracting features from JSON contexts.
 * <p>
 * This extractor uses JsonPath conventions to navigate and extract values from a JSON structure
 * represented by a DocumentContext or Map. It can handle multiple fields specified in a
 * FeatureConfiguration and returns the extracted values as a ModelFeature.
 * </p>
 */
public class JsonExtractor implements Extractor {

    public JsonExtractor() {
    }

    /**
     * Extracts feature values from a JSON-based request based on the provided configuration.
     * <p>
     * This method iterates through the fields specified in the FeatureConfiguration,
     * attempts to extract each field's value from the OpenRtbRequestContext, and collects
     * these values into a ModelFeature object.
     * </p>
     *
     * @param openRtbRequestContext The context representing the JSON document to extract from.
     * @param featureConfiguration  The configuration specifying which fields to extract.
     * @return A ModelFeature containing the extracted values and the original configuration.
     */
    @Override
    public ModelFeature extract(OpenRtbRequestContext openRtbRequestContext, FeatureConfiguration featureConfiguration) {
        List<String> fields = featureConfiguration.getFields();
        List<String> attributes = new ArrayList<>(fields.size());
        for (String field: fields) {
            attributes.addAll(openRtbRequestContext.findPath(field));
        }
        return ModelFeature.builder()
                .configuration(featureConfiguration)
                .values(attributes)
                .build();
    }

    @Override
    public FeatureExtractorType getType() {
        return FeatureExtractorType.JsonExtractor;
    }

}
