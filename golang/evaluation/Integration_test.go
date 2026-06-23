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
	highValueModelIdentifier = "adsp_high-value-deals_v1"
)

func TestHighValueDealIntegrationSuite(t *testing.T) {
	suite.Run(t, new(HighValueDealIntegrationSuite))
}

type HighValueDealIntegrationSuite struct {
	suite.Suite
	mockLocalCacheFactory  *mockInterfaces.LocalCacheFactoryInterface
	mockDaoFactory         *mockInterfaces.DaoFactoryInterface
	mockModelConfigHandler *mockInterfaces.ModelConfigurationHandlerInterface
	mockTimeProvider       *mockInterfaces.TimeProvider
	modelResultHandler     *modelfeature.ModelResultHandler
	ruleBasedEvaluator     *RuleBasedModelEvaluator
}

func (suite *HighValueDealIntegrationSuite) SetupTest() {
	suite.mockLocalCacheFactory = mockInterfaces.NewLocalCacheFactoryInterface(suite.T())
	suite.mockDaoFactory = mockInterfaces.NewDaoFactoryInterface(suite.T())
	suite.mockModelConfigHandler = mockInterfaces.NewModelConfigurationHandlerInterface(suite.T())
	suite.mockTimeProvider = mockInterfaces.NewTimeProvider(suite.T())

	suite.modelResultHandler = modelfeature.NewModelResultHandler(
		"ssp",
		"./testdata",
		suite.mockDaoFactory,
		suite.mockModelConfigHandler,
		suite.mockLocalCacheFactory,
		suite.mockTimeProvider,
	)

	suite.ruleBasedEvaluator = NewRuleBasedModelEvaluator(suite.modelResultHandler)
}

// TestFullHighValueDealPipeline exercises the entire flow:
// JSON request → parse wildcard fields → IncludeDefaultValue transformer → BuildKeys (Cartesian product) → Provide (first-hit-wins)
func (suite *HighValueDealIntegrationSuite) TestFullHighValueDealPipeline() {
	// Model definition: a HighValue deal model with:
	// - dealId feature using wildcard path and IncludeDefaultValue transformer
	// - publisherId feature using GetFirstNotEmpty transformer
	modelDefinition := interfaces.ModelDefinition{
		Identifier:           highValueModelIdentifier,
		Dsp:                  "adsp",
		Name:                 "high-value-deals",
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

	// The OpenRTB request has 3 deals: deal-AAA, deal-BBB, deal-CCC
	openRtbRequest := `{
		"site": {"publisher": {"id": "pub123"}},
		"imp": [{
			"pmp": {
				"deals": [
					{"id": "deal-AAA", "bidfloor": 1.0},
					{"id": "deal-BBB", "bidfloor": 2.0},
					{"id": "deal-CCC", "bidfloor": 3.0}
				]
			}
		}]
	}`

	// Build the FeatureFieldValueMap by simulating what parse() would produce for this request.
	// Wildcard path: $.imp[0].pmp.deals[*].id → ["deal-AAA", "deal-BBB", "deal-CCC"]
	// Scalar paths: $.site.publisher.id → ["pub123"], $.app.publisher.id → []
	featureFieldValueMap := map[string][]string{
		"$.imp[0].pmp.deals[*].id": {"deal-AAA", "deal-BBB", "deal-CCC"},
		"$.site.publisher.id":      {"pub123"},
		"$.app.publisher.id":       {},
	}

	input := interfaces.ModelEvaluatorInput{
		Context:              interfaces.NewContext(),
		OpenRtbRequest:       openRtbRequest,
		ModelDefinition:      &modelDefinition,
		FeatureFieldValueMap: featureFieldValueMap,
	}

	// After IncludeDefaultValue: dealId values = ["deal-AAA", "deal-BBB", "deal-CCC", "no_deal"]
	// After GetFirstNotEmpty: publisherId values = ["pub123"]
	// BuildKeys produces 4 × 1 = 4 permutation keys:
	//   "deal-AAA|pub123", "deal-BBB|pub123", "deal-CCC|pub123", "no_deal|pub123"
	expectedKeys := []string{
		"deal-AAA|pub123",
		"deal-BBB|pub123",
		"deal-CCC|pub123",
		"no_deal|pub123",
	}

	// Simulate cache: only "deal-BBB|pub123" is a cache hit (value 1.0)
	// All others are cache misses.
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(highValueModelIdentifier, "deal-AAA|pub123").
		Return(nil, false).Once()
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(highValueModelIdentifier, "deal-BBB|pub123").
		Return(float32(1.0), true).Once()
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(highValueModelIdentifier, "deal-CCC|pub123").
		Return(nil, false).Once()
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(highValueModelIdentifier, "no_deal|pub123").
		Return(nil, false).Once()

	// Execute the full pipeline
	output, err := suite.ruleBasedEvaluator.Evaluate(input)

	// Verify no error and successful evaluation
	suite.NoError(err)
	suite.Equal(interfaces.ModelEvaluationStatusSuccess, output.Status)

	// Verify the ModelResult
	result := output.ModelResult

	// Value should be 1.0 (first cache hit is deal-BBB|pub123)
	suite.Equal(float32(1.0), result.Value, "Value should be the first cache hit value")

	// Key should be the first-hit key
	suite.Equal("deal-BBB|pub123", result.Key, "Key should be the first cache-hit key")

	// Keys should contain all 4 permutation keys in order
	suite.Equal(expectedKeys, result.Keys, "Keys should contain all permutation keys in BuildKeys order")

	// Values should reflect hit/miss per key (defaultValue=0.0 for HighValue model)
	expectedValues := []float32{0.0, 1.0, 0.0, 0.0}
	suite.Equal(expectedValues, result.Values, "Values should reflect cache hit (1.0) or default (0.0) per key")
}

// TestFullHighValueDealPipeline_AllMiss verifies that when no deals match the cache,
// the result returns the HighValue default (0.0) for all keys.
func (suite *HighValueDealIntegrationSuite) TestFullHighValueDealPipeline_AllMiss() {
	modelDefinition := interfaces.ModelDefinition{
		Identifier:           highValueModelIdentifier,
		Dsp:                  "adsp",
		Name:                 "high-value-deals",
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

	featureFieldValueMap := map[string][]string{
		"$.imp[0].pmp.deals[*].id": {"deal-X", "deal-Y"},
		"$.site.publisher.id":      {"pub456"},
	}

	input := interfaces.ModelEvaluatorInput{
		Context:              interfaces.NewContext(),
		ModelDefinition:      &modelDefinition,
		FeatureFieldValueMap: featureFieldValueMap,
	}

	// After IncludeDefaultValue: dealId values = ["deal-X", "deal-Y", "no_deal"]
	// After GetFirstNotEmpty: publisherId values = ["pub456"]
	// BuildKeys: "deal-X|pub456", "deal-Y|pub456", "no_deal|pub456"
	expectedKeys := []string{
		"deal-X|pub456",
		"deal-Y|pub456",
		"no_deal|pub456",
	}

	// All cache misses
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(highValueModelIdentifier, "deal-X|pub456").
		Return(nil, false).Once()
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(highValueModelIdentifier, "deal-Y|pub456").
		Return(nil, false).Once()
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(highValueModelIdentifier, "no_deal|pub456").
		Return(nil, false).Once()

	output, err := suite.ruleBasedEvaluator.Evaluate(input)

	suite.NoError(err)
	suite.Equal(interfaces.ModelEvaluationStatusSuccess, output.Status)

	result := output.ModelResult

	// All miss → Value is default (0.0), Key is first key in the list
	suite.Equal(float32(0.0), result.Value, "Value should be default (0.0) when all keys miss")
	suite.Equal("deal-X|pub456", result.Key, "Key should be the first permutation key when all miss")
	suite.Equal(expectedKeys, result.Keys)
	suite.Equal([]float32{0.0, 0.0, 0.0}, result.Values, "All values should be default (0.0)")
}

// TestFullHighValueDealPipeline_FirstHitWins verifies that the first cache hit in BuildKeys order
// determines the overall result value even when multiple keys are hits.
func (suite *HighValueDealIntegrationSuite) TestFullHighValueDealPipeline_FirstHitWins() {
	modelDefinition := interfaces.ModelDefinition{
		Identifier:           highValueModelIdentifier,
		Dsp:                  "adsp",
		Name:                 "high-value-deals",
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

	featureFieldValueMap := map[string][]string{
		"$.imp[0].pmp.deals[*].id": {"deal-1", "deal-2"},
		"$.site.publisher.id":      {"pub789"},
	}

	input := interfaces.ModelEvaluatorInput{
		Context:              interfaces.NewContext(),
		ModelDefinition:      &modelDefinition,
		FeatureFieldValueMap: featureFieldValueMap,
	}

	// BuildKeys: "deal-1|pub789", "deal-2|pub789", "no_deal|pub789"
	// Both deal-1 and deal-2 are cache hits, but deal-1 is first
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(highValueModelIdentifier, "deal-1|pub789").
		Return(float32(1.0), true).Once()
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(highValueModelIdentifier, "deal-2|pub789").
		Return(float32(1.0), true).Once()
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(highValueModelIdentifier, "no_deal|pub789").
		Return(nil, false).Once()

	output, err := suite.ruleBasedEvaluator.Evaluate(input)

	suite.NoError(err)
	suite.Equal(interfaces.ModelEvaluationStatusSuccess, output.Status)

	result := output.ModelResult

	// First hit is deal-1|pub789
	suite.Equal(float32(1.0), result.Value)
	suite.Equal("deal-1|pub789", result.Key, "Key should be the first cache-hit key in order")
	suite.Equal([]string{"deal-1|pub789", "deal-2|pub789", "no_deal|pub789"}, result.Keys)
	suite.Equal([]float32{1.0, 1.0, 0.0}, result.Values)
}
