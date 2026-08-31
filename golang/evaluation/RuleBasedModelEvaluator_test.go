// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package evaluation

import (
	"encoding/json"
	"fmt"
	"os"
	"testing"

	"github.com/stretchr/testify/mock"
	"github.com/stretchr/testify/suite"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
)

func TestRuleBasedModelEvaluatorSuite(t *testing.T) {
	suite.Run(t, new(RuleBasedModelEvaluatorSuite))
}

type RuleBasedModelEvaluatorSuite struct {
	suite.Suite
	mockModelResultHandler       *mockInterfaces.ModelResultHandlerInterface
	mockModelConfigHandler       *mockInterfaces.ModelConfigurationHandlerInterface
	mockModelEvaluator           *mockInterfaces.ModelEvaluator
	mockTrafficAllocationContext *mockInterfaces.TrafficAllocationContextInterface
	evaluator                    *RuleBasedModelEvaluator
	testDataDir                  string
	openRtbRequest               string
	modelConfiguration           interfaces.ModelConfiguration
}

func (suite *RuleBasedModelEvaluatorSuite) SetupSuite() {
	suite.mockModelConfigHandler = mockInterfaces.NewModelConfigurationHandlerInterface(suite.T())
	suite.mockModelResultHandler = mockInterfaces.NewModelResultHandlerInterface(suite.T())

	suite.evaluator = NewRuleBasedModelEvaluator(suite.mockModelResultHandler)

	dir, err := os.Getwd()
	suite.NoError(err, "Failed to get current working directory")
	suite.testDataDir = dir + "/../testdata"
	requestTestDataFilePath := suite.testDataDir + "/request.txt"
	requestTestData, dataErr := os.ReadFile(requestTestDataFilePath)
	suite.NoError(dataErr, "Failed to read request test data file")
	suite.openRtbRequest = string(requestTestData)
	modelConfigurationTestDataFilePath := suite.testDataDir + "/ssp/configuration/model/config.json"
	modelConfigurationData, modelConfigurationDataErr := os.ReadFile(modelConfigurationTestDataFilePath)
	suite.NoError(modelConfigurationDataErr, "Failed to read model configuration test data file")
	jsonErr := json.Unmarshal(modelConfigurationData, &suite.modelConfiguration)
	suite.NoError(jsonErr, "Failed to unmarshal model configuration test data")
}

func (suite *RuleBasedModelEvaluatorSuite) TestEvaluate_Success() {
	modelDefinition, exists := suite.modelConfiguration.ModelDefinitionByIdentifier[ModelIdentifierV2]
	suite.True(exists, "Model definition not found")
	input := interfaces.ModelEvaluatorInput{
		Context:              interfaces.NewContext(),
		ModelDefinition:      &modelDefinition,
		FeatureFieldValueMap: CompleteFieldValueMap,
	}

	modelResult := interfaces.ModelResult{
		Value: ModelResultValue,
		Key:   "modelResultKey",
	}
	suite.mockModelResultHandler.EXPECT().
		Provide(modelDefinition.Identifier, mock.Anything, mock.Anything).
		Return(&modelResult, nil).
		Once()

	actualOutput, err := suite.evaluator.Evaluate(input)

	suite.NoError(err, "Evaluation failed")
	suite.Equal(interfaces.ModelEvaluationStatusSuccess, actualOutput.Status, "Unexpected evaluation status")
	suite.Equal(modelResult, actualOutput.ModelResult, "Unexpected model result")
}

func (suite *RuleBasedModelEvaluatorSuite) TestEvaluate_ReturnError_NoMatchedFeatureFieldValue() {
	modelDefinition, exists := suite.modelConfiguration.ModelDefinitionByIdentifier[ModelIdentifierV2]
	suite.True(exists, "Model definition not found")
	input := interfaces.ModelEvaluatorInput{
		Context:              interfaces.NewContext(),
		ModelDefinition:      &modelDefinition,
		FeatureFieldValueMap: IncompleteFieldValueMap,
	}

	actualOutput, err := suite.evaluator.Evaluate(input)

	suite.EqualError(err, "error getting modelFeatures: error getting fields values [[$.device.devicetype]] due to the error field [$.device.devicetype] does not exist in valueMap [map[$.app:[] $.app.publisher.id:[] $.device.geo.country:[USA] $.id:[e0371864-238f-41b1-a544-59b4b6a602ec] $.imp[0].banner.h:[250] $.imp[0].banner.pos:[1] $.imp[0].banner.w:[970] $.imp[0].video:[] $.imp[0].video.h:[] $.imp[0].video.pos:[] $.imp[0].video.w:[] $.site.publisher.id:[539014228]]]", "Evaluation failed")
	suite.Equal(interfaces.ModelEvaluationStatusError, actualOutput.Status, "Unexpected evaluation status")
}

func (suite *RuleBasedModelEvaluatorSuite) TestEvaluate_ReturnError_ModelResultHandlerProvideError() {
	modelDefinition, exists := suite.modelConfiguration.ModelDefinitionByIdentifier[ModelIdentifierV2]
	suite.True(exists, "Model definition not found")
	input := interfaces.ModelEvaluatorInput{
		Context:              interfaces.NewContext(),
		ModelDefinition:      &modelDefinition,
		FeatureFieldValueMap: CompleteFieldValueMap,
	}

	suite.mockModelResultHandler.EXPECT().
		Provide(modelDefinition.Identifier, mock.Anything, mock.Anything).
		Return(nil, fmt.Errorf("ModelResultHandlerProvideError")).
		Once()

	actualOutput, err := suite.evaluator.Evaluate(input)

	suite.EqualError(err, "error getting modelResult: ModelResultHandlerProvideError")
	suite.Equal(interfaces.ModelEvaluationStatusError, actualOutput.Status, "Unexpected evaluation status")
}

func (suite *RuleBasedModelEvaluatorSuite) TestEvaluate_DefaultValueDerivedFromModelType() {
	tests := []struct {
		name            string
		modelType       interfaces.ModelType
		expectedDefault float32
	}{
		{
			name:            "HighValue model uses 0.0 as default",
			modelType:       "HighValue",
			expectedDefault: 0.0,
		},
		{
			name:            "LowValue model uses 1.0 as default",
			modelType:       "LowValue",
			expectedDefault: 1.0,
		},
		{
			name:            "empty string model type uses 1.0 as default",
			modelType:       "",
			expectedDefault: 1.0,
		},
		{
			name:            "unrecognized model type uses 1.0 as default",
			modelType:       "SomethingElse",
			expectedDefault: 1.0,
		},
	}

	for _, tt := range tests {
		suite.Run(tt.name, func() {
			modelDefinition := interfaces.ModelDefinition{
				Identifier: "test_model_v1",
				Type:       tt.modelType,
				Features: []interfaces.FeatureConfiguration{
					{
						Name:            "testFeature",
						Fields:          []string{"$.site.publisher.id"},
						Transformations: []interfaces.TransformerName{"GetFirstNotEmpty"},
					},
				},
			}
			input := interfaces.ModelEvaluatorInput{
				Context:         interfaces.NewContext(),
				ModelDefinition: &modelDefinition,
				FeatureFieldValueMap: map[string][]string{
					"$.site.publisher.id": {"539014228"},
				},
			}

			// The mock returns a ModelResult whose Value equals the defaultValue passed in.
			// This lets us verify the correct default was derived from the model type.
			suite.mockModelResultHandler.EXPECT().
				Provide(modelDefinition.Identifier, mock.Anything, tt.expectedDefault).
				Return(&interfaces.ModelResult{
					Value:  tt.expectedDefault,
					Key:    "",
					Keys:   []string{""},
					Values: []float32{tt.expectedDefault},
				}, nil).
				Once()

			output, err := suite.evaluator.Evaluate(input)

			suite.NoError(err)
			suite.Equal(interfaces.ModelEvaluationStatusSuccess, output.Status)
			suite.Equal(tt.expectedDefault, output.ModelResult.Value)
		})
	}
}

func (suite *RuleBasedModelEvaluatorSuite) TestGetDefaultValue() {
	tests := []struct {
		name          string
		modelType     interfaces.ModelType
		expectedValue float32
	}{
		{
			name:          "HighValue returns 0.0",
			modelType:     "HighValue",
			expectedValue: 0.0,
		},
		{
			name:          "LowValue returns 1.0",
			modelType:     "LowValue",
			expectedValue: 1.0,
		},
		{
			name:          "empty string returns 1.0",
			modelType:     "",
			expectedValue: 1.0,
		},
		{
			name:          "unrecognized type returns 1.0",
			modelType:     "UnknownType",
			expectedValue: 1.0,
		},
	}

	for _, tt := range tests {
		suite.Run(tt.name, func() {
			result := suite.evaluator.getDefaultValue(tt.modelType)
			suite.Equal(tt.expectedValue, result)
		})
	}
}

func (suite *RuleBasedModelEvaluatorSuite) TestGetFeature_ReturnError_UnknownTransformer() {
	modelDefinition, exists := suite.modelConfiguration.ModelDefinitionByIdentifier[ModelIdentifierV2]
	suite.True(exists, "Model definition not found")
	modelDefinition.Features[0].Transformations[0] = "unknownTransformer"
	input := interfaces.ModelEvaluatorInput{
		Context:              interfaces.NewContext(),
		ModelDefinition:      &modelDefinition,
		FeatureFieldValueMap: CompleteFieldValueMap,
	}

	actualModelFeature, err := suite.evaluator.getFeatures(input)

	suite.EqualError(err, "error transform the modelFeature Configuration [{Name:isMobile Fields:[$.app] Transformations:[unknownTransformer ApplyMappings] Mapping:map[0:site 1:app] MappingDefaultValue:}] and Values [[]] due to the error transformer [unknownTransformer] not found")
	suite.Nil(actualModelFeature, "Unexpected model result")
}

func (suite *RuleBasedModelEvaluatorSuite) TestEvaluate_BackwardCompatibility_LowValueSingleValueFeatures() {
	// Verifies that existing LowValue model behavior with single-value features
	// is unchanged after the multi-value refactoring. This confirms that SSP
	// integrations using single-value fields continue to produce identical results.

	tests := []struct {
		name            string
		modelType       interfaces.ModelType
		features        []interfaces.FeatureConfiguration
		fieldValueMap   map[string][]string
		expectedKey     string
		expectedDefault float32
	}{
		{
			name:      "LowValue model with single-value features produces correct single key",
			modelType: "LowValue",
			features: []interfaces.FeatureConfiguration{
				{
					Name:            "publisherId",
					Fields:          []string{"$.site.publisher.id", "$.app.publisher.id"},
					Transformations: []interfaces.TransformerName{"GetFirstNotEmpty"},
				},
				{
					Name:            "country",
					Fields:          []string{"$.device.geo.country"},
					Transformations: []interfaces.TransformerName{},
				},
			},
			fieldValueMap: map[string][]string{
				"$.site.publisher.id":  {"539014228"},
				"$.app.publisher.id":   {},
				"$.device.geo.country": {"USA"},
			},
			expectedKey:     "539014228|USA",
			expectedDefault: 1.0,
		},
		{
			name:      "empty model type defaults to LowValue behavior with 1.0 default",
			modelType: "",
			features: []interfaces.FeatureConfiguration{
				{
					Name:            "publisherId",
					Fields:          []string{"$.site.publisher.id"},
					Transformations: []interfaces.TransformerName{},
				},
			},
			fieldValueMap: map[string][]string{
				"$.site.publisher.id": {"539014228"},
			},
			expectedKey:     "539014228",
			expectedDefault: 1.0,
		},
		{
			name:      "LowValue model using full feature pipeline with transformers",
			modelType: "LowValue",
			features: []interfaces.FeatureConfiguration{
				{
					Name:            "isMobile",
					Fields:          []string{"$.app"},
					Transformations: []interfaces.TransformerName{"Exists", "ApplyMappings"},
					Mapping:         map[string]string{"0": "site", "1": "app"},
				},
				{
					Name:            "publisherId",
					Fields:          []string{"$.site.publisher.id", "$.app.publisher.id"},
					Transformations: []interfaces.TransformerName{"GetFirstNotEmpty"},
				},
				{
					Name:            "country",
					Fields:          []string{"$.device.geo.country"},
					Transformations: []interfaces.TransformerName{},
				},
			},
			fieldValueMap: map[string][]string{
				"$.app":                {"com.example.app"},
				"$.site.publisher.id":  {"539014228"},
				"$.app.publisher.id":   {""},
				"$.device.geo.country": {"USA"},
			},
			expectedKey:     "app|539014228|USA",
			expectedDefault: 1.0,
		},
	}

	for _, tt := range tests {
		suite.Run(tt.name, func() {
			modelDefinition := interfaces.ModelDefinition{
				Identifier: "adsp_low-value_v2",
				Type:       tt.modelType,
				Features:   tt.features,
			}
			input := interfaces.ModelEvaluatorInput{
				Context:              interfaces.NewContext(),
				ModelDefinition:      &modelDefinition,
				FeatureFieldValueMap: tt.fieldValueMap,
			}

			// Mock Provide: verify 1.0 is passed as defaultValue (LowValue behavior),
			// and return a ModelResult with the expected single key.
			suite.mockModelResultHandler.EXPECT().
				Provide(modelDefinition.Identifier, mock.MatchedBy(func(features []interfaces.ModelFeature) bool {
					// All features should have exactly one value (single-value pipeline)
					for _, f := range features {
						if len(f.Values) != 1 {
							return false
						}
					}
					return true
				}), tt.expectedDefault).
				Return(&interfaces.ModelResult{
					Value:  tt.expectedDefault,
					Key:    tt.expectedKey,
					Keys:   []string{tt.expectedKey},
					Values: []float32{tt.expectedDefault},
				}, nil).
				Once()

			output, err := suite.evaluator.Evaluate(input)

			suite.NoError(err)
			suite.Equal(interfaces.ModelEvaluationStatusSuccess, output.Status)
			// Verify Key matches the expected single key (BuildKey-compatible output)
			suite.Equal(tt.expectedKey, output.ModelResult.Key)
			// Verify Keys slice has exactly one entry (single-value pipeline)
			suite.Equal([]string{tt.expectedKey}, output.ModelResult.Keys)
			// Verify Values slice has exactly one entry matching defaultValue
			suite.Equal([]float32{tt.expectedDefault}, output.ModelResult.Values)
			// Verify backward-compatible Value field
			suite.Equal(tt.expectedDefault, output.ModelResult.Value)
		})
	}
}

func (suite *RuleBasedModelEvaluatorSuite) TestGetFieldsValues_MultiValueSlices() {
	tests := []struct {
		name           string
		fields         []string
		valueMap       map[string][]string
		expectedValues []string
		expectError    bool
		errorContains  string
	}{
		{
			name:   "single field with multiple values returns all values",
			fields: []string{"$.imp[0].pmp.deals[*].id"},
			valueMap: map[string][]string{
				"$.imp[0].pmp.deals[*].id": {"deal1", "deal2", "deal3"},
			},
			expectedValues: []string{"deal1", "deal2", "deal3"},
			expectError:    false,
		},
		{
			name:   "single field with single value returns that value in a slice",
			fields: []string{"$.site.publisher.id"},
			valueMap: map[string][]string{
				"$.site.publisher.id": {"539014228"},
			},
			expectedValues: []string{"539014228"},
			expectError:    false,
		},
		{
			name:   "multiple fields concatenated in declaration order",
			fields: []string{"$.site.publisher.id", "$.app.publisher.id"},
			valueMap: map[string][]string{
				"$.site.publisher.id": {"pub1", "pub2"},
				"$.app.publisher.id":  {"appPub1"},
			},
			expectedValues: []string{"pub1", "pub2", "appPub1"},
			expectError:    false,
		},
		{
			name:   "missing field returns error",
			fields: []string{"$.site.publisher.id", "$.nonexistent.field"},
			valueMap: map[string][]string{
				"$.site.publisher.id": {"pub1"},
			},
			expectError:   true,
			errorContains: "$.nonexistent.field",
		},
	}

	for _, tt := range tests {
		suite.Run(tt.name, func() {
			result, err := suite.evaluator.getFieldsValues(tt.fields, tt.valueMap)
			if tt.expectError {
				suite.Error(err)
				suite.Contains(err.Error(), tt.errorContains)
				suite.Nil(result)
			} else {
				suite.NoError(err)
				suite.Equal(tt.expectedValues, result)
			}
		})
	}
}
