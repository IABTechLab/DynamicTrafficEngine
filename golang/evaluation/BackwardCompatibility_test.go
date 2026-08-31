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

func TestBackwardCompatibilitySuite(t *testing.T) {
	suite.Run(t, new(BackwardCompatibilitySuite))
}

type BackwardCompatibilitySuite struct {
	suite.Suite
}

// TestLowValueModel_SingleValueFeatures_BuildKeyFormat verifies that single-value features
// with a LowValue model produce the same BuildKey output format as before the multi-value changes.
func (suite *BackwardCompatibilitySuite) TestLowValueModel_SingleValueFeatures_BuildKeyFormat() {
	mockLocalCache := mockInterfaces.NewLocalCacheFactoryInterface(suite.T())
	mockDaoFactory := mockInterfaces.NewDaoFactoryInterface(suite.T())
	mockModelConfigHandler := mockInterfaces.NewModelConfigurationHandlerInterface(suite.T())
	mockTimeProvider := mockInterfaces.NewTimeProvider(suite.T())

	handler := modelfeature.NewModelResultHandler(
		"ssp", "testdata", mockDaoFactory, mockModelConfigHandler, mockLocalCache, mockTimeProvider,
	)

	// Single-value features mimicking the LowValue model's extracted features after transformation
	features := []interfaces.ModelFeature{
		{Values: []string{"site"}},
		{Values: []string{"banner"}},
		{Values: []string{"539014228"}},
		{Values: []string{"USA"}},
		{Values: []string{"970x250"}},
		{Values: []string{"a"}},
		{Values: []string{"2"}},
	}

	// BuildKey should produce the traditional pipe-delimited key
	expectedKey := "site|banner|539014228|USA|970x250|a|2"
	key := handler.BuildKey(features)
	suite.Equal(expectedKey, key, "BuildKey output format should match traditional single-key format")

	// BuildKeys should produce exactly 1 key matching BuildKey
	keys := handler.BuildKeys(features)
	suite.Equal(1, len(keys), "Single-value features must produce exactly 1 key in BuildKeys")
	suite.Equal(expectedKey, keys[0], "BuildKeys[0] must match BuildKey for single-value features")
}

// TestLowValueModel_Provide_CacheHit verifies that Provide returns Value=0.0 (cache hit value)
// with defaultValue=1.0 for a LowValue model, and the result preserves backward-compatible fields.
func (suite *BackwardCompatibilitySuite) TestLowValueModel_Provide_CacheHit() {
	mockLocalCache := mockInterfaces.NewLocalCacheFactoryInterface(suite.T())
	mockDaoFactory := mockInterfaces.NewDaoFactoryInterface(suite.T())
	mockModelConfigHandler := mockInterfaces.NewModelConfigurationHandlerInterface(suite.T())
	mockTimeProvider := mockInterfaces.NewTimeProvider(suite.T())

	handler := modelfeature.NewModelResultHandler(
		"ssp", "testdata", mockDaoFactory, mockModelConfigHandler, mockLocalCache, mockTimeProvider,
	)

	features := []interfaces.ModelFeature{
		{Values: []string{"site"}},
		{Values: []string{"banner"}},
		{Values: []string{"539014228"}},
		{Values: []string{"USA"}},
		{Values: []string{"970x250"}},
		{Values: []string{"a"}},
		{Values: []string{"2"}},
	}

	expectedKey := "site|banner|539014228|USA|970x250|a|2"
	cacheHitValue := float32(0.0)
	defaultValue := float32(1.0)

	// Mock a cache hit for the single key
	mockLocalCache.EXPECT().
		GetFromLocalCache("adsp_low-value_v2", expectedKey).
		Return(cacheHitValue, true).
		Once()

	result, err := handler.Provide("adsp_low-value_v2", features, defaultValue)

	suite.NoError(err)
	// Value should be the cached value (0.0) since it was a hit
	suite.Equal(cacheHitValue, result.Value, "Value should be cache hit value (0.0) for LowValue model")
	// Key should be the traditional single key
	suite.Equal(expectedKey, result.Key, "Key should match the single traditional key")
	// Keys array should have exactly 1 element
	suite.Equal(1, len(result.Keys), "Keys must have exactly 1 element for single-value features")
	suite.Equal(expectedKey, result.Keys[0], "Keys[0] must match the traditional key format")
	// Values array should have exactly 1 element matching the cache hit value
	suite.Equal(1, len(result.Values), "Values must have exactly 1 element for single-value features")
	suite.Equal(cacheHitValue, result.Values[0], "Values[0] must match the cache hit value")
}

// TestLowValueModel_Provide_CacheMiss verifies that Provide returns defaultValue=1.0
// when the key is not found in cache for a LowValue model.
func (suite *BackwardCompatibilitySuite) TestLowValueModel_Provide_CacheMiss() {
	mockLocalCache := mockInterfaces.NewLocalCacheFactoryInterface(suite.T())
	mockDaoFactory := mockInterfaces.NewDaoFactoryInterface(suite.T())
	mockModelConfigHandler := mockInterfaces.NewModelConfigurationHandlerInterface(suite.T())
	mockTimeProvider := mockInterfaces.NewTimeProvider(suite.T())

	handler := modelfeature.NewModelResultHandler(
		"ssp", "testdata", mockDaoFactory, mockModelConfigHandler, mockLocalCache, mockTimeProvider,
	)

	features := []interfaces.ModelFeature{
		{Values: []string{"site"}},
		{Values: []string{"banner"}},
		{Values: []string{"539014228"}},
		{Values: []string{"USA"}},
		{Values: []string{"970x250"}},
		{Values: []string{"a"}},
		{Values: []string{"2"}},
	}

	expectedKey := "site|banner|539014228|USA|970x250|a|2"
	defaultValue := float32(1.0)

	// Mock a cache miss
	mockLocalCache.EXPECT().
		GetFromLocalCache("adsp_low-value_v2", expectedKey).
		Return(nil, false).
		Once()

	result, err := handler.Provide("adsp_low-value_v2", features, defaultValue)

	suite.NoError(err)
	// Value should be the default (1.0) since it was a miss
	suite.Equal(defaultValue, result.Value, "Value should be defaultValue (1.0) on cache miss for LowValue model")
	// Key should be the first key (traditional key)
	suite.Equal(expectedKey, result.Key, "Key should be the first key on miss")
	// Keys array should have exactly 1 element
	suite.Equal(1, len(result.Keys), "Keys must have exactly 1 element for single-value features")
	suite.Equal(expectedKey, result.Keys[0])
	// Values array should have exactly 1 element matching defaultValue
	suite.Equal(1, len(result.Values), "Values must have exactly 1 element for single-value features")
	suite.Equal(defaultValue, result.Values[0], "Values[0] must be defaultValue on cache miss")
}

// TestLowValueModel_EndToEnd_EvaluateThroughRuleBasedModelEvaluator verifies that the full evaluation
// pipeline produces correct results for a LowValue model with single-value features using a
// model configuration with features that produce single values, and a mocked cache.
func (suite *BackwardCompatibilitySuite) TestLowValueModel_EndToEnd_EvaluateThroughRuleBasedModelEvaluator() {
	mockLocalCache := mockInterfaces.NewLocalCacheFactoryInterface(suite.T())
	mockDaoFactory := mockInterfaces.NewDaoFactoryInterface(suite.T())
	mockModelConfigHandler := mockInterfaces.NewModelConfigurationHandlerInterface(suite.T())
	mockTimeProvider := mockInterfaces.NewTimeProvider(suite.T())

	handler := modelfeature.NewModelResultHandler(
		"ssp", "testdata", mockDaoFactory, mockModelConfigHandler, mockLocalCache, mockTimeProvider,
	)

	evaluator := NewRuleBasedModelEvaluator(handler)

	// Use a model with single-value features that exercise GetFirstNotEmpty
	// (the most common transformer pattern for single-value feature extraction).
	modelDefinition := interfaces.ModelDefinition{
		Identifier:           "adsp_low-value_v2",
		Type:                 "LowValue",
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
				Transformations: []interfaces.TransformerName{},
			},
			{
				Name:            "deviceType",
				Fields:          []string{"$.device.devicetype"},
				Transformations: []interfaces.TransformerName{"GetFirstNotEmpty"},
			},
		},
	}

	// Expected key: "539014228|USA|2" (GetFirstNotEmpty produces a single value each)
	expectedKey := "539014228|USA|2"

	// Mock cache hit for the expected key
	cacheHitValue := float32(0.0)
	mockLocalCache.EXPECT().
		GetFromLocalCache("adsp_low-value_v2", expectedKey).
		Return(cacheHitValue, true).
		Once()

	input := interfaces.ModelEvaluatorInput{
		Context:         interfaces.NewContext(),
		ModelDefinition: &modelDefinition,
		FeatureFieldValueMap: map[string][]string{
			"$.site.publisher.id":  {"539014228"},
			"$.app.publisher.id":   {},
			"$.device.geo.country": {"USA"},
			"$.device.devicetype":  {"2"},
		},
	}

	output, err := evaluator.Evaluate(input)

	suite.NoError(err)
	suite.Equal(interfaces.ModelEvaluationStatusSuccess, output.Status)
	// LowValue model: cache hit produces Value=0.0
	suite.Equal(cacheHitValue, output.ModelResult.Value, "LowValue model cache hit should return 0.0")
	suite.Equal(expectedKey, output.ModelResult.Key, "Key should be the traditional pipe-delimited format")
	suite.Equal(1, len(output.ModelResult.Keys), "Single-value features must produce exactly 1 key")
	suite.Equal(expectedKey, output.ModelResult.Keys[0])
	suite.Equal(1, len(output.ModelResult.Values), "Single-value features must produce exactly 1 value")
	suite.Equal(cacheHitValue, output.ModelResult.Values[0])
}

// TestLowValueModel_EndToEnd_CacheMiss_ReturnsDefaultOne verifies that when the cache misses,
// the LowValue model returns 1.0 (high value default, meaning pass through).
func (suite *BackwardCompatibilitySuite) TestLowValueModel_EndToEnd_CacheMiss_ReturnsDefaultOne() {
	mockLocalCache := mockInterfaces.NewLocalCacheFactoryInterface(suite.T())
	mockDaoFactory := mockInterfaces.NewDaoFactoryInterface(suite.T())
	mockModelConfigHandler := mockInterfaces.NewModelConfigurationHandlerInterface(suite.T())
	mockTimeProvider := mockInterfaces.NewTimeProvider(suite.T())

	handler := modelfeature.NewModelResultHandler(
		"ssp", "testdata", mockDaoFactory, mockModelConfigHandler, mockLocalCache, mockTimeProvider,
	)

	evaluator := NewRuleBasedModelEvaluator(handler)

	// Use a model with single-value features that exercise GetFirstNotEmpty
	modelDefinition := interfaces.ModelDefinition{
		Identifier:           "adsp_low-value_v2",
		Type:                 "LowValue",
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
				Transformations: []interfaces.TransformerName{},
			},
			{
				Name:            "deviceType",
				Fields:          []string{"$.device.devicetype"},
				Transformations: []interfaces.TransformerName{"GetFirstNotEmpty"},
			},
		},
	}

	expectedKey := "539014228|USA|2"

	// Mock cache miss
	mockLocalCache.EXPECT().
		GetFromLocalCache("adsp_low-value_v2", expectedKey).
		Return(nil, false).
		Once()

	input := interfaces.ModelEvaluatorInput{
		Context:         interfaces.NewContext(),
		ModelDefinition: &modelDefinition,
		FeatureFieldValueMap: map[string][]string{
			"$.site.publisher.id":  {"539014228"},
			"$.app.publisher.id":   {},
			"$.device.geo.country": {"USA"},
			"$.device.devicetype":  {"2"},
		},
	}

	output, err := evaluator.Evaluate(input)

	suite.NoError(err)
	suite.Equal(interfaces.ModelEvaluationStatusSuccess, output.Status)
	// LowValue model: cache miss returns defaultValue=1.0
	suite.Equal(float32(1.0), output.ModelResult.Value, "LowValue model cache miss should return 1.0")
	suite.Equal(expectedKey, output.ModelResult.Key, "Key should be the first key when all miss")
	suite.Equal(1, len(output.ModelResult.Keys))
	suite.Equal(expectedKey, output.ModelResult.Keys[0])
	suite.Equal(1, len(output.ModelResult.Values))
	suite.Equal(float32(1.0), output.ModelResult.Values[0], "Values[0] should be 1.0 on cache miss")
}

// TestLowValueModel_DefaultValueDerivation verifies that the LowValue ModelType correctly
// results in a defaultValue of 1.0 being passed to Provide.
func (suite *BackwardCompatibilitySuite) TestLowValueModel_DefaultValueDerivation() {
	// Verify the ModelTypeDefaultValue map produces the correct default for LowValue
	defaultValue, exists := modelfeature.ModelTypeDefaultValue[interfaces.ModelType("LowValue")]
	suite.True(exists, "LowValue should exist in ModelTypeDefaultValue map")
	suite.Equal(float32(1.0), defaultValue, "LowValue default should be 1.0")
}

// TestLowValueModel_EmptyModelType_DefaultsToLowValueBehavior verifies that when the ModelType
// field is absent (empty string), the system defaults to LowValue behavior (defaultValue=1.0).
func (suite *BackwardCompatibilitySuite) TestLowValueModel_EmptyModelType_DefaultsToLowValueBehavior() {
	mockLocalCache := mockInterfaces.NewLocalCacheFactoryInterface(suite.T())
	mockDaoFactory := mockInterfaces.NewDaoFactoryInterface(suite.T())
	mockModelConfigHandler := mockInterfaces.NewModelConfigurationHandlerInterface(suite.T())
	mockTimeProvider := mockInterfaces.NewTimeProvider(suite.T())

	handler := modelfeature.NewModelResultHandler(
		"ssp", "testdata", mockDaoFactory, mockModelConfigHandler, mockLocalCache, mockTimeProvider,
	)

	evaluator := NewRuleBasedModelEvaluator(handler)

	// Create a model definition with empty Type (simulates absent modelType in JSON)
	modelDefinition := interfaces.ModelDefinition{
		Identifier:           "adsp_low-value_v2",
		Type:                 "", // absent/empty defaults to LowValue behavior
		FeatureExtractorType: "JsonExtractor",
		Features: []interfaces.FeatureConfiguration{
			{
				Name:            "publisherId",
				Fields:          []string{"$.site.publisher.id"},
				Transformations: []interfaces.TransformerName{"GetFirstNotEmpty"},
			},
		},
	}

	expectedKey := "539014228"

	// Mock cache miss — should return defaultValue=1.0 (LowValue behavior)
	mockLocalCache.EXPECT().
		GetFromLocalCache("adsp_low-value_v2", expectedKey).
		Return(nil, false).
		Once()

	input := interfaces.ModelEvaluatorInput{
		Context:         interfaces.NewContext(),
		ModelDefinition: &modelDefinition,
		FeatureFieldValueMap: map[string][]string{
			"$.site.publisher.id": {"539014228"},
		},
	}

	output, err := evaluator.Evaluate(input)

	suite.NoError(err)
	suite.Equal(interfaces.ModelEvaluationStatusSuccess, output.Status)
	// Empty ModelType defaults to LowValue behavior: defaultValue=1.0
	suite.Equal(float32(1.0), output.ModelResult.Value, "Empty ModelType should default to 1.0 (LowValue behavior)")
	suite.Equal(expectedKey, output.ModelResult.Key)
	suite.Equal(1, len(output.ModelResult.Keys))
	suite.Equal(1, len(output.ModelResult.Values))
	suite.Equal(float32(1.0), output.ModelResult.Values[0])
}
