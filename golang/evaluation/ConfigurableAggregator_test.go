// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package evaluation

import (
	"testing"

	"github.com/stretchr/testify/suite"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/modelfeature"
)

const (
	testExperimentName = "DemandDrivenTrafficEvaluatorSoftFilter"
	testModelA         = "model_a"
	testModelB         = "model_b"
	testModelC         = "model_c"
)

func TestConfigurableAggregatorSuite(t *testing.T) {
	suite.Run(t, new(ConfigurableAggregatorTestSuite))
}

type ConfigurableAggregatorTestSuite struct {
	suite.Suite
	aggregator                   *ConfigurableAggregator
	mockTrafficAllocationContext *mockInterfaces.TrafficAllocationContextInterface
}

func (suite *ConfigurableAggregatorTestSuite) SetupTest() {
	suite.aggregator = NewConfigurableAggregator()
	suite.mockTrafficAllocationContext = mockInterfaces.NewTrafficAllocationContextInterface(suite.T())
}

// setupMockContext configures mock expectations for the Aggregate method's context access.
func (suite *ConfigurableAggregatorTestSuite) setupMockContext(treatmentCodeInInt int8, treatmentCode string) *interfaces.Context {
	suite.mockTrafficAllocationContext.EXPECT().
		GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter).
		Return(
			&interfaces.ExperimentDefinition{
				Name: testExperimentName,
				Type: modelfeature.ExperimentTypeSoftFilter,
			},
			nil,
		).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetTreatmentCodeInInt(testExperimentName).
		Return(treatmentCodeInInt).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetTreatmentCode(testExperimentName).
		Return(treatmentCode).
		Once()

	return &interfaces.Context{
		TrafficAllocationContext: suite.mockTrafficAllocationContext,
	}
}

// Test single leaf node with hit score (0.0) — model recommends filtering
func (suite *ConfigurableAggregatorTestSuite) TestAggregate_SingleLeaf_HitScore() {
	schema := &interfaces.AggregationNode{
		ModelIdentifier: testModelA,
	}
	modelOutputs := []interfaces.ModelEvaluatorOutput{
		{
			Status:          interfaces.ModelEvaluationStatusSuccess,
			ModelDefinition: interfaces.ModelDefinition{Identifier: testModelA},
			ModelResult:     interfaces.ModelResult{Value: 0.0},
		},
	}
	context := suite.setupMockContext(0, "T")

	result, err := suite.aggregator.Aggregate(schema, modelOutputs, context)

	suite.NoError(err)
	suite.Equal(float32(0.0), result.Score)
}

// Test single leaf node with forward score (1.0) — model recommends forwarding
func (suite *ConfigurableAggregatorTestSuite) TestAggregate_SingleLeaf_ForwardScore() {
	schema := &interfaces.AggregationNode{
		ModelIdentifier: testModelA,
	}
	modelOutputs := []interfaces.ModelEvaluatorOutput{
		{
			Status:          interfaces.ModelEvaluationStatusSuccess,
			ModelDefinition: interfaces.ModelDefinition{Identifier: testModelA},
			ModelResult:     interfaces.ModelResult{Value: 1.0},
		},
	}
	context := suite.setupMockContext(0, "T")

	result, err := suite.aggregator.Aggregate(schema, modelOutputs, context)

	suite.NoError(err)
	suite.Equal(float32(1.0), result.Score)
}

// Test leaf with missing model defaults to 1.0 (forward)
func (suite *ConfigurableAggregatorTestSuite) TestAggregate_SingleLeaf_MissingModel_DefaultsToForward() {
	schema := &interfaces.AggregationNode{
		ModelIdentifier: testModelA,
	}
	// No model outputs — model_a is missing from evaluation
	modelOutputs := []interfaces.ModelEvaluatorOutput{}
	context := suite.setupMockContext(0, "T")

	result, err := suite.aggregator.Aggregate(schema, modelOutputs, context)

	suite.NoError(err)
	suite.Equal(float32(1.0), result.Score)
}

// Test flat OR: one child 0.0 → result 0.0
func (suite *ConfigurableAggregatorTestSuite) TestAggregate_FlatOR_OneChildHit_ReturnsFilter() {
	schema := &interfaces.AggregationNode{
		Operator: interfaces.AggregationOperatorOR,
		Conditions: []interfaces.AggregationNode{
			{ModelIdentifier: testModelA},
			{ModelIdentifier: testModelB},
		},
	}
	modelOutputs := []interfaces.ModelEvaluatorOutput{
		{
			Status:          interfaces.ModelEvaluationStatusSuccess,
			ModelDefinition: interfaces.ModelDefinition{Identifier: testModelA},
			ModelResult:     interfaces.ModelResult{Value: 0.0},
		},
		{
			Status:          interfaces.ModelEvaluationStatusSuccess,
			ModelDefinition: interfaces.ModelDefinition{Identifier: testModelB},
			ModelResult:     interfaces.ModelResult{Value: 1.0},
		},
	}
	context := suite.setupMockContext(0, "T")

	result, err := suite.aggregator.Aggregate(schema, modelOutputs, context)

	suite.NoError(err)
	suite.Equal(float32(0.0), result.Score)
}

// Test flat OR: all children 1.0 → result 1.0
func (suite *ConfigurableAggregatorTestSuite) TestAggregate_FlatOR_AllChildrenForward_ReturnsForward() {
	schema := &interfaces.AggregationNode{
		Operator: interfaces.AggregationOperatorOR,
		Conditions: []interfaces.AggregationNode{
			{ModelIdentifier: testModelA},
			{ModelIdentifier: testModelB},
		},
	}
	modelOutputs := []interfaces.ModelEvaluatorOutput{
		{
			Status:          interfaces.ModelEvaluationStatusSuccess,
			ModelDefinition: interfaces.ModelDefinition{Identifier: testModelA},
			ModelResult:     interfaces.ModelResult{Value: 1.0},
		},
		{
			Status:          interfaces.ModelEvaluationStatusSuccess,
			ModelDefinition: interfaces.ModelDefinition{Identifier: testModelB},
			ModelResult:     interfaces.ModelResult{Value: 1.0},
		},
	}
	context := suite.setupMockContext(0, "T")

	result, err := suite.aggregator.Aggregate(schema, modelOutputs, context)

	suite.NoError(err)
	suite.Equal(float32(1.0), result.Score)
}

// Test flat AND: all children 0.0 → result 0.0
func (suite *ConfigurableAggregatorTestSuite) TestAggregate_FlatAND_AllChildrenHit_ReturnsFilter() {
	schema := &interfaces.AggregationNode{
		Operator: interfaces.AggregationOperatorAND,
		Conditions: []interfaces.AggregationNode{
			{ModelIdentifier: testModelA},
			{ModelIdentifier: testModelB},
		},
	}
	modelOutputs := []interfaces.ModelEvaluatorOutput{
		{
			Status:          interfaces.ModelEvaluationStatusSuccess,
			ModelDefinition: interfaces.ModelDefinition{Identifier: testModelA},
			ModelResult:     interfaces.ModelResult{Value: 0.0},
		},
		{
			Status:          interfaces.ModelEvaluationStatusSuccess,
			ModelDefinition: interfaces.ModelDefinition{Identifier: testModelB},
			ModelResult:     interfaces.ModelResult{Value: 0.0},
		},
	}
	context := suite.setupMockContext(0, "T")

	result, err := suite.aggregator.Aggregate(schema, modelOutputs, context)

	suite.NoError(err)
	suite.Equal(float32(0.0), result.Score)
}

// Test flat AND: one child 1.0 → result 1.0
func (suite *ConfigurableAggregatorTestSuite) TestAggregate_FlatAND_OneChildForward_ReturnsForward() {
	schema := &interfaces.AggregationNode{
		Operator: interfaces.AggregationOperatorAND,
		Conditions: []interfaces.AggregationNode{
			{ModelIdentifier: testModelA},
			{ModelIdentifier: testModelB},
		},
	}
	modelOutputs := []interfaces.ModelEvaluatorOutput{
		{
			Status:          interfaces.ModelEvaluationStatusSuccess,
			ModelDefinition: interfaces.ModelDefinition{Identifier: testModelA},
			ModelResult:     interfaces.ModelResult{Value: 0.0},
		},
		{
			Status:          interfaces.ModelEvaluationStatusSuccess,
			ModelDefinition: interfaces.ModelDefinition{Identifier: testModelB},
			ModelResult:     interfaces.ModelResult{Value: 1.0},
		},
	}
	context := suite.setupMockContext(0, "T")

	result, err := suite.aggregator.Aggregate(schema, modelOutputs, context)

	suite.NoError(err)
	suite.Equal(float32(1.0), result.Score)
}

// Test nested OR→AND tree:
// OR(model_a, AND(model_b, model_c))
// model_a=1.0 (forward), model_b=0.0 (hit), model_c=0.0 (hit)
// AND(model_b, model_c) → all 0.0 → 0.0
// OR(1.0, 0.0) → any 0.0 → 0.0
func (suite *ConfigurableAggregatorTestSuite) TestAggregate_NestedOR_AND_Tree() {
	schema := &interfaces.AggregationNode{
		Operator: interfaces.AggregationOperatorOR,
		Conditions: []interfaces.AggregationNode{
			{ModelIdentifier: testModelA},
			{
				Operator: interfaces.AggregationOperatorAND,
				Conditions: []interfaces.AggregationNode{
					{ModelIdentifier: testModelB},
					{ModelIdentifier: testModelC},
				},
			},
		},
	}
	modelOutputs := []interfaces.ModelEvaluatorOutput{
		{
			Status:          interfaces.ModelEvaluationStatusSuccess,
			ModelDefinition: interfaces.ModelDefinition{Identifier: testModelA},
			ModelResult:     interfaces.ModelResult{Value: 1.0},
		},
		{
			Status:          interfaces.ModelEvaluationStatusSuccess,
			ModelDefinition: interfaces.ModelDefinition{Identifier: testModelB},
			ModelResult:     interfaces.ModelResult{Value: 0.0},
		},
		{
			Status:          interfaces.ModelEvaluationStatusSuccess,
			ModelDefinition: interfaces.ModelDefinition{Identifier: testModelC},
			ModelResult:     interfaces.ModelResult{Value: 0.0},
		},
	}
	context := suite.setupMockContext(0, "T")

	result, err := suite.aggregator.Aggregate(schema, modelOutputs, context)

	suite.NoError(err)
	// AND(0.0, 0.0) = 0.0; OR(1.0, 0.0) = 0.0 (any child 0.0 means filter)
	suite.Equal(float32(0.0), result.Score)
}

// Test nested AND→OR tree:
// AND(model_a, OR(model_b, model_c))
// model_a=0.0 (hit), model_b=1.0 (forward), model_c=0.0 (hit)
// OR(model_b, model_c) → any 0.0 → 0.0
// AND(0.0, 0.0) → all 0.0 → 0.0
func (suite *ConfigurableAggregatorTestSuite) TestAggregate_NestedAND_OR_Tree() {
	schema := &interfaces.AggregationNode{
		Operator: interfaces.AggregationOperatorAND,
		Conditions: []interfaces.AggregationNode{
			{ModelIdentifier: testModelA},
			{
				Operator: interfaces.AggregationOperatorOR,
				Conditions: []interfaces.AggregationNode{
					{ModelIdentifier: testModelB},
					{ModelIdentifier: testModelC},
				},
			},
		},
	}
	modelOutputs := []interfaces.ModelEvaluatorOutput{
		{
			Status:          interfaces.ModelEvaluationStatusSuccess,
			ModelDefinition: interfaces.ModelDefinition{Identifier: testModelA},
			ModelResult:     interfaces.ModelResult{Value: 0.0},
		},
		{
			Status:          interfaces.ModelEvaluationStatusSuccess,
			ModelDefinition: interfaces.ModelDefinition{Identifier: testModelB},
			ModelResult:     interfaces.ModelResult{Value: 1.0},
		},
		{
			Status:          interfaces.ModelEvaluationStatusSuccess,
			ModelDefinition: interfaces.ModelDefinition{Identifier: testModelC},
			ModelResult:     interfaces.ModelResult{Value: 0.0},
		},
	}
	context := suite.setupMockContext(0, "T")

	result, err := suite.aggregator.Aggregate(schema, modelOutputs, context)

	suite.NoError(err)
	// OR(1.0, 0.0) = 0.0; AND(0.0, 0.0) = 0.0 (all children 0.0 means filter)
	suite.Equal(float32(0.0), result.Score)
}

// Test that treatment code and experiment metadata are preserved in the result
func (suite *ConfigurableAggregatorTestSuite) TestAggregate_PreservesTreatmentCodeAndExperimentMetadata() {
	schema := &interfaces.AggregationNode{
		ModelIdentifier: testModelA,
	}
	modelOutputs := []interfaces.ModelEvaluatorOutput{
		{
			Status:          interfaces.ModelEvaluationStatusSuccess,
			ModelDefinition: interfaces.ModelDefinition{Identifier: testModelA},
			ModelResult:     interfaces.ModelResult{Value: 0.0},
		},
	}
	context := suite.setupMockContext(1, "C")

	result, err := suite.aggregator.Aggregate(schema, modelOutputs, context)

	suite.NoError(err)
	suite.Equal(testExperimentName, result.ExperimentName)
	suite.Equal("soft-filter", result.ExperimentType)
	suite.Equal("C", result.TreatmentCode)
	suite.Equal(int8(1), result.TreatmentCodeInInt)
	suite.Equal(float32(0.0), result.Score)
	// ScoreWithTreatment = max(score, treatmentCodeInInt) = max(0.0, 1.0) = 1.0
	suite.Equal(float32(1.0), result.ScoreWithTreatment)
	suite.Equal("configurable", result.AggregationType)
}
