// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package modelfeature

import (
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
)

type TrafficAllocatorTestSuite struct {
	suite.Suite
	allocator *TrafficAllocator
}

func (suite *TrafficAllocatorTestSuite) SetupTest() {
	suite.allocator = NewTrafficAllocator()
}

func (suite *TrafficAllocatorTestSuite) TestNewTrafficAllocator() {
	suite.NotNil(suite.allocator)
	suite.NotZero(suite.allocator.seed)
}

func (suite *TrafficAllocatorTestSuite) TestUpdateConfiguration() {
	config := &interfaces.ExperimentConfiguration{
		ExperimentDefinitionByName: map[string]interfaces.ExperimentDefinition{
			"exp1": {
				Treatments: []interfaces.Treatment{
					{TreatmentCode: "A", Weight: 50},
					{TreatmentCode: "B", Weight: 50},
				},
			},
		},
	}

	err := suite.allocator.UpdateConfiguration(config)
	suite.NoError(err)
	suite.Equal(config, suite.allocator.experimentConfiguration)
	suite.Len(suite.allocator.experimentThresholds, 1)
	suite.Equal([]uint32{50, 100}, suite.allocator.experimentThresholds["exp1"])
}

func (suite *TrafficAllocatorTestSuite) TestUpdateConfigurationInvalidWeight() {
	config := &interfaces.ExperimentConfiguration{
		ExperimentDefinitionByName: map[string]interfaces.ExperimentDefinition{
			"exp1": {
				Treatments: []interfaces.Treatment{
					{TreatmentCode: "A", Weight: 60},
					{TreatmentCode: "B", Weight: 60},
				},
			},
		},
	}

	err := suite.allocator.UpdateConfiguration(config)
	suite.Error(err)
	suite.Contains(err.Error(), "total weight must be 100")
}

func (suite *TrafficAllocatorTestSuite) TestGetTrafficAllocationContext() {
	config := &interfaces.ExperimentConfiguration{
		ExperimentDefinitionByName: map[string]interfaces.ExperimentDefinition{
			"exp1": {
				Treatments: []interfaces.Treatment{
					{TreatmentCode: "A", Weight: 50},
					{TreatmentCode: "B", Weight: 50},
				},
			},
		},
	}
	err := suite.allocator.UpdateConfiguration(config)
	suite.NoError(err)

	context := suite.allocator.GetTrafficAllocationContext()
	suite.NotNil(context)
	suite.Len(context.GetExperimentArrangement(), 1)
	suite.Contains([]string{"A", "B"}, context.GetExperimentArrangement()["exp1"])
}

func (suite *TrafficAllocatorTestSuite) TestGetTrafficAllocationContext_NoThresholdForExperiment() {
	config := &interfaces.ExperimentConfiguration{
		ExperimentDefinitionByName: map[string]interfaces.ExperimentDefinition{
			"exp1": {
				Treatments: []interfaces.Treatment{
					{TreatmentCode: "A", Weight: 50},
					{TreatmentCode: "B", Weight: 50},
				},
			},
		},
	}
	err := suite.allocator.UpdateConfiguration(config)
	suite.NoError(err)

	newConfig := &interfaces.ExperimentConfiguration{
		ExperimentDefinitionByName: map[string]interfaces.ExperimentDefinition{
			"exp2": {
				Treatments: []interfaces.Treatment{
					{TreatmentCode: "A", Weight: 50},
					{TreatmentCode: "B", Weight: 25},
					{TreatmentCode: "C", Weight: 25},
				},
			},
		},
	}
	suite.allocator.experimentConfiguration = newConfig
	context := suite.allocator.GetTrafficAllocationContext()
	suite.NotNil(context)
	suite.Len(context.GetExperimentArrangement(), 0)
}

func (suite *TrafficAllocatorTestSuite) TestGetTrafficAllocationContext_GetTreatmentCodeError() {
	config := &interfaces.ExperimentConfiguration{
		ExperimentDefinitionByName: map[string]interfaces.ExperimentDefinition{
			"exp1": {
				Treatments: []interfaces.Treatment{
					{TreatmentCode: "A", Weight: 50},
					{TreatmentCode: "B", Weight: 50},
				},
			},
		},
	}
	err := suite.allocator.UpdateConfiguration(config)
	suite.NoError(err)

	newConfig := &interfaces.ExperimentConfiguration{
		ExperimentDefinitionByName: map[string]interfaces.ExperimentDefinition{
			"exp1": {
				Treatments: []interfaces.Treatment{},
			},
		},
	}
	suite.allocator.experimentConfiguration = newConfig
	context := suite.allocator.GetTrafficAllocationContext()
	suite.NotNil(context)
	suite.Len(context.GetExperimentArrangement(), 0)
}

func (suite *TrafficAllocatorTestSuite) TestGetTreatmentCode() {
	thresholds := []uint32{50, 100}
	treatments := []interfaces.Treatment{
		{TreatmentCode: "A", Weight: 50},
		{TreatmentCode: "B", Weight: 50},
	}

	// Test multiple times to ensure both treatments are selected
	treatmentCounts := make(map[string]int)
	for i := 0; i < 1000; i++ {
		code, err := suite.allocator.GetTreatmentCode(thresholds, treatments)
		suite.NoError(err)
		suite.Contains([]string{"A", "B"}, code)
		treatmentCounts[code]++
	}

	// Check that both treatments were selected and roughly equally
	suite.Greater(treatmentCounts["A"], 400)
	suite.Greater(treatmentCounts["B"], 400)
}

func (suite *TrafficAllocatorTestSuite) TestGetTreatmentCodeNoTreatments() {
	_, err := suite.allocator.GetTreatmentCode([]uint32{}, []interfaces.Treatment{})
	suite.Error(err)
	suite.Contains(err.Error(), "no treatments configured")
}

func (suite *TrafficAllocatorTestSuite) TestRand() {
	// Test that Rand() generates different numbers
	first := suite.allocator.Rand()
	time.Sleep(time.Nanosecond) // Ensure a different seed
	second := suite.allocator.Rand()
	suite.NotEqual(first, second)
}

func TestTrafficAllocatorSuite(t *testing.T) {
	suite.Run(t, new(TrafficAllocatorTestSuite))
}

type TrafficAllocationContextTestSuite struct {
	suite.Suite
	context *TrafficAllocationContext
}

func (suite *TrafficAllocationContextTestSuite) SetupTest() {
	experimentArrangement := map[string]string{
		"exp1": "T",
		"exp2": "C",
	}
	configuration := interfaces.ExperimentConfiguration{
		ExperimentDefinitionByName: map[string]interfaces.ExperimentDefinition{
			"exp1": {Type: "type1", Name: "exp1"},
			"exp2": {Type: "type2", Name: "exp2"},
		},
		ModelToExperiment: map[string]string{
			"model1": "exp1",
			"model2": "exp1",
			"model3": "exp2",
		},
	}
	suite.context = NewTrafficAllocationContext(experimentArrangement, &configuration)
}

func (suite *TrafficAllocationContextTestSuite) TestNewTrafficAllocationContext() {
	suite.NotNil(suite.context)
	suite.Len(suite.context.experimentArrangement, 2)
	suite.Len(suite.context.experimentDefinitionByName, 2)
	suite.Len(suite.context.modelToExperiment, 3)
	suite.Len(suite.context.modelsByExperiment, 2)
	suite.Len(suite.context.modelsByExperiment["exp1"], 2)
	suite.Len(suite.context.modelsByExperiment["exp2"], 1)
}

func (suite *TrafficAllocationContextTestSuite) TestGetModelIdentifiers() {
	models := suite.context.GetModelIdentifiers()
	suite.Len(models, 3)
	suite.Contains(models, "model1")
	suite.Contains(models, "model2")
	suite.Contains(models, "model3")
}

func (suite *TrafficAllocationContextTestSuite) TestGetTreatmentCode() {
	suite.Equal("T", suite.context.GetTreatmentCode("exp1"))
	suite.Equal("C", suite.context.GetTreatmentCode("exp2"))
	suite.Equal("", suite.context.GetTreatmentCode("nonexistent"))
}

func (suite *TrafficAllocationContextTestSuite) TestGetTreatmentCodeInInt() {
	suite.Equal(int8(0), suite.context.GetTreatmentCodeInInt("exp1"))
	suite.Equal(int8(1), suite.context.GetTreatmentCodeInInt("exp2"))
	suite.Equal(int8(1), suite.context.GetTreatmentCodeInInt("nonexistent"))
}

func (suite *TrafficAllocationContextTestSuite) TestGetExperimentDefinition() {
	def := suite.context.GetExperimentDefinition("exp1")
	suite.Equal("type1", def.Type)
	suite.Equal("exp1", def.Name)

	def = suite.context.GetExperimentDefinition("nonexistent")
	suite.Equal(interfaces.ExperimentDefinition{}, def)
}

func (suite *TrafficAllocationContextTestSuite) TestGetExperimentDefinitionByModel() {
	def := suite.context.GetExperimentDefinitionByModel("model1")
	suite.Equal("type1", def.Type)
	suite.Equal("exp1", def.Name)

	def = suite.context.GetExperimentDefinitionByModel("nonexistent")
	suite.Equal(interfaces.ExperimentDefinition{}, def)
}

func (suite *TrafficAllocationContextTestSuite) TestGetExperimentDefinitionByType() {
	def, err := suite.context.GetExperimentDefinitionByType("type1")
	suite.NoError(err)
	suite.Equal("type1", def.Type)
	suite.Equal("exp1", def.Name)

	def, err = suite.context.GetExperimentDefinitionByType("nonexistent")
	suite.Error(err)
	suite.Nil(def)
}

func (suite *TrafficAllocationContextTestSuite) TestGetModelsByExperiment() {
	models := suite.context.GetModelsByExperiment()
	suite.Len(models, 2)
	suite.Len(models["exp1"], 2)
	suite.Len(models["exp2"], 1)
	suite.Contains(models["exp1"], "model1")
	suite.Contains(models["exp1"], "model2")
	suite.Contains(models["exp2"], "model3")
}

func TestTrafficAllocationContextSuite(t *testing.T) {
	suite.Run(t, new(TrafficAllocationContextTestSuite))
}
