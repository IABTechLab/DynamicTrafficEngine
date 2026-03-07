// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.OpenRtbRequestContext;
import com.amazon.demanddriventrafficevaluator.modelfeature.extractor.Extractor;
import com.amazon.demanddriventrafficevaluator.repository.entity.FeatureConfiguration;

/**
 * A class responsible for extracting model features from a context using registered extractors.
 * <p>
 * This class acts as a facade for the feature extraction process, delegating the actual extraction
 * to specific extractor implementations based on the provided FeatureExtractorType.
 * </p>
 */
public class Extraction {
    private final Registry<Extractor> extractorRegistry;

    public Extraction(Registry<Extractor> extractorRegistry) {
        this.extractorRegistry = extractorRegistry;
    }

    /**
     * Extracts a model feature from the given context using the specified configuration and extractor type.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Retrieves the appropriate extractor from the registry based on the provided FeatureExtractorType</li>
     *   <li>If no extractor is found, throws an IllegalArgumentException</li>
     *   <li>Delegates the extraction process to the retrieved extractor</li>
     * </ol>
     * </p>
     *
     * @param openRtbRequest       The context from which to extract the feature.
     * @param configuration        The configuration specifying how to extract the feature.
     * @param featureExtractorType The type of extractor to use for this extraction.
     * @return A ModelFeature containing the extracted data.
     * @throws IllegalArgumentException if no extractor is registered for the given FeatureExtractorType.
     */
    public ModelFeature extract(OpenRtbRequestContext openRtbRequest, FeatureConfiguration configuration, FeatureExtractorType featureExtractorType) {
        Extractor extractor = extractorRegistry.get(featureExtractorType.toString());
        if (extractor == null) {
            throw new IllegalArgumentException("No extractor found for feature extraction type: "
                    + featureExtractorType);
        }
        return extractor.extract(openRtbRequest, configuration);
    }
}
