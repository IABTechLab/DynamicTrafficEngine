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

	suite.EqualError(err, "error getting modelFeatures: error getting fields values [[$.device.devicetype]] due to the error field [$.device.devicetype] does not exist in valueMap [map[$.app: $.app.publisher.id: $.device.geo.country:USA $.id:e0371864-238f-41b1-a544-59b4b6a602ec $.imp[0].banner.h:250 $.imp[0].banner.pos:1 $.imp[0].banner.w:970 $.imp[0].video: $.imp[0].video.h: $.imp[0].video.pos: $.imp[0].video.w: $.site.publisher.id:539014228]]", "Evaluation failed")
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
