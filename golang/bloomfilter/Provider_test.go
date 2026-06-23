// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package bloomfilter

import (
	"testing"

	bloom "github.com/OldPanda/bloomfilter"
	"github.com/stretchr/testify/suite"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
)

func TestBloomFilterProviderSuite(t *testing.T) {
	suite.Run(t, new(BloomFilterProviderSuite))
}

type BloomFilterProviderSuite struct {
	suite.Suite
	store    *BloomFilterStore
	provider *BloomFilterProvider
}

func (s *BloomFilterProviderSuite) SetupTest() {
	s.store = NewBloomFilterStore()
	s.provider = NewBloomFilterProvider(s.store)
}

func (s *BloomFilterProviderSuite) createTestFilter(keys ...string) *bloom.BloomFilter {
	filter, err := bloom.NewBloomFilter(1000, 0.0001)
	s.Require().NoError(err)
	for _, key := range keys {
		filter.Put(key)
	}
	return filter
}

// --- Membership hit returns cache hit value ---

func (s *BloomFilterProviderSuite) TestMembershipHit_LowValue_ReturnsCacheHitValue() {
	// LowValue: defaultValue=1.0, hitValue=0.0
	filter := s.createTestFilter("pub123|site456")
	s.store.Put("model_a", filter)

	features := []interfaces.ModelFeature{
		{Values: []string{"pub123"}},
		{Values: []string{"site456"}},
	}

	result, err := s.provider.Provide("model_a", features, 1.0)

	s.NoError(err)
	s.Equal(float32(0.0), result.Value)
	s.Equal("pub123|site456", result.Key)
}

func (s *BloomFilterProviderSuite) TestMembershipHit_HighValue_ReturnsCacheHitValue() {
	// HighValue: defaultValue=0.0, hitValue=1.0
	filter := s.createTestFilter("pub123|site456")
	s.store.Put("model_b", filter)

	features := []interfaces.ModelFeature{
		{Values: []string{"pub123"}},
		{Values: []string{"site456"}},
	}

	result, err := s.provider.Provide("model_b", features, 0.0)

	s.NoError(err)
	s.Equal(float32(1.0), result.Value)
	s.Equal("pub123|site456", result.Key)
}

// --- Membership miss returns default value ---

func (s *BloomFilterProviderSuite) TestMembershipMiss_LowValue_ReturnsDefaultValue() {
	// LowValue: defaultValue=1.0, key not in filter
	filter := s.createTestFilter("other_key")
	s.store.Put("model_a", filter)

	features := []interfaces.ModelFeature{
		{Values: []string{"pub999"}},
		{Values: []string{"site888"}},
	}

	result, err := s.provider.Provide("model_a", features, 1.0)

	s.NoError(err)
	s.Equal(float32(1.0), result.Value)
	s.Equal("pub999|site888", result.Key) // first key when all miss
}

func (s *BloomFilterProviderSuite) TestMembershipMiss_HighValue_ReturnsDefaultValue() {
	// HighValue: defaultValue=0.0, key not in filter
	filter := s.createTestFilter("other_key")
	s.store.Put("model_b", filter)

	features := []interfaces.ModelFeature{
		{Values: []string{"pub999"}},
		{Values: []string{"site888"}},
	}

	result, err := s.provider.Provide("model_b", features, 0.0)

	s.NoError(err)
	s.Equal(float32(0.0), result.Value)
	s.Equal("pub999|site888", result.Key) // first key when all miss
}

// --- First-hit-wins when multiple keys match ---

func (s *BloomFilterProviderSuite) TestFirstHitWins_MultipleKeysMatch() {
	// Insert multiple keys but only the second permutation should be the first hit
	filter := s.createTestFilter("pub1|siteB", "pub2|siteA")
	s.store.Put("model_a", filter)

	// Features with multiple values produce Cartesian product:
	// pub1|siteA, pub1|siteB, pub2|siteA, pub2|siteB
	features := []interfaces.ModelFeature{
		{Values: []string{"pub1", "pub2"}},
		{Values: []string{"siteA", "siteB"}},
	}

	result, err := s.provider.Provide("model_a", features, 1.0)

	s.NoError(err)
	// First hit in Cartesian order: pub1|siteB (index 1)
	s.Equal(float32(0.0), result.Value)
	s.Equal("pub1|siteB", result.Key)
}

func (s *BloomFilterProviderSuite) TestFirstHitWins_FirstKeyIsHit() {
	filter := s.createTestFilter("pub1|siteA")
	s.store.Put("model_a", filter)

	features := []interfaces.ModelFeature{
		{Values: []string{"pub1", "pub2"}},
		{Values: []string{"siteA", "siteB"}},
	}

	result, err := s.provider.Provide("model_a", features, 1.0)

	s.NoError(err)
	// First key pub1|siteA is a hit
	s.Equal(float32(0.0), result.Value)
	s.Equal("pub1|siteA", result.Key)
}

// --- All-miss returns default value with first key as Key ---

func (s *BloomFilterProviderSuite) TestAllMiss_ReturnsDefaultValue_WithFirstKeyAsKey() {
	filter := s.createTestFilter("completely_unrelated_key")
	s.store.Put("model_a", filter)

	features := []interfaces.ModelFeature{
		{Values: []string{"pub1", "pub2"}},
		{Values: []string{"siteA", "siteB"}},
	}

	result, err := s.provider.Provide("model_a", features, 1.0)

	s.NoError(err)
	s.Equal(float32(1.0), result.Value)
	s.Equal("pub1|siteA", result.Key) // first key from BuildKeys output
}

// --- Missing bloom filter in store returns default value ---

func (s *BloomFilterProviderSuite) TestMissingBloomFilter_LowValue_ReturnsDefaultValue() {
	// No filter stored for this model
	features := []interfaces.ModelFeature{
		{Values: []string{"pub1"}},
		{Values: []string{"site1"}},
	}

	result, err := s.provider.Provide("nonexistent_model", features, 1.0)

	s.NoError(err)
	s.Equal(float32(1.0), result.Value)
	s.Equal("pub1|site1", result.Key)
}

func (s *BloomFilterProviderSuite) TestMissingBloomFilter_HighValue_ReturnsDefaultValue() {
	// No filter stored for this model
	features := []interfaces.ModelFeature{
		{Values: []string{"pub1"}},
		{Values: []string{"site1"}},
	}

	result, err := s.provider.Provide("nonexistent_model", features, 0.0)

	s.NoError(err)
	s.Equal(float32(0.0), result.Value)
	s.Equal("pub1|site1", result.Key)
}

func (s *BloomFilterProviderSuite) TestMissingBloomFilter_AllKeysGetDefaultValue() {
	features := []interfaces.ModelFeature{
		{Values: []string{"pub1", "pub2"}},
		{Values: []string{"site1"}},
	}

	result, err := s.provider.Provide("nonexistent_model", features, 1.0)

	s.NoError(err)
	s.Require().Len(result.Keys, 2)
	s.Require().Len(result.Values, 2)
	s.Equal(float32(1.0), result.Values[0])
	s.Equal(float32(1.0), result.Values[1])
}

// --- Empty features returns default ModelResult with empty key ---

func (s *BloomFilterProviderSuite) TestEmptyFeatures_ReturnsDefaultModelResult() {
	filter := s.createTestFilter("some_key")
	s.store.Put("model_a", filter)

	features := []interfaces.ModelFeature{}

	result, err := s.provider.Provide("model_a", features, 1.0)

	s.NoError(err)
	s.Equal(float32(1.0), result.Value)
	s.Equal("", result.Key)
	s.Equal([]string{""}, result.Keys)
	s.Equal([]float32{1.0}, result.Values)
}

func (s *BloomFilterProviderSuite) TestEmptyFeatureValues_ReturnsDefaultModelResult() {
	// Feature with no values produces no keys
	filter := s.createTestFilter("some_key")
	s.store.Put("model_a", filter)

	features := []interfaces.ModelFeature{
		{Values: []string{}},
	}

	result, err := s.provider.Provide("model_a", features, 0.0)

	s.NoError(err)
	s.Equal(float32(0.0), result.Value)
	s.Equal("", result.Key)
	s.Equal([]string{""}, result.Keys)
	s.Equal([]float32{0.0}, result.Values)
}

func (s *BloomFilterProviderSuite) TestNilFeatures_ReturnsDefaultModelResult() {
	filter := s.createTestFilter("some_key")
	s.store.Put("model_a", filter)

	result, err := s.provider.Provide("model_a", nil, 1.0)

	s.NoError(err)
	s.Equal(float32(1.0), result.Value)
	s.Equal("", result.Key)
	s.Equal([]string{""}, result.Keys)
	s.Equal([]float32{1.0}, result.Values)
}

// --- Keys and Values parallel arrays are populated correctly ---

func (s *BloomFilterProviderSuite) TestKeysAndValues_ParallelArrays_CorrectlyPopulated() {
	// Filter contains only "pub1|site1" — so first key hits, second misses
	filter := s.createTestFilter("pub1|site1")
	s.store.Put("model_a", filter)

	features := []interfaces.ModelFeature{
		{Values: []string{"pub1"}},
		{Values: []string{"site1", "site2"}},
	}

	result, err := s.provider.Provide("model_a", features, 1.0)

	s.NoError(err)

	// Keys: ["pub1|site1", "pub1|site2"]
	s.Require().Len(result.Keys, 2)
	s.Equal("pub1|site1", result.Keys[0])
	s.Equal("pub1|site2", result.Keys[1])

	// Values: [0.0 (hit), 1.0 (miss)] for LowValue model (defaultValue=1.0)
	s.Require().Len(result.Values, 2)
	s.Equal(float32(0.0), result.Values[0]) // hit → hitValue
	s.Equal(float32(1.0), result.Values[1]) // miss → defaultValue
}

func (s *BloomFilterProviderSuite) TestKeysAndValues_AllHits_AllGetHitValue() {
	// Filter contains both keys
	filter := s.createTestFilter("pub1|site1", "pub1|site2")
	s.store.Put("model_a", filter)

	features := []interfaces.ModelFeature{
		{Values: []string{"pub1"}},
		{Values: []string{"site1", "site2"}},
	}

	result, err := s.provider.Provide("model_a", features, 1.0)

	s.NoError(err)

	s.Require().Len(result.Keys, 2)
	s.Require().Len(result.Values, 2)
	s.Equal(float32(0.0), result.Values[0]) // hit
	s.Equal(float32(0.0), result.Values[1]) // hit
	// First hit wins — first key
	s.Equal(float32(0.0), result.Value)
	s.Equal("pub1|site1", result.Key)
}

func (s *BloomFilterProviderSuite) TestKeysAndValues_HighValueModel_CorrectScoring() {
	// HighValue: defaultValue=0.0, hitValue=1.0
	filter := s.createTestFilter("pub2|site1")
	s.store.Put("model_b", filter)

	features := []interfaces.ModelFeature{
		{Values: []string{"pub1", "pub2"}},
		{Values: []string{"site1"}},
	}

	result, err := s.provider.Provide("model_b", features, 0.0)

	s.NoError(err)

	// Keys: ["pub1|site1", "pub2|site1"]
	s.Require().Len(result.Keys, 2)
	s.Equal("pub1|site1", result.Keys[0])
	s.Equal("pub2|site1", result.Keys[1])

	// Values: [0.0 (miss), 1.0 (hit)] for HighValue
	s.Require().Len(result.Values, 2)
	s.Equal(float32(0.0), result.Values[0]) // miss → defaultValue
	s.Equal(float32(1.0), result.Values[1]) // hit → hitValue

	// First hit is second key
	s.Equal(float32(1.0), result.Value)
	s.Equal("pub2|site1", result.Key)
}

func (s *BloomFilterProviderSuite) TestKeysAndValues_LargeCartesianProduct_CappedAt100() {
	filter := s.createTestFilter("v0|v0|v0")
	s.store.Put("model_a", filter)

	// 11 x 11 x 11 = 1331 permutations, capped at 100
	values := []string{"v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v8", "v9", "v10"}
	features := []interfaces.ModelFeature{
		{Values: values},
		{Values: values},
		{Values: values},
	}

	result, err := s.provider.Provide("model_a", features, 1.0)

	s.NoError(err)
	s.Len(result.Keys, 100)
	s.Len(result.Values, 100)
}
