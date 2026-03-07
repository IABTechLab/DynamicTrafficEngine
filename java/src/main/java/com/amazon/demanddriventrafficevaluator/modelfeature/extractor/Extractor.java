// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature.extractor;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.OpenRtbRequestContext;
import com.amazon.demanddriventrafficevaluator.modelfeature.FeatureExtractorType;
import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;
import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeatureOperator;
import com.amazon.demanddriventrafficevaluator.repository.entity.FeatureConfiguration;

/**
 * An interface for extracting model features from a document context based on a given configuration.
 * <p>
 * Implementations of this interface are responsible for parsing a document (typically represented
 * by a DocumentContext or Map) and extracting specific features as defined by a FeatureConfiguration.
 * This interface extends ModelFeatureOperator, so that extractor is one type of
 * operator that can be performed on model features.
 * </p>
 */
public interface Extractor extends ModelFeatureOperator {

    /**
     * Extracts a model feature from the given context based on the provided configuration.
     * <p>
     * This method should parse the openRtbRequestContext and extract the relevant data as specified
     * by the featureConfiguration. The extracted data should be encapsulated in a ModelFeature object.
     * </p>
     *
     * @param openRtbRequestContext The context of the request from which to extract features.
     *                              This typically represents the structure and content of the document.
     * @param featureConfiguration  The configuration specifying which features to extract and how.
     * @return A ModelFeature object containing the extracted feature data.
     */
    ModelFeature extract(OpenRtbRequestContext openRtbRequestContext, FeatureConfiguration featureConfiguration);

    /**
     * Returns the type of this feature extractor.
     * <p>
     * This method allows for identification of the specific type of extractor,
     * which can be useful for routing or processing logic based on extractor types.
     * </p>
     *
     * @return The FeatureExtractorType enum value representing this extractor's type.
     */
    FeatureExtractorType getType();
}
