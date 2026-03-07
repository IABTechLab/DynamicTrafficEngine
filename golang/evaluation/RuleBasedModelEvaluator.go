// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package evaluation

import (
	"fmt"

	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/modelfeature"
)

const HighValueDefaultScore = 1.0

// RuleBasedModelEvaluator provides a filter recommendation for OpenRTB requests for a rules-based model.
//
// A rules-based model take in a set of features derived from the OpenRTB request, and genereate a string tuple
// based on the model configuration. The tuple is used to fetch the value metric calculated by the model, which
// is then used to make a filter recommendation.
type RuleBasedModelEvaluator struct {
	modelResultHandler interfaces.ModelResultHandlerInterface
}

func NewRuleBasedModelEvaluator(modelResultHandler interfaces.ModelResultHandlerInterface) *RuleBasedModelEvaluator {
	return &RuleBasedModelEvaluator{
		modelResultHandler: modelResultHandler,
	}
}

func (t *RuleBasedModelEvaluator) Evaluate(input interfaces.ModelEvaluatorInput) (*interfaces.ModelEvaluatorOutput, error) {
	modelDefinition := input.ModelDefinition
	modelFeatures, err := t.getFeatures(input)
	if err != nil {
		return &interfaces.ModelEvaluatorOutput{
			Status: interfaces.ModelEvaluationStatusError,
		}, fmt.Errorf("error getting modelFeatures: %w", err)
	}
	Logger.Debug().Msgf("modelFeatures: %+v", modelFeatures)
	modelResult, err := t.modelResultHandler.Provide(modelDefinition.Identifier, modelFeatures, HighValueDefaultScore)
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

func (t *RuleBasedModelEvaluator) getFeatures(input interfaces.ModelEvaluatorInput) ([]interfaces.ModelFeature, error) {
	modelDefinition := input.ModelDefinition
	featureConfigurations := modelDefinition.Features
	Logger.Debug().Msgf("featureConfigurations: %+v", featureConfigurations)
	featureFieldValueMap := input.FeatureFieldValueMap
	var features []interfaces.ModelFeature
	for _, featureConfiguration := range featureConfigurations {
		fieldsValues, err := t.getFieldsValues(featureConfiguration.Fields, featureFieldValueMap)
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

func (t *RuleBasedModelEvaluator) getFieldsValues(fields []string, valueMap map[string]string) ([]string, error) {
	var fieldsValues []string
	for _, field := range fields {
		fieldValue, exists := valueMap[field]
		if !exists {
			return nil, fmt.Errorf("field [%v] does not exist in valueMap [%v]", field, valueMap)
		}
		fieldsValues = append(fieldsValues, fieldValue)
	}
	return fieldsValues, nil
}
