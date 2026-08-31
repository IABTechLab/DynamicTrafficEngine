// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package evaluation

import (
	"testing"

	"github.com/stretchr/testify/mock"
	"github.com/stretchr/testify/suite"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
)

func TestHighValueDealPipelineSuite(t *testing.T) {
	suite.Run(t, new(HighValueDealPipelineSuite))
}

type HighValueDealPipelineSuite struct {
	suite.Suite
	mockModelResultHandler *mockInterfaces.ModelResultHandlerInterface
	evaluator              *RuleBasedModelEvaluator
}

func (suite *HighValueDealPipelineSuite) SetupTest() {
	suite.mockModelResultHandler = mockInterfaces.NewModelResultHandlerInterface(suite.T())
	suite.evaluator = NewRuleBasedModelEvaluator(suite.mockModelResultHandler)
}

// Tests the full high-value deal pipeline: a HighValue model with wildcard-extracted
// multi-value deal IDs, IncludeDefaultValue transformer, and correct default value derivation.
func (suite *HighValueDealPipelineSuite) TestHighValueDealPipeline_MultiValueFeatures_CorrectDefaultAndKeys() {
	// Set up a HighValue model with a deal ID feature using IncludeDefaultValue transformer
	// and a publisher ID feature using GetFirstNotEmpty transformer.
	modelDefinition := interfaces.ModelDefinition{
		Identifier:           "adsp_high-value-deals_v1",
		Name:                 "high-value-deals",
		Dsp:                  "adsp",
		Version:              "v1",
		Type:                 "HighValue",
		FeatureExtractorType: "JsonExtractor",
		Features: []interfaces.FeatureConfiguration{
			{
				Name:                "dealId",
				Fields:              []string{"$.imp[0].pmp.deals[*].id"},
				Transformations:     []interfaces.TransformerName{"IncludeDefaultValue"},
				MappingDefaultValue: "no_deal",
			},
			{
				Name:            "publisherId",
				Fields:          []string{"$.site.publisher.id", "$.app.publisher.id"},
				Transformations: []interfaces.TransformerName{"GetFirstNotEmpty"},
			},
		},
	}

	// Simulate a FeatureFieldValueMap as it would come from parsing an OpenRTB request
	// with multiple deal IDs extracted via the [*] wildcard path.
	featureFieldValueMap := map[string][]string{
		"$.imp[0].pmp.deals[*].id": {"OX-XPT-DuQvxM", "OX-XPT-k503sK", "OX-XPT-Nj7ncX"},
		"$.site.publisher.id":      {"539014228"},
		"$.app.publisher.id":       {},
	}

	input := interfaces.ModelEvaluatorInput{
		Context:              interfaces.NewContext(),
		ModelDefinition:      &modelDefinition,
		FeatureFieldValueMap: featureFieldValueMap,
	}

	// Capture the arguments passed to Provide to verify:
	// 1. The correct defaultValue (0.0 for HighValue)
	// 2. The correct features (deal IDs + "no_deal" from IncludeDefaultValue, publisher ID from GetFirstNotEmpty)
	var capturedFeatures []interfaces.ModelFeature
	var capturedDefaultValue float32
	var capturedModelIdentifier string

	suite.mockModelResultHandler.EXPECT().
		Provide(mock.AnythingOfType("string"), mock.Anything, mock.AnythingOfType("float32")).
		Run(func(modelIdentifier string, features []interfaces.ModelFeature, defaultValue float32) {
			capturedModelIdentifier = modelIdentifier
			capturedFeatures = features
			capturedDefaultValue = defaultValue
		}).
		Return(&interfaces.ModelResult{
			Value:  float32(1.0),
			Key:    "OX-XPT-DuQvxM|539014228",
			Keys:   []string{"OX-XPT-DuQvxM|539014228", "OX-XPT-k503sK|539014228", "OX-XPT-Nj7ncX|539014228", "no_deal|539014228"},
			Values: []float32{1.0, 0.0, 0.0, 0.0},
		}, nil).
		Once()

	output, err := suite.evaluator.Evaluate(input)

	// Verify no error and successful evaluation
	suite.NoError(err)
	suite.Equal(interfaces.ModelEvaluationStatusSuccess, output.Status)

	// Verify the correct model identifier was passed
	suite.Equal("adsp_high-value-deals_v1", capturedModelIdentifier)

	// Verify defaultValue is 0.0 for HighValue models
	suite.Equal(float32(0.0), capturedDefaultValue)

	// Verify features passed to Provide
	suite.Require().Len(capturedFeatures, 2)

	// First feature (dealId): IncludeDefaultValue filters empties and appends "no_deal"
	dealIdFeature := capturedFeatures[0]
	suite.Equal("dealId", dealIdFeature.Configuration.Name)
	suite.Equal([]string{"OX-XPT-DuQvxM", "OX-XPT-k503sK", "OX-XPT-Nj7ncX", "no_deal"}, dealIdFeature.Values)

	// Second feature (publisherId): GetFirstNotEmpty returns the first non-empty value
	publisherFeature := capturedFeatures[1]
	suite.Equal("publisherId", publisherFeature.Configuration.Name)
	suite.Equal([]string{"539014228"}, publisherFeature.Values)

	// Verify the ModelResult returned has cache hit value
	suite.Equal(float32(1.0), output.ModelResult.Value)
	suite.Equal("OX-XPT-DuQvxM|539014228", output.ModelResult.Key)
}

// Tests that when all deal IDs are empty strings, IncludeDefaultValue filters them
// and only appends the default value.
func (suite *HighValueDealPipelineSuite) TestHighValueDealPipeline_EmptyDealIds_OnlyDefaultValueRemains() {
	modelDefinition := interfaces.ModelDefinition{
		Identifier:           "adsp_high-value-deals_v1",
		Name:                 "high-value-deals",
		Dsp:                  "adsp",
		Version:              "v1",
		Type:                 "HighValue",
		FeatureExtractorType: "JsonExtractor",
		Features: []interfaces.FeatureConfiguration{
			{
				Name:                "dealId",
				Fields:              []string{"$.imp[0].pmp.deals[*].id"},
				Transformations:     []interfaces.TransformerName{"IncludeDefaultValue"},
				MappingDefaultValue: "no_deal",
			},
			{
				Name:            "publisherId",
				Fields:          []string{"$.site.publisher.id"},
				Transformations: []interfaces.TransformerName{"GetFirstNotEmpty"},
			},
		},
	}

	// Simulate extracted values where deal IDs are all empty strings
	featureFieldValueMap := map[string][]string{
		"$.imp[0].pmp.deals[*].id": {"", "", ""},
		"$.site.publisher.id":      {"539014228"},
	}

	input := interfaces.ModelEvaluatorInput{
		Context:              interfaces.NewContext(),
		ModelDefinition:      &modelDefinition,
		FeatureFieldValueMap: featureFieldValueMap,
	}

	var capturedFeatures []interfaces.ModelFeature
	var capturedDefaultValue float32

	suite.mockModelResultHandler.EXPECT().
		Provide(mock.AnythingOfType("string"), mock.Anything, mock.AnythingOfType("float32")).
		Run(func(modelIdentifier string, features []interfaces.ModelFeature, defaultValue float32) {
			capturedFeatures = features
			capturedDefaultValue = defaultValue
		}).
		Return(&interfaces.ModelResult{
			Value:  float32(0.0),
			Key:    "no_deal|539014228",
			Keys:   []string{"no_deal|539014228"},
			Values: []float32{0.0},
		}, nil).
		Once()

	output, err := suite.evaluator.Evaluate(input)

	suite.NoError(err)
	suite.Equal(interfaces.ModelEvaluationStatusSuccess, output.Status)
	suite.Equal(float32(0.0), capturedDefaultValue)

	// IncludeDefaultValue filters all empty strings, leaving only "no_deal"
	suite.Require().Len(capturedFeatures, 2)
	suite.Equal([]string{"no_deal"}, capturedFeatures[0].Values)
	suite.Equal([]string{"539014228"}, capturedFeatures[1].Values)
}

// Tests that a HighValue model with multiple deal IDs from multiple fields
// concatenates them in field-declaration order before applying the transformer.
func (suite *HighValueDealPipelineSuite) TestHighValueDealPipeline_MultipleFieldsConcatenated() {
	modelDefinition := interfaces.ModelDefinition{
		Identifier:           "adsp_high-value-deals_v1",
		Name:                 "high-value-deals",
		Dsp:                  "adsp",
		Version:              "v1",
		Type:                 "HighValue",
		FeatureExtractorType: "JsonExtractor",
		Features: []interfaces.FeatureConfiguration{
			{
				Name:                "dealId",
				Fields:              []string{"$.imp[0].pmp.deals[*].id", "$.imp[0].pmp.ext_deals[*].id"},
				Transformations:     []interfaces.TransformerName{"IncludeDefaultValue"},
				MappingDefaultValue: "no_deal",
			},
		},
	}

	// Values from two wildcard fields are concatenated in declaration order
	featureFieldValueMap := map[string][]string{
		"$.imp[0].pmp.deals[*].id":     {"deal-A", "deal-B"},
		"$.imp[0].pmp.ext_deals[*].id": {"ext-deal-C"},
	}

	input := interfaces.ModelEvaluatorInput{
		Context:              interfaces.NewContext(),
		ModelDefinition:      &modelDefinition,
		FeatureFieldValueMap: featureFieldValueMap,
	}

	var capturedFeatures []interfaces.ModelFeature

	suite.mockModelResultHandler.EXPECT().
		Provide(mock.AnythingOfType("string"), mock.Anything, mock.AnythingOfType("float32")).
		Run(func(modelIdentifier string, features []interfaces.ModelFeature, defaultValue float32) {
			capturedFeatures = features
		}).
		Return(&interfaces.ModelResult{
			Value:  float32(0.0),
			Key:    "deal-A",
			Keys:   []string{"deal-A", "deal-B", "ext-deal-C", "no_deal"},
			Values: []float32{0.0, 0.0, 0.0, 0.0},
		}, nil).
		Once()

	output, err := suite.evaluator.Evaluate(input)

	suite.NoError(err)
	suite.Equal(interfaces.ModelEvaluationStatusSuccess, output.Status)

	// Values from both fields concatenated in order, then IncludeDefaultValue appends "no_deal"
	suite.Require().Len(capturedFeatures, 1)
	suite.Equal([]string{"deal-A", "deal-B", "ext-deal-C", "no_deal"}, capturedFeatures[0].Values)
}

// Tests backward compatibility: a LowValue model still passes 1.0 as defaultValue.
func (suite *HighValueDealPipelineSuite) TestLowValueModel_DefaultValueIsOne() {
	modelDefinition := interfaces.ModelDefinition{
		Identifier:           "adsp_low-value_v1",
		Name:                 "low-value",
		Dsp:                  "adsp",
		Version:              "v1",
		Type:                 "LowValue",
		FeatureExtractorType: "JsonExtractor",
		Features: []interfaces.FeatureConfiguration{
			{
				Name:            "publisherId",
				Fields:          []string{"$.site.publisher.id"},
				Transformations: []interfaces.TransformerName{"GetFirstNotEmpty"},
			},
		},
	}

	featureFieldValueMap := map[string][]string{
		"$.site.publisher.id": {"539014228"},
	}

	input := interfaces.ModelEvaluatorInput{
		Context:              interfaces.NewContext(),
		ModelDefinition:      &modelDefinition,
		FeatureFieldValueMap: featureFieldValueMap,
	}

	var capturedDefaultValue float32

	suite.mockModelResultHandler.EXPECT().
		Provide(mock.AnythingOfType("string"), mock.Anything, mock.AnythingOfType("float32")).
		Run(func(modelIdentifier string, features []interfaces.ModelFeature, defaultValue float32) {
			capturedDefaultValue = defaultValue
		}).
		Return(&interfaces.ModelResult{
			Value:  float32(1.0),
			Key:    "539014228",
			Keys:   []string{"539014228"},
			Values: []float32{1.0},
		}, nil).
		Once()

	output, err := suite.evaluator.Evaluate(input)

	suite.NoError(err)
	suite.Equal(interfaces.ModelEvaluationStatusSuccess, output.Status)
	suite.Equal(float32(1.0), capturedDefaultValue)
}
