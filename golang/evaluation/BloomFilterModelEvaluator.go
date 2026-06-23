// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package evaluation

import (
	"fmt"

	"golang.a2z.com/demanddriventrafficevaluator/bloomfilter"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/modelfeature"
)

// BloomFilterModelEvaluator provides a filter recommendation for OpenRTB requests
// using bloom filter models. Feature extraction is identical to RuleBasedModelEvaluator.
type BloomFilterModelEvaluator struct {
	bloomFilterProvider bloomfilter.BloomFilterProviderInterface
}

// NewBloomFilterModelEvaluator creates a new BloomFilterModelEvaluator with the given provider.
func NewBloomFilterModelEvaluator(provider bloomfilter.BloomFilterProviderInterface) *BloomFilterModelEvaluator {
	return &BloomFilterModelEvaluator{
		bloomFilterProvider: provider,
	}
}

// Evaluate extracts features from the OpenRTB request and performs a bloom filter lookup.
func (e *BloomFilterModelEvaluator) Evaluate(input interfaces.ModelEvaluatorInput) (*interfaces.ModelEvaluatorOutput, error) {
	modelDefinition := input.ModelDefinition
	modelFeatures, err := e.getFeatures(input)
	if err != nil {
		return &interfaces.ModelEvaluatorOutput{
			Status: interfaces.ModelEvaluationStatusError,
		}, fmt.Errorf("error getting modelFeatures: %w", err)
	}
	Logger.Debug().Msgf("modelFeatures: %+v", modelFeatures)
	modelResult, err := e.bloomFilterProvider.Provide(modelDefinition.Identifier, modelFeatures, e.getDefaultValue(modelDefinition.Type))
	Logger.Debug().Msgf("modelResult: %+v", modelResult)
	if err != nil {
		return &interfaces.ModelEvaluatorOutput{
			Status:          interfaces.ModelEvaluationStatusError,
			ModelDefinition: *modelDefinition,
			ModelFeatures:   modelFeatures,
		}, fmt.Errorf("error getting modelResult: %w", err)
	}

	output := &interfaces.ModelEvaluatorOutput{
		Context:         *input.Context,
		Status:          interfaces.ModelEvaluationStatusSuccess,
		ModelResult:     *modelResult,
		ModelDefinition: *modelDefinition,
		ModelFeatures:   modelFeatures,
	}
	return output, nil
}

// getFeatures extracts and transforms features identically to RuleBasedModelEvaluator.
func (e *BloomFilterModelEvaluator) getFeatures(input interfaces.ModelEvaluatorInput) ([]interfaces.ModelFeature, error) {
	modelDefinition := input.ModelDefinition
	featureConfigurations := modelDefinition.Features
	Logger.Debug().Msgf("featureConfigurations: %+v", featureConfigurations)
	featureFieldValueMap := input.FeatureFieldValueMap
	var features []interfaces.ModelFeature
	for _, featureConfiguration := range featureConfigurations {
		fieldsValues, err := e.getFieldsValues(featureConfiguration.Fields, featureFieldValueMap)
		if err != nil {
			return nil, fmt.Errorf("error getting fields values [%v] due to the error %v", featureConfiguration.Fields, err)
		}
		modelFeature := &interfaces.ModelFeature{
			Configuration: &featureConfiguration,
			Values:        fieldsValues,
		}
		transformed, err := modelfeature.Transform(modelFeature)
		if err != nil {
			return nil, fmt.Errorf("error transform the modelFeature Configuration [%+v] and Values [%+v] due to the error %+v", *modelFeature.Configuration, modelFeature.Values, err)
		}
		features = append(features, *transformed)
	}

	return features, nil
}

// getFieldsValues retrieves values for each field from the feature field value map.
func (e *BloomFilterModelEvaluator) getFieldsValues(fields []string, valueMap map[string][]string) ([]string, error) {
	var fieldsValues []string
	for _, field := range fields {
		fieldValues, exists := valueMap[field]
		if !exists {
			return nil, fmt.Errorf("field [%v] does not exist in valueMap [%v]", field, valueMap)
		}
		fieldsValues = append(fieldsValues, fieldValues...)
	}
	return fieldsValues, nil
}

// getDefaultValue returns the default score for a given model type.
func (e *BloomFilterModelEvaluator) getDefaultValue(modelType interfaces.ModelType) float32 {
	defaultValue, exists := modelfeature.ModelTypeDefaultValue[modelType]
	if !exists {
		return 1.0
	}
	return defaultValue
}
