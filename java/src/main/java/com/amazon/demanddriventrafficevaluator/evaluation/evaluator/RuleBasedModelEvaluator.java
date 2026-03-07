// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import com.amazon.demanddriventrafficevaluator.modelfeature.Extraction;
import com.amazon.demanddriventrafficevaluator.modelfeature.FeatureExtractorType;
import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;
import com.amazon.demanddriventrafficevaluator.modelfeature.Transformation;
import com.amazon.demanddriventrafficevaluator.repository.entity.FeatureConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelDefinition;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelResult;
import com.amazon.demanddriventrafficevaluator.repository.provider.model.ModelResultProvider;
import com.amazon.demanddriventrafficevaluator.repository.provider.model.ModelResultProviderInput;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;

/**
 * A rule-based implementation of the ModelEvaluator interface.
 * This class is responsible for evaluating models based on predefined rules and configurations.
 * It handles feature extraction, transformation, and model result generation.
 * <p>
 * The evaluator uses three main components:
 * <ul>
 *     <li>Extraction: for extracting raw features from the input data</li>
 *     <li>Transformation: for transforming extracted features into a suitable format</li>
 *     <li>ModelResultProvider: for generating model results based on the transformed features</li>
 * </ul>
 * </p>
 */
@Log4j2
public class RuleBasedModelEvaluator implements ModelEvaluator {

    private final Extraction extraction;
    private final Transformation transformation;
    private final ModelResultProvider ruleBasedmodelResultProvider;

    public RuleBasedModelEvaluator(
            Extraction extraction,
            Transformation transformation,
            ModelResultProvider ruleBasedmodelResultProvider
    ) {
        this.extraction = extraction;
        this.transformation = transformation;
        this.ruleBasedmodelResultProvider = ruleBasedmodelResultProvider;
    }

    /**
     * Extracts and transforms features based on the provided input.
     * This method processes the model definition to extract features using the specified
     * feature configurations and extractor type, then applies transformations to these features.
     *
     * @param input The ModelEvaluatorInput containing the necessary context and model definition.
     * @return A list of transformed ModelFeature objects.
     * @throws RuntimeException if an error occurs during feature extraction or transformation.
     */
    @Override
    public List<ModelFeature> getFeatures(ModelEvaluatorInput input) {
        ModelEvaluationContext context = input.getContext();
        try {
            ModelDefinition modelDefinition = input.getModelDefinition();
            List<FeatureConfiguration> featureConfigurations = modelDefinition.getFeatures();
            FeatureExtractorType featureExtractorType = modelDefinition.getFeatureExtractorType();
            log.debug("featureConfigurations: {}", featureConfigurations);
            OpenRtbRequestContext openRtbRequestContext = context.getEvaluationContext().getOpenRtbRequestContext();

            List<ModelFeature> modelFeatures = new ArrayList<>(featureConfigurations.size());
            for (FeatureConfiguration featureConfiguration : featureConfigurations) {
                ModelFeature modelFeature = extraction.extract(openRtbRequestContext, featureConfiguration, featureExtractorType);
                modelFeatures.add(transformation.transform(modelFeature));
            }

            return modelFeatures;
        } catch (Exception e) {
            context.addError("Error while getting features.\n" + e.getMessage());
            throw new RuntimeException("Error while getting features", e);
        }
    }

    /**
     * Evaluates the model based on the provided input.
     * This method extracts features, generates a model result, and produces a ModelEvaluatorOutput.
     * If successful, it returns an output with a SUCCESS status and the generated model result.
     * If an error occurs, it returns an output with an ERROR status.
     *
     * @param input The ModelEvaluatorInput containing the necessary context and model definition.
     * @return A ModelEvaluatorOutput containing the evaluation results or error status.
     */
    @Override
    public ModelEvaluatorOutput evaluate(ModelEvaluatorInput input) {
        ModelEvaluationContext context = input.getContext();
        ModelDefinition modelDefinition = input.getModelDefinition();
        log.debug("modelDefinition: {}", modelDefinition);
        try {
            List<ModelFeature> modelFeatures = getFeatures(input);
            log.debug("modelFeatures: {}", modelFeatures);
            ModelResult modelResult = ruleBasedmodelResultProvider.provide(
                    ModelResultProviderInput.builder()
                            .modelDefinition(modelDefinition)
                            .modelFeatures(modelFeatures)
                            .build()
            );
            return ModelEvaluatorOutput.builder()
                    .context(context)
                    .status(ModelEvaluationStatus.SUCCESS)
                    .modelResult(modelResult)
                    .modelFeatures(modelFeatures)
                    .modelDefinition(modelDefinition)
                    .build();
        } catch (Exception e) {
            context.addError("Error while evaluating model.\n" + e.getMessage());
            log.error("Error while evaluating model", e);
            return ModelEvaluatorOutput.builder()
                    .context(context)
                    .modelDefinition(modelDefinition)
                    .status(ModelEvaluationStatus.ERROR)
                    .build();
        }
    }
}
