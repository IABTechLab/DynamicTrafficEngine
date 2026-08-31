// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package evaluation

import (
	"fmt"
	"testing"

	bloom "github.com/OldPanda/bloomfilter"
	"github.com/stretchr/testify/mock"
	"github.com/stretchr/testify/suite"
	"golang.a2z.com/demanddriventrafficevaluator/bloomfilter"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/modelfeature"
)

const (
	bfIntegrationBloomModelId     = "adsp_rsp_v1"
	bfIntegrationRuleBasedModelId = "adsp_low-value_v2"
	bfIntegrationExperimentName   = "DemandDrivenTrafficEvaluatorSoftFilter"
)

func TestBloomFilterIntegrationSuite(t *testing.T) {
	suite.Run(t, new(BloomFilterIntegrationSuite))
}

type BloomFilterIntegrationSuite struct {
	suite.Suite
	bloomFilterStore             *bloomfilter.BloomFilterStore
	bloomFilterProvider          *bloomfilter.BloomFilterProvider
	bloomFilterEvaluator         *BloomFilterModelEvaluator
	delegatingEvaluator          *DelegatingModelEvaluator
	configurableAggregator       *ConfigurableAggregator
	mockTrafficAllocator         *mockInterfaces.TrafficAllocatorInterface
	mockTrafficAllocationContext *mockInterfaces.TrafficAllocationContextInterface
	mockModelConfigHandler       *mockInterfaces.ModelConfigurationHandlerInterface
	mockLocalCacheFactory        *mockInterfaces.LocalCacheFactoryInterface
}

func (suite *BloomFilterIntegrationSuite) SetupTest() {
	// Create real bloom filter infrastructure
	suite.bloomFilterStore = bloomfilter.NewBloomFilterStore()
	suite.bloomFilterProvider = bloomfilter.NewBloomFilterProvider(suite.bloomFilterStore)
	suite.bloomFilterEvaluator = NewBloomFilterModelEvaluator(suite.bloomFilterProvider)

	// Create mocks for rule-based evaluator dependencies
	suite.mockLocalCacheFactory = mockInterfaces.NewLocalCacheFactoryInterface(suite.T())
	suite.mockTrafficAllocator = mockInterfaces.NewTrafficAllocatorInterface(suite.T())
	suite.mockTrafficAllocationContext = mockInterfaces.NewTrafficAllocationContextInterface(suite.T())
	suite.mockModelConfigHandler = mockInterfaces.NewModelConfigurationHandlerInterface(suite.T())

	// Create the configurable aggregator
	suite.configurableAggregator = NewConfigurableAggregator()
}

func (suite *BloomFilterIntegrationSuite) createBloomFilter(keys ...string) *bloom.BloomFilter {
	filter, err := bloom.NewBloomFilter(1000, 0.01)
	suite.Require().NoError(err)
	for _, key := range keys {
		filter.Put(key)
	}
	return filter
}

// TestEndToEndBloomFilterEvaluation exercises the full pipeline:
// model definition with BLOOM_FILTER format → DelegatingModelEvaluator routes to
// BloomFilterModelEvaluator → provider checks store → returns correct ModelResult
func (suite *BloomFilterIntegrationSuite) TestEndToEndBloomFilterEvaluation() {
	// Populate the bloom filter store with a filter containing known keys
	knownKey := "pub123|USA"
	filter := suite.createBloomFilter(knownKey)
	suite.bloomFilterStore.Put(bfIntegrationBloomModelId, filter)

	// Create a mock rule-based evaluator for the delegating evaluator
	mockRuleBasedEvaluator := mockInterfaces.NewModelEvaluator(suite.T())

	// Build the delegating evaluator with both real bloom filter evaluator and mock rule-based
	suite.delegatingEvaluator = NewDelegatingModelEvaluator(map[string]interfaces.ModelEvaluator{
		interfaces.ModelFormatRuleBased:   mockRuleBasedEvaluator,
		interfaces.ModelFormatBloomFilter: suite.bloomFilterEvaluator,
	})

	// Model definition with BLOOM_FILTER format (LowValue: hit → 0.0, miss → 1.0)
	modelDefinition := interfaces.ModelDefinition{
		Identifier:           bfIntegrationBloomModelId,
		Dsp:                  "adsp",
		Name:                 "rsp",
		Version:              "v1",
		Type:                 "LowValue",
		ModelFormat:          interfaces.ModelFormatBloomFilter,
		S3PathMode:           interfaces.S3PathModeStatic,
		FeatureExtractorType: "JsonExtractor",
		Features: []interfaces.FeatureConfiguration{
			{
				Name:            "publisherId",
				Fields:          []string{"$.site.publisher.id", "$.app.publisher.id"},
				Transformations: []interfaces.TransformerName{"GetFirstNotEmpty"},
			},
			{
				Name:            "country",
				Fields:          []string{"$.device.geo.country"},
				Transformations: []interfaces.TransformerName{"GetFirstNotEmpty"},
			},
		},
	}

	// Build feature field value map simulating parsed OpenRTB request
	featureFieldValueMap := map[string][]string{
		"$.site.publisher.id":  {"pub123"},
		"$.app.publisher.id":   {},
		"$.device.geo.country": {"USA"},
	}

	input := interfaces.ModelEvaluatorInput{
		Context:              interfaces.NewContext(),
		OpenRtbRequest:       `{"site":{"publisher":{"id":"pub123"}},"device":{"geo":{"country":"USA"}}}`,
		ModelDefinition:      &modelDefinition,
		FeatureFieldValueMap: featureFieldValueMap,
	}

	// Execute through the delegating evaluator
	output, err := suite.delegatingEvaluator.Evaluate(input)

	// Verify no error and successful evaluation
	suite.NoError(err)
	suite.Equal(interfaces.ModelEvaluationStatusSuccess, output.Status)

	// Verify the ModelResult
	result := output.ModelResult

	// The key "pub123|USA" is in the bloom filter → LowValue hit value is 0.0
	suite.Equal(float32(0.0), result.Value, "Value should be 0.0 (LowValue hit) because key is in bloom filter")
	suite.Equal(knownKey, result.Key, "Key should be the matching tuple")
	suite.Equal([]string{knownKey}, result.Keys, "Keys should contain the single permutation key")
	suite.Equal([]float32{0.0}, result.Values, "Values should be [0.0] for the hit key")

	// Verify model definition is passed through
	suite.Equal(bfIntegrationBloomModelId, output.ModelDefinition.Identifier)
	suite.Equal(interfaces.ModelFormatBloomFilter, output.ModelDefinition.ModelFormat)
}

// TestEndToEndBloomFilterEvaluation_Miss verifies that when a key is NOT in the bloom filter,
// the default value is returned (1.0 for LowValue = forward).
func (suite *BloomFilterIntegrationSuite) TestEndToEndBloomFilterEvaluation_Miss() {
	// Populate store with a filter that does NOT contain the lookup key
	filter := suite.createBloomFilter("other_key|other_country")
	suite.bloomFilterStore.Put(bfIntegrationBloomModelId, filter)

	mockRuleBasedEvaluator := mockInterfaces.NewModelEvaluator(suite.T())
	suite.delegatingEvaluator = NewDelegatingModelEvaluator(map[string]interfaces.ModelEvaluator{
		interfaces.ModelFormatRuleBased:   mockRuleBasedEvaluator,
		interfaces.ModelFormatBloomFilter: suite.bloomFilterEvaluator,
	})

	modelDefinition := interfaces.ModelDefinition{
		Identifier:           bfIntegrationBloomModelId,
		Dsp:                  "adsp",
		Name:                 "rsp",
		Version:              "v1",
		Type:                 "LowValue",
		ModelFormat:          interfaces.ModelFormatBloomFilter,
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
		"$.site.publisher.id": {"pub999"},
	}

	input := interfaces.ModelEvaluatorInput{
		Context:              interfaces.NewContext(),
		ModelDefinition:      &modelDefinition,
		FeatureFieldValueMap: featureFieldValueMap,
	}

	output, err := suite.delegatingEvaluator.Evaluate(input)

	suite.NoError(err)
	suite.Equal(interfaces.ModelEvaluationStatusSuccess, output.Status)

	// Key "pub999" is NOT in the bloom filter → LowValue default (miss) value = 1.0
	suite.Equal(float32(1.0), output.ModelResult.Value, "Value should be 1.0 (LowValue default/forward) on miss")
	suite.Equal("pub999", output.ModelResult.Key, "Key should be the first (and only) permutation key")
}

// TestMixedAggregation_ORCombinesBloomFilterAndRuleBased sets up a configurable aggregation
// tree (OR) that combines results from a bloom filter model (hit → 0.0) and a rule-based model
// (forward → 1.0), verifying OR produces 0.0 (filter) because one child is 0.0.
func (suite *BloomFilterIntegrationSuite) TestMixedAggregation_ORCombinesBloomFilterAndRuleBased() {
	// Populate bloom filter store: the bloom filter model will produce a hit (0.0)
	filter := suite.createBloomFilter("pub123")
	suite.bloomFilterStore.Put(bfIntegrationBloomModelId, filter)

	// Build the delegating evaluator with a real bloom filter evaluator and mock rule-based
	mockRuleBasedEvaluator := mockInterfaces.NewModelEvaluator(suite.T())
	suite.delegatingEvaluator = NewDelegatingModelEvaluator(map[string]interfaces.ModelEvaluator{
		interfaces.ModelFormatRuleBased:   mockRuleBasedEvaluator,
		interfaces.ModelFormatBloomFilter: suite.bloomFilterEvaluator,
	})

	// Define the bloom filter model
	bloomModelDef := interfaces.ModelDefinition{
		Identifier:           bfIntegrationBloomModelId,
		Dsp:                  "adsp",
		Name:                 "rsp",
		Version:              "v1",
		Type:                 "LowValue",
		ModelFormat:          interfaces.ModelFormatBloomFilter,
		FeatureExtractorType: "JsonExtractor",
		Features: []interfaces.FeatureConfiguration{
			{
				Name:            "publisherId",
				Fields:          []string{"$.site.publisher.id"},
				Transformations: []interfaces.TransformerName{"GetFirstNotEmpty"},
			},
		},
	}

	// Evaluate bloom filter model through delegating evaluator
	bloomInput := interfaces.ModelEvaluatorInput{
		Context:              interfaces.NewContext(),
		ModelDefinition:      &bloomModelDef,
		FeatureFieldValueMap: map[string][]string{"$.site.publisher.id": {"pub123"}},
	}
	bloomOutput, err := suite.delegatingEvaluator.Evaluate(bloomInput)
	suite.NoError(err)
	suite.Equal(interfaces.ModelEvaluationStatusSuccess, bloomOutput.Status)
	suite.Equal(float32(0.0), bloomOutput.ModelResult.Value, "Bloom filter should produce 0.0 (hit)")

	// Simulate a rule-based model output that forwards (1.0)
	ruleBasedOutput := interfaces.ModelEvaluatorOutput{
		Status: interfaces.ModelEvaluationStatusSuccess,
		ModelResult: interfaces.ModelResult{
			Value: 1.0,
			Key:   "some_key",
		},
		ModelDefinition: interfaces.ModelDefinition{
			Identifier: bfIntegrationRuleBasedModelId,
		},
	}

	// Create an OR aggregation schema combining both models
	orSchema := &interfaces.AggregationNode{
		Operator: interfaces.AggregationOperatorOR,
		Conditions: []interfaces.AggregationNode{
			{ModelIdentifier: bfIntegrationBloomModelId},
			{ModelIdentifier: bfIntegrationRuleBasedModelId},
		},
	}

	// Set up traffic allocation context mocks for the aggregator
	context := interfaces.NewContext()
	context.TrafficAllocationContext = suite.mockTrafficAllocationContext

	suite.mockTrafficAllocationContext.EXPECT().
		GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter).
		Return(&interfaces.ExperimentDefinition{
			Name: bfIntegrationExperimentName,
			Type: modelfeature.ExperimentTypeSoftFilter,
		}, nil).Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetTreatmentCodeInInt(bfIntegrationExperimentName).
		Return(int8(0)).Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetTreatmentCode(bfIntegrationExperimentName).
		Return("T").Once()

	// Aggregate with OR schema
	modelOutputs := []interfaces.ModelEvaluatorOutput{*bloomOutput, ruleBasedOutput}
	aggregatedResult, err := suite.configurableAggregator.Aggregate(orSchema, modelOutputs, context)

	suite.NoError(err)
	suite.NotNil(aggregatedResult)

	// OR: any child is 0.0 → result is 0.0 (filter)
	suite.Equal(float32(0.0), aggregatedResult.Score, "OR aggregation should produce 0.0 because bloom filter hit is 0.0")
	suite.Equal("configurable", aggregatedResult.AggregationType)
	suite.Equal(bfIntegrationExperimentName, aggregatedResult.ExperimentName)
}

// TestDefaultForwardWhenAllEvaluationsFail verifies that when all evaluators return errors,
// the RequestEvaluator returns the default forward response (filterDecision 1.0).
func (suite *BloomFilterIntegrationSuite) TestDefaultForwardWhenAllEvaluationsFail() {
	// Create a mock evaluator that returns errors for both models
	mockEvaluator := mockInterfaces.NewModelEvaluator(suite.T())

	bloomModelDef := interfaces.ModelDefinition{
		Identifier:  bfIntegrationBloomModelId,
		ModelFormat: interfaces.ModelFormatBloomFilter,
		Type:        "LowValue",
	}
	ruleBasedModelDef := interfaces.ModelDefinition{
		Identifier:  bfIntegrationRuleBasedModelId,
		ModelFormat: interfaces.ModelFormatRuleBased,
		Type:        "LowValue",
	}

	// Both evaluators return errors
	mockEvaluator.EXPECT().
		Evaluate(mock.MatchedBy(func(input interfaces.ModelEvaluatorInput) bool {
			return input.ModelDefinition.Identifier == bfIntegrationBloomModelId
		})).
		Return(&interfaces.ModelEvaluatorOutput{
			Status: interfaces.ModelEvaluationStatusError,
		}, fmt.Errorf("bloom filter evaluation failed")).Once()

	mockEvaluator.EXPECT().
		Evaluate(mock.MatchedBy(func(input interfaces.ModelEvaluatorInput) bool {
			return input.ModelDefinition.Identifier == bfIntegrationRuleBasedModelId
		})).
		Return(&interfaces.ModelEvaluatorOutput{
			Status: interfaces.ModelEvaluationStatusError,
		}, fmt.Errorf("rule-based evaluation failed")).Once()

	// Set up model configuration
	modelConfig := &interfaces.ModelConfiguration{
		ModelDefinitionByIdentifier: map[string]interfaces.ModelDefinition{
			bfIntegrationBloomModelId: bloomModelDef,
			bfIntegrationRuleBasedModelId:   ruleBasedModelDef,
		},
	}

	suite.mockModelConfigHandler.EXPECT().
		Provide().
		Return(modelConfig, nil).Once()
	suite.mockModelConfigHandler.EXPECT().
		GetAllUniqueFeatureFields().
		Return([]string{"$.site.publisher.id"}, nil).Once()

	// Set up traffic allocation context
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelIdentifiers().
		Return([]string{bfIntegrationBloomModelId, bfIntegrationRuleBasedModelId}).Once()

	suite.mockTrafficAllocator.EXPECT().
		GetTrafficAllocationContext().
		Return(suite.mockTrafficAllocationContext).Once()

	// Build the RequestEvaluator with the mock evaluator
	requestEvaluator := NewRequestEvaluator(
		"ssp",
		suite.mockTrafficAllocator,
		mockEvaluator,
		suite.mockModelConfigHandler,
		suite.configurableAggregator,
	)

	requestInput := &BidRequestEvaluatorInput{
		OpenRtbRequest: `{"site":{"publisher":{"id":"pub123"}}}`,
	}

	// Execute evaluation
	output := requestEvaluator.Evaluate(requestInput)

	// When all models fail, RequestEvaluator returns the DefaultResponse (filterDecision 1.0)
	suite.NotNil(output)
	suite.Equal(DefaultFilterRecommendation, output.Response.Slots[0].FilterDecision,
		"Should return default forward (1.0) when all model evaluations fail")
}
