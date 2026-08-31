// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package interfaces

import (
	"encoding/json"
	"testing"

	"github.com/stretchr/testify/suite"
)

func TestModelFeatureDeserializationSuite(t *testing.T) {
	suite.Run(t, new(ModelFeatureDeserializationSuite))
}

type ModelFeatureDeserializationSuite struct {
	suite.Suite
}

// --- ModelDefinition deserialization tests ---

func (suite *ModelFeatureDeserializationSuite) TestModelDefinition_MissingModelFormat_DefaultsToEmptyString() {
	// When modelFormat is absent in JSON, the field should deserialize to empty string.
	// Consuming code treats empty string as RULE_BASED.
	jsonData := `{
		"identifier": "adsp_low-value_v2",
		"dsp": "adsp",
		"name": "low-value",
		"version": "v2",
		"modelType": "LowValue",
		"featureExtractorType": "JsonExtractor",
		"features": []
	}`

	var modelDef ModelDefinition
	err := json.Unmarshal([]byte(jsonData), &modelDef)

	suite.NoError(err)
	suite.Equal("", modelDef.ModelFormat, "Missing modelFormat should default to empty string")
	suite.Equal("adsp_low-value_v2", modelDef.Identifier)
}

func (suite *ModelFeatureDeserializationSuite) TestModelDefinition_MissingS3PathMode_DefaultsToEmptyString() {
	// When s3PathMode is absent in JSON, the field should deserialize to empty string.
	// Consuming code treats empty string as DYNAMIC.
	jsonData := `{
		"identifier": "adsp_rsp_v1",
		"dsp": "adsp",
		"name": "rsp",
		"version": "v1",
		"modelType": "LowValue",
		"modelFormat": "BLOOM_FILTER",
		"featureExtractorType": "JsonExtractor",
		"features": []
	}`

	var modelDef ModelDefinition
	err := json.Unmarshal([]byte(jsonData), &modelDef)

	suite.NoError(err)
	suite.Equal("", modelDef.S3PathMode, "Missing s3PathMode should default to empty string")
	suite.Equal("BLOOM_FILTER", modelDef.ModelFormat)
}

func (suite *ModelFeatureDeserializationSuite) TestModelDefinition_ExplicitModelFormatAndS3PathMode() {
	// When both fields are provided, they should deserialize correctly.
	jsonData := `{
		"identifier": "adsp_rsp_v1",
		"dsp": "adsp",
		"name": "rsp",
		"version": "v1",
		"modelType": "LowValue",
		"modelFormat": "BLOOM_FILTER",
		"s3PathMode": "STATIC",
		"featureExtractorType": "JsonExtractor",
		"features": []
	}`

	var modelDef ModelDefinition
	err := json.Unmarshal([]byte(jsonData), &modelDef)

	suite.NoError(err)
	suite.Equal("BLOOM_FILTER", modelDef.ModelFormat)
	suite.Equal("STATIC", modelDef.S3PathMode)
}

func (suite *ModelFeatureDeserializationSuite) TestModelDefinition_BothFieldsMissing_DefaultToEmptyStrings() {
	// Legacy JSON without modelFormat or s3PathMode should still parse with empty defaults.
	jsonData := `{
		"identifier": "adsp_low-value_v2",
		"dsp": "adsp",
		"name": "low-value",
		"version": "v2",
		"modelType": "LowValue",
		"featureExtractorType": "JsonExtractor",
		"features": [
			{
				"name": "publisherId",
				"fields": ["$.site.publisher.id"],
				"transformation": ["GetFirstNotEmpty"]
			}
		]
	}`

	var modelDef ModelDefinition
	err := json.Unmarshal([]byte(jsonData), &modelDef)

	suite.NoError(err)
	suite.Equal("", modelDef.ModelFormat, "Missing modelFormat should default to empty string")
	suite.Equal("", modelDef.S3PathMode, "Missing s3PathMode should default to empty string")
	suite.Equal(1, len(modelDef.Features), "Features should be deserialized")
}

// --- AggregationNode JSON marshal/unmarshal round trip tests ---

func (suite *ModelFeatureDeserializationSuite) TestAggregationNode_LeafNode_RoundTrip() {
	original := AggregationNode{
		ModelIdentifier: "adsp_rsp_v1",
	}

	data, err := json.Marshal(original)
	suite.NoError(err)

	var result AggregationNode
	err = json.Unmarshal(data, &result)

	suite.NoError(err)
	suite.Equal(original.ModelIdentifier, result.ModelIdentifier)
	suite.Equal("", result.Operator)
	suite.Nil(result.Conditions)
	suite.True(result.IsLeaf())
}

func (suite *ModelFeatureDeserializationSuite) TestAggregationNode_BranchNode_RoundTrip() {
	original := AggregationNode{
		Operator: AggregationOperatorOR,
		Conditions: []AggregationNode{
			{ModelIdentifier: "model_a"},
			{ModelIdentifier: "model_b"},
		},
	}

	data, err := json.Marshal(original)
	suite.NoError(err)

	var result AggregationNode
	err = json.Unmarshal(data, &result)

	suite.NoError(err)
	suite.Equal(AggregationOperatorOR, result.Operator)
	suite.Equal(2, len(result.Conditions))
	suite.Equal("model_a", result.Conditions[0].ModelIdentifier)
	suite.Equal("model_b", result.Conditions[1].ModelIdentifier)
	suite.False(result.IsLeaf())
}

func (suite *ModelFeatureDeserializationSuite) TestAggregationNode_NestedTree_RoundTrip() {
	// Tree: OR(leaf_a, AND(leaf_b, leaf_c))
	original := AggregationNode{
		Operator: AggregationOperatorOR,
		Conditions: []AggregationNode{
			{ModelIdentifier: "adsp_low-value_v2"},
			{
				Operator: AggregationOperatorAND,
				Conditions: []AggregationNode{
					{ModelIdentifier: "adsp_rsp_v1"},
					{ModelIdentifier: "adsp_rsp_v2"},
				},
			},
		},
	}

	data, err := json.Marshal(original)
	suite.NoError(err)

	var result AggregationNode
	err = json.Unmarshal(data, &result)

	suite.NoError(err)
	suite.Equal(AggregationOperatorOR, result.Operator)
	suite.Equal(2, len(result.Conditions))
	// First child: leaf node
	suite.True(result.Conditions[0].IsLeaf())
	suite.Equal("adsp_low-value_v2", result.Conditions[0].ModelIdentifier)
	// Second child: AND branch
	suite.False(result.Conditions[1].IsLeaf())
	suite.Equal(AggregationOperatorAND, result.Conditions[1].Operator)
	suite.Equal(2, len(result.Conditions[1].Conditions))
	suite.Equal("adsp_rsp_v1", result.Conditions[1].Conditions[0].ModelIdentifier)
	suite.Equal("adsp_rsp_v2", result.Conditions[1].Conditions[1].ModelIdentifier)
}

func (suite *ModelFeatureDeserializationSuite) TestAggregationNode_DeserializeFromJSON() {
	// Deserialize from a JSON string representative of real config
	jsonData := `{
		"operator": "OR",
		"conditions": [
			{"modelIdentifier": "adsp_low-value_v2"},
			{
				"operator": "AND",
				"conditions": [
					{"modelIdentifier": "adsp_rsp_v1"},
					{"modelIdentifier": "adsp_rsp_v2"}
				]
			}
		]
	}`

	var node AggregationNode
	err := json.Unmarshal([]byte(jsonData), &node)

	suite.NoError(err)
	suite.Equal("OR", node.Operator)
	suite.Equal(2, len(node.Conditions))
	suite.True(node.Conditions[0].IsLeaf())
	suite.Equal("adsp_low-value_v2", node.Conditions[0].ModelIdentifier)
	suite.False(node.Conditions[1].IsLeaf())
	suite.Equal("AND", node.Conditions[1].Operator)
}

// --- ExperimentDefinition tests with and without aggregationSchema ---

func (suite *ModelFeatureDeserializationSuite) TestExperimentDefinition_WithoutAggregationSchema() {
	// When aggregationSchema is absent, the field should be nil (max-aggregation fallback).
	jsonData := `{
		"name": "DemandDrivenTrafficEvaluatorSoftFilter",
		"type": "soft-filter",
		"treatments": [
			{"treatmentCode": "T", "weight": 80},
			{"treatmentCode": "C", "weight": 20}
		],
		"startTimeUTC": 1654498800000,
		"endTimeUTC": 1727334000000
	}`

	var expDef ExperimentDefinition
	err := json.Unmarshal([]byte(jsonData), &expDef)

	suite.NoError(err)
	suite.Nil(expDef.AggregationSchema, "Missing aggregationSchema should be nil")
	suite.Equal("DemandDrivenTrafficEvaluatorSoftFilter", expDef.Name)
	suite.Equal("soft-filter", expDef.Type)
	suite.Equal(2, len(expDef.Treatments))
}

func (suite *ModelFeatureDeserializationSuite) TestExperimentDefinition_WithAggregationSchema() {
	// When aggregationSchema is present, it should deserialize into the AggregationNode tree.
	jsonData := `{
		"name": "DemandDrivenTrafficEvaluatorSoftFilter",
		"type": "soft-filter",
		"aggregationSchema": {
			"operator": "OR",
			"conditions": [
				{"modelIdentifier": "adsp_low-value_v2"},
				{"modelIdentifier": "adsp_rsp_v1"}
			]
		},
		"treatments": [
			{"treatmentCode": "T", "weight": 100}
		],
		"startTimeUTC": 1654498800000,
		"endTimeUTC": 1727334000000
	}`

	var expDef ExperimentDefinition
	err := json.Unmarshal([]byte(jsonData), &expDef)

	suite.NoError(err)
	suite.NotNil(expDef.AggregationSchema, "aggregationSchema should not be nil")
	suite.Equal("OR", expDef.AggregationSchema.Operator)
	suite.Equal(2, len(expDef.AggregationSchema.Conditions))
	suite.Equal("adsp_low-value_v2", expDef.AggregationSchema.Conditions[0].ModelIdentifier)
	suite.Equal("adsp_rsp_v1", expDef.AggregationSchema.Conditions[1].ModelIdentifier)
}

func (suite *ModelFeatureDeserializationSuite) TestExperimentDefinition_AggregationSchemaRoundTrip() {
	// Full round trip: marshal and unmarshal ExperimentDefinition with aggregationSchema.
	original := ExperimentDefinition{
		Name: "TestExperiment",
		Type: "soft-filter",
		AggregationSchema: &AggregationNode{
			Operator: AggregationOperatorAND,
			Conditions: []AggregationNode{
				{ModelIdentifier: "model_a"},
				{ModelIdentifier: "model_b"},
			},
		},
		Treatments: []Treatment{
			{TreatmentCode: "T", Weight: 50},
			{TreatmentCode: "C", Weight: 50},
		},
		StartTimeUTC: 1654498800000,
		EndTimeUTC:   1727334000000,
	}

	data, err := json.Marshal(original)
	suite.NoError(err)

	var result ExperimentDefinition
	err = json.Unmarshal(data, &result)

	suite.NoError(err)
	suite.Equal(original.Name, result.Name)
	suite.Equal(original.Type, result.Type)
	suite.NotNil(result.AggregationSchema)
	suite.Equal(AggregationOperatorAND, result.AggregationSchema.Operator)
	suite.Equal(2, len(result.AggregationSchema.Conditions))
	suite.Equal("model_a", result.AggregationSchema.Conditions[0].ModelIdentifier)
	suite.Equal("model_b", result.AggregationSchema.Conditions[1].ModelIdentifier)
	suite.Equal(2, len(result.Treatments))
	suite.Equal(original.StartTimeUTC, result.StartTimeUTC)
	suite.Equal(original.EndTimeUTC, result.EndTimeUTC)
}

func (suite *ModelFeatureDeserializationSuite) TestExperimentDefinition_NilAggregationSchema_OmittedInJSON() {
	// When AggregationSchema is nil, it should be omitted from JSON output.
	expDef := ExperimentDefinition{
		Name: "TestExperiment",
		Type: "soft-filter",
		Treatments: []Treatment{
			{TreatmentCode: "T", Weight: 100},
		},
		StartTimeUTC: 1654498800000,
		EndTimeUTC:   1727334000000,
	}

	data, err := json.Marshal(expDef)
	suite.NoError(err)

	// The JSON should not contain "aggregationSchema"
	var rawMap map[string]json.RawMessage
	err = json.Unmarshal(data, &rawMap)
	suite.NoError(err)
	_, exists := rawMap["aggregationSchema"]
	suite.False(exists, "aggregationSchema should be omitted when nil")
}
