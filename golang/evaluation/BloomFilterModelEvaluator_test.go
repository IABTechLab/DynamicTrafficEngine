// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package evaluation

import (
	"fmt"
	"testing"

	"github.com/stretchr/testify/mock"
	"github.com/stretchr/testify/suite"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	mockBloomfilter "golang.a2z.com/demanddriventrafficevaluator/mocks/bloomfilter"
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
)

func TestBloomFilterModelEvaluatorSuite(t *testing.T) {
	suite.Run(t, new(BloomFilterModelEvaluatorSuite))
}

type BloomFilterModelEvaluatorSuite struct {
	suite.Suite
	mockProvider           *mockBloomfilter.BloomFilterProviderInterface
	mockModelResultHandler *mockInterfaces.ModelResultHandlerInterface
	bloomFilterEvaluator   *BloomFilterModelEvaluator
	ruleBasedEvaluator     *RuleBasedModelEvaluator
}

func (suite *BloomFilterModelEvaluatorSuite) SetupTest() {
	suite.mockProvider = mockBloomfilter.NewBloomFilterProviderInterface(suite.T())
	suite.mockModelResultHandler = mockInterfaces.NewModelResultHandlerInterface(suite.T())
	suite.bloomFilterEvaluator = NewBloomFilterModelEvaluator(suite.mockProvider)
	suite.ruleBasedEvaluator = NewRuleBasedModelEvaluator(suite.mockModelResultHandler)
}

func (suite *BloomFilterModelEvaluatorSuite) TestEvaluate_Success_ReturnsSuccessStatusWithCorrectModelResult() {
	modelDefinition := interfaces.ModelDefinition{
		Identifier: "adsp_rsp_v1",
		Type:       "LowValue",
		Features: []interfaces.FeatureConfiguration{
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
	}
	input := interfaces.ModelEvaluatorInput{
		Context:         interfaces.NewContext(),
		ModelDefinition: &modelDefinition,
		FeatureFieldValueMap: map[string][]string{
			"$.site.publisher.id":  {"539014228"},
			"$.app.publisher.id":   {},
			"$.device.geo.country": {"USA"},
		},
	}

	expectedModelResult := interfaces.ModelResult{
		Value:  0.0,
		Key:    "539014228|USA",
		Keys:   []string{"539014228|USA"},
		Values: []float32{0.0},
	}

	suite.mockProvider.EXPECT().
		Provide(modelDefinition.Identifier, mock.Anything, float32(1.0)).
		Return(&expectedModelResult, nil).
		Once()

	output, err := suite.bloomFilterEvaluator.Evaluate(input)

	suite.NoError(err)
	suite.Equal(interfaces.ModelEvaluationStatusSuccess, output.Status)
	suite.Equal(expectedModelResult, output.ModelResult)
	suite.Equal(modelDefinition, output.ModelDefinition)
	suite.NotEmpty(output.ModelFeatures)
}

func (suite *BloomFilterModelEvaluatorSuite) TestEvaluate_FeatureExtractionFailure_ReturnsErrorStatus() {
	modelDefinition := interfaces.ModelDefinition{
		Identifier: "adsp_rsp_v1",
		Type:       "LowValue",
		Features: []interfaces.FeatureConfiguration{
			{
				Name:            "publisherId",
				Fields:          []string{"$.site.publisher.id"},
				Transformations: []interfaces.TransformerName{},
			},
		},
	}
	// Missing field in the value map causes feature extraction failure
	input := interfaces.ModelEvaluatorInput{
		Context:         interfaces.NewContext(),
		ModelDefinition: &modelDefinition,
		FeatureFieldValueMap: map[string][]string{
			"$.nonexistent.field": {"value"},
		},
	}

	output, err := suite.bloomFilterEvaluator.Evaluate(input)

	suite.Error(err)
	suite.Contains(err.Error(), "error getting modelFeatures")
	suite.Equal(interfaces.ModelEvaluationStatusError, output.Status)
}

func (suite *BloomFilterModelEvaluatorSuite) TestEvaluate_ProviderError_ReturnsErrorStatusWithModelDefinitionAndFeatures() {
	modelDefinition := interfaces.ModelDefinition{
		Identifier: "adsp_rsp_v1",
		Type:       "LowValue",
		Features: []interfaces.FeatureConfiguration{
			{
				Name:            "publisherId",
				Fields:          []string{"$.site.publisher.id"},
				Transformations: []interfaces.TransformerName{},
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

	suite.mockProvider.EXPECT().
		Provide(modelDefinition.Identifier, mock.Anything, float32(1.0)).
		Return(nil, fmt.Errorf("bloom filter lookup failed")).
		Once()

	output, err := suite.bloomFilterEvaluator.Evaluate(input)

	suite.Error(err)
	suite.Contains(err.Error(), "error getting modelResult")
	suite.Contains(err.Error(), "bloom filter lookup failed")
	suite.Equal(interfaces.ModelEvaluationStatusError, output.Status)
	suite.Equal(modelDefinition, output.ModelDefinition)
	suite.NotEmpty(output.ModelFeatures)
	// Verify features are populated even on provider error
	suite.Equal("539014228", output.ModelFeatures[0].Values[0])
}

func (suite *BloomFilterModelEvaluatorSuite) TestEvaluate_FeatureExtractionProducesSameFeaturesAsRuleBasedEvaluator() {
	// Verifies that BloomFilterModelEvaluator extracts the same features as RuleBasedModelEvaluator
	// for an identical input, ensuring feature extraction equivalence (Requirement 4.2).
	modelDefinition := interfaces.ModelDefinition{
		Identifier: "adsp_low-value_v2",
		Type:       "LowValue",
		Features: []interfaces.FeatureConfiguration{
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
	}
	fieldValueMap := map[string][]string{
		"$.app":                {"com.example.app"},
		"$.site.publisher.id":  {"539014228"},
		"$.app.publisher.id":   {""},
		"$.device.geo.country": {"USA"},
	}
	input := interfaces.ModelEvaluatorInput{
		Context:              interfaces.NewContext(),
		ModelDefinition:      &modelDefinition,
		FeatureFieldValueMap: fieldValueMap,
	}

	// Capture features from BloomFilterModelEvaluator
	var capturedBloomFeatures []interfaces.ModelFeature
	suite.mockProvider.EXPECT().
		Provide(modelDefinition.Identifier, mock.Anything, float32(1.0)).
		Run(func(modelIdentifier string, features []interfaces.ModelFeature, defaultValue float32) {
			capturedBloomFeatures = features
		}).
		Return(&interfaces.ModelResult{
			Value:  1.0,
			Key:    "app|539014228|USA",
			Keys:   []string{"app|539014228|USA"},
			Values: []float32{1.0},
		}, nil).
		Once()

	// Capture features from RuleBasedModelEvaluator
	var capturedRuleFeatures []interfaces.ModelFeature
	suite.mockModelResultHandler.EXPECT().
		Provide(modelDefinition.Identifier, mock.Anything, float32(1.0)).
		Run(func(modelIdentifier string, features []interfaces.ModelFeature, defaultValue float32) {
			capturedRuleFeatures = features
		}).
		Return(&interfaces.ModelResult{
			Value:  1.0,
			Key:    "app|539014228|USA",
			Keys:   []string{"app|539014228|USA"},
			Values: []float32{1.0},
		}, nil).
		Once()

	_, bloomErr := suite.bloomFilterEvaluator.Evaluate(input)
	_, ruleErr := suite.ruleBasedEvaluator.Evaluate(input)

	suite.NoError(bloomErr)
	suite.NoError(ruleErr)
	suite.Require().Equal(len(capturedRuleFeatures), len(capturedBloomFeatures),
		"Both evaluators should produce the same number of features")

	for i := range capturedRuleFeatures {
		suite.Equal(capturedRuleFeatures[i].Values, capturedBloomFeatures[i].Values,
			"Feature values at index %d should be identical", i)
		suite.Equal(capturedRuleFeatures[i].Configuration.Name, capturedBloomFeatures[i].Configuration.Name,
			"Feature configuration name at index %d should be identical", i)
	}
}
