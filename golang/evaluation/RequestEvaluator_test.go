// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package evaluation

import (
	"encoding/json"
	"fmt"
	"os"
	"reflect"
	"testing"

	"github.com/stretchr/testify/mock"
	"github.com/stretchr/testify/suite"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/modelfeature"
	"golang.a2z.com/demanddriventrafficevaluator/repository"
)

const (
	ModelResultValue       = float32(0.0)
	ExperimentName         = "DemandDrivenTrafficEvaluatorSoftFilter"
	ModelIdentifierV1      = "adsp_low-value_v1"
	ModelIdentifierV2      = "adsp_low-value_v2"
	TreatmentCodeInIntZero = 0
	TreatmentCodeInIntOne  = 1
	TreatmentT             = "T"
	TreatmentC             = "C"
)

func TestRequestEvaluatorSuite(t *testing.T) {
	suite.Run(t, new(RequestEvaluatorTestSuite))
}

var (
	AllUniqueFeatureFields = []string{
		"$.id",
		"$.app",
		"$.imp[0].video",
		"$.site.publisher.id",
		"$.app.publisher.id",
		"$.device.geo.country",
		"$.imp[0].video.w",
		"$.imp[0].video.h",
		"$.imp[0].banner.w",
		"$.imp[0].banner.h",
		"$.imp[0].video.pos",
		"$.imp[0].banner.pos",
		"$.device.devicetype",
	}
	CompleteFieldValueMap = map[string][]string{
		"$.site.publisher.id":  {"539014228"},
		"$.imp[0].banner.w":    {"970"},
		"$.device.geo.country": {"USA"},
		"$.app":                {},
		"$.imp[0].video.h":     {},
		"$.imp[0].video.pos":   {},
		"$.id":                 {"e0371864-238f-41b1-a544-59b4b6a602ec"},
		"$.imp[0].banner.h":    {"250"},
		"$.imp[0].banner.pos":  {"1"},
		"$.device.devicetype":  {"2"},
		"$.imp[0].video":       {},
		"$.app.publisher.id":   {},
		"$.imp[0].video.w":     {},
	}
	IncompleteFieldValueMap = map[string][]string{
		"$.site.publisher.id":  {"539014228"},
		"$.imp[0].banner.w":    {"970"},
		"$.device.geo.country": {"USA"},
		"$.app":                {},
		"$.imp[0].video.h":     {},
		"$.imp[0].video.pos":   {},
		"$.id":                 {"e0371864-238f-41b1-a544-59b4b6a602ec"},
		"$.imp[0].banner.h":    {"250"},
		"$.imp[0].banner.pos":  {"1"},
		"$.imp[0].video":       {},
		"$.app.publisher.id":   {},
		"$.imp[0].video.w":     {},
	}
)

type RequestEvaluatorTestSuite struct {
	suite.Suite
	mockTrafficAllocator         *mockInterfaces.TrafficAllocatorInterface
	mockModelConfigHandler       *mockInterfaces.ModelConfigurationHandlerInterface
	mockModelEvaluator           *mockInterfaces.ModelEvaluator
	mockTrafficAllocationContext *mockInterfaces.TrafficAllocationContextInterface
	evaluator                    *RequestEvaluator
	testDataDir                  string
	requestInput                 BidRequestEvaluatorInput
	modelConfiguration           interfaces.ModelConfiguration
}

func (suite *RequestEvaluatorTestSuite) SetupTest() {
	suite.mockTrafficAllocator = mockInterfaces.NewTrafficAllocatorInterface(suite.T())
	suite.mockModelConfigHandler = mockInterfaces.NewModelConfigurationHandlerInterface(suite.T())
	suite.mockModelEvaluator = mockInterfaces.NewModelEvaluator(suite.T())
	suite.mockTrafficAllocationContext = mockInterfaces.NewTrafficAllocationContextInterface(suite.T())

	suite.evaluator = NewRequestEvaluator(
		"ssp",
		suite.mockTrafficAllocator,
		suite.mockModelEvaluator,
		suite.mockModelConfigHandler,
		NewConfigurableAggregator(),
	)

	dir, err := os.Getwd()
	suite.NoError(err, "Failed to get current working directory")
	suite.testDataDir = dir + "/../testdata"
	requestTestDataFilePath := suite.testDataDir + "/request.txt"
	requestTestData, dataErr := os.ReadFile(requestTestDataFilePath)
	suite.NoError(dataErr, "Failed to read request test data file")
	suite.requestInput = BidRequestEvaluatorInput{string(requestTestData), nil}
	modelConfigurationTestDataFilePath := suite.testDataDir + "/ssp/configuration/model/config.json"
	modelConfigurationData, modelConfigurationDataErr := os.ReadFile(modelConfigurationTestDataFilePath)
	suite.NoError(modelConfigurationDataErr, "Failed to read model configuration test data file")
	jsonErr := json.Unmarshal(modelConfigurationData, &suite.modelConfiguration)
	suite.NoError(jsonErr, "Failed to unmarshal model configuration test data")
}

func (suite *RequestEvaluatorTestSuite) TestEvaluate_Success() {
	suite.mockTrafficAllocationContext.EXPECT().
		GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter).
		Return(&interfaces.ExperimentDefinition{
			Name: ExperimentName,
			Type: modelfeature.ExperimentTypeSoftFilter,
		},
			nil,
		).
		Times(2)
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelIdentifiers().
		Return([]string{ModelIdentifierV2}).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelsByExperiment().
		Return(map[string][]string{ExperimentName: {ModelIdentifierV2}}).
		Once()
	suite.mockTrafficAllocationContext.
		EXPECT().
		GetTreatmentCodeInInt(ExperimentName).
		Return(TreatmentCodeInIntZero).
		Once()
	suite.mockTrafficAllocationContext.
		EXPECT().
		GetTreatmentCode(ExperimentName).
		Return(TreatmentT).
		Once()
	suite.mockTrafficAllocator.EXPECT().
		GetTrafficAllocationContext().
		Return(suite.mockTrafficAllocationContext).
		Once()

	suite.mockModelConfigHandler.EXPECT().
		Provide().
		Return(&suite.modelConfiguration, nil).
		Once()
	suite.mockModelConfigHandler.EXPECT().
		GetAllUniqueFeatureFields().
		Return(AllUniqueFeatureFields, nil).
		Once()

	modelDefinition := suite.modelConfiguration.ModelDefinitionByIdentifier[ModelIdentifierV2]
	suite.mockModelEvaluator.EXPECT().
		Evaluate(mock.MatchedBy(func(input interfaces.ModelEvaluatorInput) bool {
			return input.Context != nil &&
				input.OpenRtbRequest == suite.requestInput.OpenRtbRequest &&
				reflect.DeepEqual(*input.ModelDefinition, modelDefinition) &&
				reflect.DeepEqual(input.FeatureFieldValueMap, CompleteFieldValueMap)
		})).
		Return(&interfaces.ModelEvaluatorOutput{
			Context: interfaces.Context{},
			Status:  interfaces.ModelEvaluationStatusSuccess,
			ModelResult: interfaces.ModelResult{
				Value: ModelResultValue,
				Key:   "modelResultKey",
			},
			ModelDefinition: modelDefinition,
			ModelFeatures:   []interfaces.ModelFeature{{}},
		},
			nil).
		Once()

	result := suite.evaluator.Evaluate(&suite.requestInput)

	suite.NotNil(result)
	suite.Equal(1, len(result.Response.Slots), "Slots size should be 1.")
	suite.Equal(ModelResultValue, result.Response.Slots[TreatmentCodeInIntZero].FilterDecision, "FilterDecision should be 0.0")
	suite.Equal(`{"amazontest":{"decision":0}}`, result.Response.Slots[TreatmentCodeInIntZero].Ext, "The slot Ext should be `{\"amazontest\":{\"decision\":0}}`")
	suite.Equal(`{"amazontest":{"learning":0}}`, result.Response.Ext, "The response Ext should be `{\"amazontest\":{\"learning\":0}}`")
}

func (suite *RequestEvaluatorTestSuite) TestEvaluateMap_Success() {
	suite.requestInput = BidRequestEvaluatorInput{"", CompleteFieldValueMap}

	suite.mockTrafficAllocationContext.EXPECT().
		GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter).
		Return(&interfaces.ExperimentDefinition{
			Name: ExperimentName,
			Type: modelfeature.ExperimentTypeSoftFilter,
		},
			nil,
		).
		Times(2)
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelIdentifiers().
		Return([]string{ModelIdentifierV2}).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelsByExperiment().
		Return(map[string][]string{ExperimentName: {ModelIdentifierV2}}).
		Once()
	suite.mockTrafficAllocationContext.
		EXPECT().
		GetTreatmentCodeInInt(ExperimentName).
		Return(TreatmentCodeInIntZero).
		Once()
	suite.mockTrafficAllocationContext.
		EXPECT().
		GetTreatmentCode(ExperimentName).
		Return(TreatmentT).
		Once()
	suite.mockTrafficAllocator.EXPECT().
		GetTrafficAllocationContext().
		Return(suite.mockTrafficAllocationContext).
		Once()

	suite.mockModelConfigHandler.EXPECT().
		Provide().
		Return(&suite.modelConfiguration, nil).
		Once()
	suite.mockModelConfigHandler.EXPECT().
		GetAllUniqueFeatureFields().
		Return(AllUniqueFeatureFields, nil).
		Once()

	modelDefinition := suite.modelConfiguration.ModelDefinitionByIdentifier[ModelIdentifierV2]
	suite.mockModelEvaluator.EXPECT().
		Evaluate(mock.MatchedBy(func(input interfaces.ModelEvaluatorInput) bool {
			return input.Context != nil &&
				input.OpenRtbRequest == suite.requestInput.OpenRtbRequest &&
				reflect.DeepEqual(*input.ModelDefinition, modelDefinition) &&
				reflect.DeepEqual(input.FeatureFieldValueMap, CompleteFieldValueMap)
		})).
		Return(&interfaces.ModelEvaluatorOutput{
			Context: interfaces.Context{},
			Status:  interfaces.ModelEvaluationStatusSuccess,
			ModelResult: interfaces.ModelResult{
				Value: ModelResultValue,
				Key:   "modelResultKey",
			},
			ModelDefinition: modelDefinition,
			ModelFeatures:   []interfaces.ModelFeature{{}},
		},
			nil).
		Once()

	result := suite.evaluator.Evaluate(&suite.requestInput)

	suite.NotNil(result)
	suite.Equal(1, len(result.Response.Slots), "Slots size should be 1.")
	suite.Equal(ModelResultValue, result.Response.Slots[TreatmentCodeInIntZero].FilterDecision, "FilterDecision should be 0.0")
	suite.Equal(`{"amazontest":{"decision":0}}`, result.Response.Slots[TreatmentCodeInIntZero].Ext, "The slot Ext should be `{\"amazontest\":{\"decision\":0}}`")
	suite.Equal(`{"amazontest":{"learning":0}}`, result.Response.Ext, "The response Ext should be `{\"amazontest\":{\"learning\":0}}`")
}

func (suite *RequestEvaluatorTestSuite) TestEvaluate_ReturnDefaultResponse_GetTrafficAllocationContextPanic() {
	suite.mockTrafficAllocator.On("GetTrafficAllocationContext").
		Panic("GetTrafficAllocationContext panics").
		Once()

	result := suite.evaluator.Evaluate(&suite.requestInput)

	suite.NotNil(result)
	suite.Equal(DefaultResponse, result.Response)
}

func (suite *RequestEvaluatorTestSuite) TestEvaluate_ReturnDefaultResponse_ParseError() {
	suite.mockTrafficAllocator.EXPECT().
		GetTrafficAllocationContext().
		Return(suite.mockTrafficAllocationContext).
		Once()
	suite.mockModelConfigHandler.EXPECT().
		GetAllUniqueFeatureFields().
		Return(
			nil,
			fmt.Errorf("error getting ModelDefinition from local cache [Configuration] with Key [ModelConfiguration]: error getting Config from local cache [Configuration] with Key [Model]"),
		).
		Once()

	result := suite.evaluator.Evaluate(&suite.requestInput)

	suite.NotNil(result)
	suite.Equal(DefaultResponse, result.Response)
}

func (suite *RequestEvaluatorTestSuite) TestEvaluate_ReturnDefaultResponse_GetModelDefinitionsError() {
	suite.mockTrafficAllocator.EXPECT().
		GetTrafficAllocationContext().
		Return(suite.mockTrafficAllocationContext).
		Once()
	suite.mockModelConfigHandler.EXPECT().
		GetAllUniqueFeatureFields().
		Return(AllUniqueFeatureFields, nil).
		Once()
	suite.mockModelConfigHandler.EXPECT().
		Provide().
		Return(
			nil,
			fmt.Errorf("error getting Config from local cache [%s] with Key [%s]", repository.CacheNameConfiguration, repository.CacheKeyModel),
		).
		Once()

	result := suite.evaluator.Evaluate(&suite.requestInput)

	suite.NotNil(result)
	suite.Equal(DefaultResponse, result.Response)
}

func (suite *RequestEvaluatorTestSuite) TestEvaluate_ReturnDefaultResponse_NoAvailableModelEvaluation() {
	suite.mockTrafficAllocator.EXPECT().
		GetTrafficAllocationContext().
		Return(suite.mockTrafficAllocationContext).
		Once()
	suite.mockModelConfigHandler.EXPECT().
		GetAllUniqueFeatureFields().
		Return(AllUniqueFeatureFields, nil).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelIdentifiers().
		Return([]string{ModelIdentifierV2}).
		Once()

	suite.mockModelConfigHandler.EXPECT().
		Provide().
		Return(&suite.modelConfiguration, nil).
		Once()
	suite.mockModelEvaluator.EXPECT().
		Evaluate(mock.Anything).
		Return(nil, fmt.Errorf("model evaluation error")).
		Once()

	result := suite.evaluator.Evaluate(&suite.requestInput)

	suite.NotNil(result)
	suite.Equal(DefaultResponse, result.Response)
}

func (suite *RequestEvaluatorTestSuite) TestEvaluate_ReturnDefaultResponse_AggregateModelEvaluationResultsOnMaxError() {
	suite.mockTrafficAllocator.EXPECT().
		GetTrafficAllocationContext().
		Return(suite.mockTrafficAllocationContext).
		Once()
	suite.mockModelConfigHandler.EXPECT().
		GetAllUniqueFeatureFields().
		Return(AllUniqueFeatureFields, nil).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelIdentifiers().
		Return([]string{ModelIdentifierV2}).
		Once()

	suite.mockModelConfigHandler.EXPECT().
		Provide().
		Return(&suite.modelConfiguration, nil).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter).
		Return(
			nil,
			fmt.Errorf("ExperimentDefinition with type [%s] not found", modelfeature.ExperimentTypeSoftFilter),
		).
		Once()
	suite.mockModelEvaluator.EXPECT().
		Evaluate(mock.Anything).
		Return(&interfaces.ModelEvaluatorOutput{}, nil).
		Once()

	result := suite.evaluator.Evaluate(&suite.requestInput)

	suite.NotNil(result)
	suite.Equal(DefaultResponse, result.Response)
}

func (suite *RequestEvaluatorTestSuite) TestParse_Success() {
	suite.mockModelConfigHandler.EXPECT().
		GetAllUniqueFeatureFields().
		Return(AllUniqueFeatureFields, nil).
		Once()

	fieldValueMap, err := suite.evaluator.parse(suite.requestInput.OpenRtbRequest, []string{"$.id"})
	suite.NoError(err, "Failed to parse request")
	suite.Equal(CompleteFieldValueMap, fieldValueMap, "Field value map should match expected value")
}

func (suite *RequestEvaluatorTestSuite) TestParse_ReturnErr_ErrInAllUniqueFeatureFields() {
	suite.mockModelConfigHandler.EXPECT().
		GetAllUniqueFeatureFields().
		Return(
			nil,
			fmt.Errorf("error getting ModelDefinition from local cache [Configuration] with Key [ModelConfiguration]: error getting Config from local cache [Configuration] with Key [Model]"),
		).
		Once()

	fieldValueMap, err := suite.evaluator.parse(suite.requestInput.OpenRtbRequest, []string{"$.id"})

	suite.Nil(fieldValueMap, "Field value map should be nil")
	suite.EqualError(err, "fail to extract openRtbRequest due to error getting ModelDefinition from local cache [Configuration] with Key [ModelConfiguration]: error getting Config from local cache [Configuration] with Key [Model]")
}

func (suite *RequestEvaluatorTestSuite) TestSetupOpenRtbRequestID_Success() {
	context := &interfaces.Context{}
	requestFieldValueMap := map[string][]string{
		"$.id": {"e0371864-238f-41b1-a544-59b4b6a602ec"},
	}

	suite.evaluator.setupOpenRtbRequestID(context, requestFieldValueMap)

	suite.Equal("e0371864-238f-41b1-a544-59b4b6a602ec", context.OpenRtbRequestId, "Request Id should be `e0371864-238f-41b1-a544-59b4b6a602ec`")
}

func (suite *RequestEvaluatorTestSuite) TestSetupOpenRtbRequestID_UnknownRequestId() {
	context := &interfaces.Context{}
	requestFieldValueMap := map[string][]string{}

	suite.evaluator.setupOpenRtbRequestID(context, requestFieldValueMap)

	suite.Equal("unknown", context.OpenRtbRequestId, "Request Id should be unknown")
}

func (suite *RequestEvaluatorTestSuite) TestGetModelDefinitions_Success() {
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelIdentifiers().
		Return([]string{ModelIdentifierV2}).
		Once()
	context := &interfaces.Context{
		TrafficAllocationContext: suite.mockTrafficAllocationContext,
	}

	suite.mockModelConfigHandler.EXPECT().
		Provide().
		Return(&suite.modelConfiguration, nil).
		Once()

	modelDefinitions, err := suite.evaluator.getModelDefinitions(context)

	suite.NoError(err, "Error should be nil")
	suite.Equal(1, len(modelDefinitions), "Model definitions size should be 1.")
	expectedModelDefinition := suite.modelConfiguration.ModelDefinitionByIdentifier[ModelIdentifierV2]
	suite.Equal([]interfaces.ModelDefinition{expectedModelDefinition}, modelDefinitions, "Model definitions should match expected value")
}

func (suite *RequestEvaluatorTestSuite) TestGetModelDefinitions_ReturnErr_NoModelConfiguration() {
	suite.mockModelConfigHandler.EXPECT().
		Provide().
		Return(
			nil,
			fmt.Errorf("error getting Config from local cache [%s] with Key [%s]", repository.CacheNameConfiguration, repository.CacheKeyModel),
		).
		Once()

	modelDefinitions, err := suite.evaluator.getModelDefinitions(&interfaces.Context{})

	suite.Nil(modelDefinitions, "Model definitions should be nil")
	suite.EqualError(err, "error while providing model configuration: error getting Config from local cache [Configuration] with Key [Model]", "Error should be nil")
}

func (suite *RequestEvaluatorTestSuite) TestGetModelDefinitions_ReturnErr_ModelInExperimentNotRegistered() {
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelIdentifiers().
		Return([]string{ModelIdentifierV1}).
		Once()
	context := &interfaces.Context{
		TrafficAllocationContext: suite.mockTrafficAllocationContext,
	}
	suite.mockModelConfigHandler.EXPECT().
		Provide().
		Return(&suite.modelConfiguration, nil).
		Once()

	modelDefinitions, err := suite.evaluator.getModelDefinitions(context)

	suite.Nil(modelDefinitions, "Model definitions should be nil")
	suite.EqualError(err, "error while finding the definition of model [adsp_low-value_v1] registered in the experiment", "Error should be nil")
}

func (suite *RequestEvaluatorTestSuite) TestAggregateModelEvaluationResultsOnMax_Success() {
	modelEvaluatorOutput := interfaces.ModelEvaluatorOutput{
		ModelResult: interfaces.ModelResult{
			Value: 0.0,
		},
		ModelDefinition: interfaces.ModelDefinition{
			Identifier: ModelIdentifierV2,
		},
		Status: interfaces.ModelEvaluationStatusSuccess,
	}
	context := &interfaces.Context{
		TrafficAllocationContext: suite.mockTrafficAllocationContext,
		ModelEvaluatorOutputs:    []interfaces.ModelEvaluatorOutput{modelEvaluatorOutput},
	}
	suite.mockTrafficAllocationContext.EXPECT().
		GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter).
		Return(
			&interfaces.ExperimentDefinition{
				Name: ExperimentName,
				Type: modelfeature.ExperimentTypeSoftFilter,
			},
			nil,
		).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelsByExperiment().
		Return(map[string][]string{ExperimentName: {ModelIdentifierV2}}).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetTreatmentCodeInInt(ExperimentName).
		Return(TreatmentCodeInIntOne).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetTreatmentCode(ExperimentName).
		Return(TreatmentC).
		Once()

	expectedAggregatedModelEvaluationResult := &interfaces.AggregatedModelEvaluationResult{
		ExperimentName:     ExperimentName,
		ExperimentType:     "soft-filter",
		TreatmentCode:      TreatmentC,
		TreatmentCodeInInt: 1,
		Score:              float32(0.0),
		ScoreWithTreatment: float32(1.0),
		AggregationType:    "max",
	}

	actualAggregatedModelEvaluationResult, err := suite.evaluator.aggregateModelEvaluationResultsOnMax(context)

	suite.NoError(err, "Error should be nil")
	suite.Equal(expectedAggregatedModelEvaluationResult, actualAggregatedModelEvaluationResult, "Aggregated model evaluation result should match expected value")
}

func (suite *RequestEvaluatorTestSuite) TestAggregateModelEvaluationResultsOnMax_ReturnErr_ExperimentDefinitionNotFound() {
	modelEvaluatorOutput := interfaces.ModelEvaluatorOutput{
		ModelResult: interfaces.ModelResult{
			Value: 0.0,
		},
		ModelDefinition: interfaces.ModelDefinition{
			Identifier: ModelIdentifierV2,
		},
		Status: interfaces.ModelEvaluationStatusSuccess,
	}
	context := &interfaces.Context{
		TrafficAllocationContext: suite.mockTrafficAllocationContext,
		ModelEvaluatorOutputs:    []interfaces.ModelEvaluatorOutput{modelEvaluatorOutput},
	}
	suite.mockTrafficAllocationContext.EXPECT().
		GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter).
		Return(
			nil,
			fmt.Errorf("ExperimentDefinition with type [%s] not found", modelfeature.ExperimentTypeSoftFilter),
		).
		Once()

	aggregatedModelEvaluationResult, err := suite.evaluator.aggregateModelEvaluationResultsOnMax(context)

	suite.Nil(aggregatedModelEvaluationResult, "Aggregated model evaluation result should be nil")
	suite.EqualError(err, "error while aggregating model evaluation results on Max due to [ExperimentDefinition with type [soft-filter] not found]", "Error should not be nil")
}

func (suite *RequestEvaluatorTestSuite) TestAggregateModelEvaluationResultsOnMax_ReturnErr_NoModelsInExperiment() {
	modelEvaluatorOutput := interfaces.ModelEvaluatorOutput{
		ModelResult: interfaces.ModelResult{
			Value: 0.0,
		},
		ModelDefinition: interfaces.ModelDefinition{
			Identifier: ModelIdentifierV2,
		},
		Status: interfaces.ModelEvaluationStatusSuccess,
	}
	context := &interfaces.Context{
		TrafficAllocationContext: suite.mockTrafficAllocationContext,
		ModelEvaluatorOutputs:    []interfaces.ModelEvaluatorOutput{modelEvaluatorOutput},
	}
	suite.mockTrafficAllocationContext.EXPECT().
		GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter).
		Return(
			&interfaces.ExperimentDefinition{
				Name: ExperimentName,
				Type: modelfeature.ExperimentTypeSoftFilter,
			},
			nil,
		).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelsByExperiment().
		Return(map[string][]string{"UndefinedExperimentName": {ModelIdentifierV2}}).
		Once()

	aggregatedModelEvaluationResult, err := suite.evaluator.aggregateModelEvaluationResultsOnMax(context)

	suite.Nil(aggregatedModelEvaluationResult, "Aggregated model evaluation result should be nil")
	suite.EqualError(err, "error while aggregating model evaluation results on Max since no models in the experiment [DemandDrivenTrafficEvaluatorSoftFilter]", "Error should not be nil")
}

func (suite *RequestEvaluatorTestSuite) TestAggregateModelEvaluationResultsOnMax_ReturnErr_NoSuccessModelsEvaluation() {
	modelEvaluatorOutput := interfaces.ModelEvaluatorOutput{
		ModelResult: interfaces.ModelResult{
			Value: 0.0,
		},
		ModelDefinition: interfaces.ModelDefinition{
			Identifier: ModelIdentifierV2,
		},
		Status: interfaces.ModelEvaluationStatusError,
	}
	context := &interfaces.Context{
		TrafficAllocationContext: suite.mockTrafficAllocationContext,
		ModelEvaluatorOutputs:    []interfaces.ModelEvaluatorOutput{modelEvaluatorOutput},
	}
	suite.mockTrafficAllocationContext.EXPECT().
		GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter).
		Return(
			&interfaces.ExperimentDefinition{
				Name: ExperimentName,
				Type: modelfeature.ExperimentTypeSoftFilter,
			},
			nil,
		).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelsByExperiment().
		Return(map[string][]string{ExperimentName: {ModelIdentifierV2}}).
		Once()

	aggregatedModelEvaluationResult, err := suite.evaluator.aggregateModelEvaluationResultsOnMax(context)

	suite.Nil(aggregatedModelEvaluationResult, "Aggregated model evaluation result should be nil")
	suite.EqualError(err, "no models have been evaluated for the experiment [DemandDrivenTrafficEvaluatorSoftFilter]", "Error should not be nil")
}

func (suite *RequestEvaluatorTestSuite) TestAggregateModelEvaluationResultsOnMax_ReturnErr_NoEvaluationOfModelsInExperiment() {
	modelEvaluatorOutput := interfaces.ModelEvaluatorOutput{
		ModelResult: interfaces.ModelResult{
			Value: 0.0,
		},
		ModelDefinition: interfaces.ModelDefinition{
			Identifier: ModelIdentifierV1,
		},
		Status: interfaces.ModelEvaluationStatusError,
	}
	context := &interfaces.Context{
		TrafficAllocationContext: suite.mockTrafficAllocationContext,
		ModelEvaluatorOutputs:    []interfaces.ModelEvaluatorOutput{modelEvaluatorOutput},
	}
	suite.mockTrafficAllocationContext.EXPECT().
		GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter).
		Return(
			&interfaces.ExperimentDefinition{
				Name: ExperimentName,
				Type: modelfeature.ExperimentTypeSoftFilter,
			},
			nil,
		).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelsByExperiment().
		Return(map[string][]string{ExperimentName: {ModelIdentifierV2}}).
		Once()

	aggregatedModelEvaluationResult, err := suite.evaluator.aggregateModelEvaluationResultsOnMax(context)

	suite.Nil(aggregatedModelEvaluationResult, "Aggregated model evaluation result should be nil")
	suite.EqualError(err, "no models have been evaluated for the experiment [DemandDrivenTrafficEvaluatorSoftFilter]", "Error should not be nil")
}

func (suite *RequestEvaluatorTestSuite) TestAddMissingEntriesToMap() {
	tests := []struct {
		name           string
		inputMap       map[string][]string
		uniqueFields   []string
		expectedResult map[string][]string
		description    string
	}{
		{
			name: "present key is copied from input map",
			inputMap: map[string][]string{
				"$.site.publisher.id":  {"539014228"},
				"$.device.geo.country": {"USA"},
			},
			uniqueFields: []string{"$.site.publisher.id", "$.device.geo.country"},
			expectedResult: map[string][]string{
				"$.site.publisher.id":  {"539014228"},
				"$.device.geo.country": {"USA"},
			},
		},
		{
			name: "missing key gets empty slice",
			inputMap: map[string][]string{
				"$.site.publisher.id": {"539014228"},
			},
			uniqueFields: []string{"$.site.publisher.id", "$.device.geo.country"},
			expectedResult: map[string][]string{
				"$.site.publisher.id":  {"539014228"},
				"$.device.geo.country": {},
			},
		},
		{
			name: "nil slice treated as missing",
			inputMap: map[string][]string{
				"$.site.publisher.id":  {"539014228"},
				"$.device.geo.country": nil,
			},
			uniqueFields: []string{"$.site.publisher.id", "$.device.geo.country"},
			expectedResult: map[string][]string{
				"$.site.publisher.id":  {"539014228"},
				"$.device.geo.country": {},
			},
		},
		{
			name: "all fields present are copied",
			inputMap: map[string][]string{
				"$.site.publisher.id":  {"539014228"},
				"$.device.geo.country": {"USA"},
				"$.imp[0].banner.w":    {"970", "728"},
			},
			uniqueFields: []string{"$.site.publisher.id", "$.device.geo.country", "$.imp[0].banner.w"},
			expectedResult: map[string][]string{
				"$.site.publisher.id":  {"539014228"},
				"$.device.geo.country": {"USA"},
				"$.imp[0].banner.w":    {"970", "728"},
			},
		},
		{
			name:         "all fields missing get empty slices",
			inputMap:     map[string][]string{},
			uniqueFields: []string{"$.site.publisher.id", "$.device.geo.country", "$.imp[0].banner.w"},
			expectedResult: map[string][]string{
				"$.site.publisher.id":  {},
				"$.device.geo.country": {},
				"$.imp[0].banner.w":    {},
			},
		},
	}

	for _, tt := range tests {
		suite.Run(tt.name, func() {
			suite.mockModelConfigHandler.EXPECT().
				GetAllUniqueFeatureFields().
				Return(tt.uniqueFields, nil).
				Once()

			result, err := suite.evaluator.addMissingEntriesToMap(tt.inputMap)

			suite.NoError(err)
			suite.Equal(tt.expectedResult, result)
		})
	}
}

func (suite *RequestEvaluatorTestSuite) TestExtractWildcardField() {
	jsonWithDeals := `{"imp":[{"pmp":{"deals":[{"id":"deal-1","bidfloor":1.5},{"id":"deal-2","bidfloor":2.0},{"id":"deal-3","bidfloor":3.0}]}}]}`

	tests := []struct {
		name      string
		jsonData  string
		fieldPath string
		expected  []string
	}{
		{
			name:      "wildcard path extracts all deal IDs from deals array",
			jsonData:  jsonWithDeals,
			fieldPath: "$.imp[0].pmp.deals[*].id",
			expected:  []string{"deal-1", "deal-2", "deal-3"},
		},
		{
			name:      "wildcard path on empty array returns empty slice",
			jsonData:  `{"imp":[{"pmp":{"deals":[]}}]}`,
			fieldPath: "$.imp[0].pmp.deals[*].id",
			expected:  []string{},
		},
		{
			name:      "wildcard path on non-existent parent path returns empty slice",
			jsonData:  `{"imp":[{"pmp":{}}]}`,
			fieldPath: "$.imp[0].pmp.deals[*].id",
			expected:  []string{},
		},
		{
			name:      "malformed JSON returns empty slice",
			jsonData:  `{not valid json`,
			fieldPath: "$.imp[0].pmp.deals[*].id",
			expected:  []string{},
		},
	}

	for _, tt := range tests {
		suite.Run(tt.name, func() {
			result := suite.evaluator.extractWildcardField([]byte(tt.jsonData), tt.fieldPath)
			if len(tt.expected) == 0 {
				suite.Empty(result)
			} else {
				suite.Equal(tt.expected, result)
			}
		})
	}
}

func (suite *RequestEvaluatorTestSuite) TestEvaluate_UsesConfigurableAggregator_WhenAggregationSchemaIsNonNil() {
	// When AggregationSchema is configured, ConfigurableAggregator should be used
	aggregationSchema := &interfaces.AggregationNode{
		Operator: interfaces.AggregationOperatorOR,
		Conditions: []interfaces.AggregationNode{
			{ModelIdentifier: ModelIdentifierV2},
		},
	}

	suite.mockTrafficAllocator.EXPECT().
		GetTrafficAllocationContext().
		Return(suite.mockTrafficAllocationContext).
		Once()
	suite.mockModelConfigHandler.EXPECT().
		GetAllUniqueFeatureFields().
		Return(AllUniqueFeatureFields, nil).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelIdentifiers().
		Return([]string{ModelIdentifierV2}).
		Once()
	suite.mockModelConfigHandler.EXPECT().
		Provide().
		Return(&suite.modelConfiguration, nil).
		Once()

	modelDefinition := suite.modelConfiguration.ModelDefinitionByIdentifier[ModelIdentifierV2]
	suite.mockModelEvaluator.EXPECT().
		Evaluate(mock.Anything).
		Return(&interfaces.ModelEvaluatorOutput{
			Context: interfaces.Context{},
			Status:  interfaces.ModelEvaluationStatusSuccess,
			ModelResult: interfaces.ModelResult{
				Value: 0.0,
				Key:   "modelResultKey",
			},
			ModelDefinition: modelDefinition,
			ModelFeatures:   []interfaces.ModelFeature{{}},
		}, nil).
		Once()

	// First call: from Evaluate() to check AggregationSchema and determine routing
	suite.mockTrafficAllocationContext.EXPECT().
		GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter).
		Return(&interfaces.ExperimentDefinition{
			Name:              ExperimentName,
			Type:              modelfeature.ExperimentTypeSoftFilter,
			AggregationSchema: aggregationSchema,
		}, nil).
		Once()
	// Second call: from ConfigurableAggregator.Aggregate() to get experiment metadata
	suite.mockTrafficAllocationContext.EXPECT().
		GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter).
		Return(&interfaces.ExperimentDefinition{
			Name:              ExperimentName,
			Type:              modelfeature.ExperimentTypeSoftFilter,
			AggregationSchema: aggregationSchema,
		}, nil).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetTreatmentCodeInInt(ExperimentName).
		Return(TreatmentCodeInIntZero).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetTreatmentCode(ExperimentName).
		Return(TreatmentT).
		Once()

	result := suite.evaluator.Evaluate(&suite.requestInput)

	suite.NotNil(result)
	suite.Equal(1, len(result.Response.Slots), "Slots size should be 1")
	// OR node with a single child at 0.0 → aggregated score is 0.0
	suite.Equal(float32(0.0), result.Response.Slots[TreatmentCodeInIntZero].FilterDecision, "FilterDecision should be 0.0 from configurable aggregator")
	suite.Equal(`{"amazontest":{"decision":0}}`, result.Response.Slots[TreatmentCodeInIntZero].Ext)
	suite.Equal(`{"amazontest":{"learning":0}}`, result.Response.Ext)
}

func (suite *RequestEvaluatorTestSuite) TestEvaluate_UsesMaxAggregation_WhenAggregationSchemaIsNil() {
	// When AggregationSchema is nil, max-aggregation is used (existing behavior)
	suite.mockTrafficAllocator.EXPECT().
		GetTrafficAllocationContext().
		Return(suite.mockTrafficAllocationContext).
		Once()
	suite.mockModelConfigHandler.EXPECT().
		GetAllUniqueFeatureFields().
		Return(AllUniqueFeatureFields, nil).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelIdentifiers().
		Return([]string{ModelIdentifierV2}).
		Once()
	suite.mockModelConfigHandler.EXPECT().
		Provide().
		Return(&suite.modelConfiguration, nil).
		Once()

	modelDefinition := suite.modelConfiguration.ModelDefinitionByIdentifier[ModelIdentifierV2]
	suite.mockModelEvaluator.EXPECT().
		Evaluate(mock.Anything).
		Return(&interfaces.ModelEvaluatorOutput{
			Context: interfaces.Context{},
			Status:  interfaces.ModelEvaluationStatusSuccess,
			ModelResult: interfaces.ModelResult{
				Value: 0.0,
				Key:   "modelResultKey",
			},
			ModelDefinition: modelDefinition,
			ModelFeatures:   []interfaces.ModelFeature{{}},
		}, nil).
		Once()

	// First call: from Evaluate() - returns nil AggregationSchema so max-aggregation is used
	suite.mockTrafficAllocationContext.EXPECT().
		GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter).
		Return(&interfaces.ExperimentDefinition{
			Name:              ExperimentName,
			Type:              modelfeature.ExperimentTypeSoftFilter,
			AggregationSchema: nil,
		}, nil).
		Once()
	// Second call: from aggregateModelEvaluationResultsOnMax
	suite.mockTrafficAllocationContext.EXPECT().
		GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter).
		Return(&interfaces.ExperimentDefinition{
			Name:              ExperimentName,
			Type:              modelfeature.ExperimentTypeSoftFilter,
			AggregationSchema: nil,
		}, nil).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelsByExperiment().
		Return(map[string][]string{ExperimentName: {ModelIdentifierV2}}).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetTreatmentCodeInInt(ExperimentName).
		Return(TreatmentCodeInIntZero).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetTreatmentCode(ExperimentName).
		Return(TreatmentT).
		Once()

	result := suite.evaluator.Evaluate(&suite.requestInput)

	suite.NotNil(result)
	suite.Equal(1, len(result.Response.Slots), "Slots size should be 1")
	suite.Equal(float32(0.0), result.Response.Slots[TreatmentCodeInIntZero].FilterDecision, "FilterDecision should be 0.0 from max-aggregation")
	suite.Equal(`{"amazontest":{"decision":0}}`, result.Response.Slots[TreatmentCodeInIntZero].Ext)
	suite.Equal(`{"amazontest":{"learning":0}}`, result.Response.Ext)
}

func (suite *RequestEvaluatorTestSuite) TestEvaluate_PanicRecovery_ReturnsDefaultResponse_WithBloomFilterModels() {
	// Panic during model evaluation still returns DefaultResponse, even when bloom filter models are involved
	suite.mockTrafficAllocator.EXPECT().
		GetTrafficAllocationContext().
		Return(suite.mockTrafficAllocationContext).
		Once()
	suite.mockModelConfigHandler.EXPECT().
		GetAllUniqueFeatureFields().
		Return(AllUniqueFeatureFields, nil).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelIdentifiers().
		Return([]string{ModelIdentifierV2}).
		Once()
	suite.mockModelConfigHandler.EXPECT().
		Provide().
		Return(&interfaces.ModelConfiguration{
			ModelDefinitionByIdentifier: map[string]interfaces.ModelDefinition{
				ModelIdentifierV2: {
					Identifier:  ModelIdentifierV2,
					ModelFormat: interfaces.ModelFormatBloomFilter,
				},
			},
		}, nil).
		Once()

	// The model evaluator panics during evaluation
	suite.mockModelEvaluator.EXPECT().
		Evaluate(mock.Anything).
		Panic("bloom filter model evaluator panics").
		Once()

	result := suite.evaluator.Evaluate(&suite.requestInput)

	suite.NotNil(result)
	suite.Equal(DefaultResponse, result.Response, "DefaultResponse should be returned after panic recovery")
}

func (suite *RequestEvaluatorTestSuite) TestParse_MultiValueExtraction() {
	tests := []struct {
		name          string
		uniqueFields  []string
		expectedField string
		expectedValue []string
	}{
		{
			name:          "scalar field wraps value in single-element slice",
			uniqueFields:  []string{"$.site.publisher.id"},
			expectedField: "$.site.publisher.id",
			expectedValue: []string{"539014228"},
		},
		{
			name:          "missing field returns empty slice",
			uniqueFields:  []string{"$.nonexistent.field"},
			expectedField: "$.nonexistent.field",
			expectedValue: []string{},
		},
	}

	for _, tt := range tests {
		suite.Run(tt.name, func() {
			suite.mockModelConfigHandler.EXPECT().
				GetAllUniqueFeatureFields().
				Return(tt.uniqueFields, nil).
				Once()

			fieldValueMap, err := suite.evaluator.parse(suite.requestInput.OpenRtbRequest, []string{})

			suite.NoError(err)
			suite.Equal(tt.expectedValue, fieldValueMap[tt.expectedField])
		})
	}
}
