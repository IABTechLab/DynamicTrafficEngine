// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature;

import com.amazon.demanddriventrafficevaluator.modelfeature.transformer.Transformer;
import com.amazon.demanddriventrafficevaluator.repository.entity.FeatureConfiguration;

import java.util.List;
import java.util.function.Function;

/**
 * A class responsible for applying a series of transformations to model features.
 * <p>
 * This class manages the process of applying multiple transformers to a given ModelFeature,
 * based on the transformations specified in the feature's configuration. It uses a registry
 * of transformers to retrieve the appropriate transformer for each transformation step.
 * </p>
 */
public class Transformation {
    private final Registry<Transformer> transformerRegistry;

    public Transformation(Registry<Transformer> transformerRegistry) {
        this.transformerRegistry = transformerRegistry;
    }

    /**
     * Converts a Transformer to a Function that can be used in a transformation chain.
     *
     * @param transformer The Transformer to convert.
     * @return A Function that applies the transformer's transform method.
     */
    private static Function<ModelFeature, ModelFeature> convertToFunction(Transformer transformer) {
        return transformer::transform;
    }

    /**
     * Applies a series of transformations to the given ModelFeature.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Retrieves the list of transformations from the feature's configuration</li>
     *   <li>For each transformation, retrieves the corresponding transformer from the registry</li>
     *   <li>Throws an IllegalArgumentException if any transformer is not found</li>
     *   <li>Applies the transformation in-place to the feature</li>
     *   <li>Returns the transformed feature</li>
     * </ol>
     * </p>
     *
     * @param feature The ModelFeature to transform.
     * @return A new ModelFeature resulting from applying all specified transformations.
     * @throws IllegalArgumentException if a required transformer is not found in the registry.
     */
    public ModelFeature transform(ModelFeature feature) {
        FeatureConfiguration configuration = feature.getConfiguration();
        List<FeatureTransformerName> normalizations = configuration.getTransformations();
        for (FeatureTransformerName normalization : normalizations) {
            Transformer transformer = transformerRegistry.get(normalization.toString());
            if (transformer == null) {
                throw new IllegalArgumentException("No transformer found for FeatureTransformerName: "
                        + normalization);
            }
            // in-place transformations during the loop
            feature = transformer.transform(feature);
        }
        return feature;
    }
}
