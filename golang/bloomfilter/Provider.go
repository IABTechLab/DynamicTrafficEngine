// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package bloomfilter

import (
	"github.com/rs/zerolog"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/modelfeature"
	"golang.a2z.com/demanddriventrafficevaluator/util"
)

var providerLogger zerolog.Logger

func init() {
	providerLogger = util.GetLogger()
}

const keyDelimiter = "|"
const maxKeys = 100

// BloomFilterProviderInterface defines the bloom filter result provider contract.
type BloomFilterProviderInterface interface {
	Provide(modelIdentifier string, features []interfaces.ModelFeature, defaultValue float32) (*interfaces.ModelResult, error)
}

// BloomFilterProvider performs bloom filter membership lookups for model evaluation.
type BloomFilterProvider struct {
	store *BloomFilterStore
}

// NewBloomFilterProvider creates a new BloomFilterProvider with the given store.
func NewBloomFilterProvider(store *BloomFilterStore) *BloomFilterProvider {
	return &BloomFilterProvider{
		store: store,
	}
}

// Provide checks feature tuples against the bloom filter and returns a ModelResult.
// Uses the same Cartesian product key-building logic as ModelResultHandler.BuildKeys.
func (p *BloomFilterProvider) Provide(modelIdentifier string, features []interfaces.ModelFeature, defaultValue float32) (*interfaces.ModelResult, error) {
	keys := buildKeys(features)
	providerLogger.Debug().Msgf("BloomFilterProvider.Provide: modelIdentifier=%s, numFeatures=%d, numKeys=%d, defaultValue=%f", modelIdentifier, len(features), len(keys), defaultValue)

	if len(keys) == 0 {
		providerLogger.Debug().Msgf("BloomFilterProvider.Provide: no keys generated, returning defaultValue=%f", defaultValue)
		return &interfaces.ModelResult{
			Value:  defaultValue,
			Key:    "",
			Keys:   []string{""},
			Values: []float32{defaultValue},
		}, nil
	}

	filter, found := p.store.Get(modelIdentifier)
	if !found {
		providerLogger.Debug().Msgf("BloomFilterProvider.Provide: no bloom filter in store for model=%s, returning defaultValue=%f", modelIdentifier, defaultValue)
		// No bloom filter loaded for this model — return default value for all keys
		values := make([]float32, len(keys))
		for i := range values {
			values[i] = defaultValue
		}
		return &interfaces.ModelResult{
			Value:  defaultValue,
			Key:    keys[0],
			Keys:   keys,
			Values: values,
		}, nil
	}

	// Look up each key in the bloom filter
	allValues := make([]float32, len(keys))
	var firstHitValue float32 = defaultValue
	var firstHitKey string = keys[0]
	hitFound := false

	// Determine the cache hit value from ModelTypeValue map
	// The defaultValue passed in is the miss value (from ModelTypeDefaultValue).
	// The hit value is the inverse — look it up from ModelTypeValue.
	hitValue := getCacheHitValue(defaultValue)

	for i, key := range keys {
		if filter.MightContain(key) {
			allValues[i] = hitValue
			if !hitFound {
				firstHitValue = hitValue
				firstHitKey = key
				hitFound = true
			}
		} else {
			allValues[i] = defaultValue
		}
	}

	providerLogger.Debug().Msgf("BloomFilterProvider.Provide: model=%s, hitFound=%t, firstHitKey=%s, resultValue=%f", modelIdentifier, hitFound, firstHitKey, firstHitValue)

	return &interfaces.ModelResult{
		Value:  firstHitValue,
		Key:    firstHitKey,
		Keys:   keys,
		Values: allValues,
	}, nil
}

// getCacheHitValue derives the hit value from the default (miss) value.
// LowValue: default=1.0, hit=0.0
// HighValue: default=0.0, hit=1.0
func getCacheHitValue(defaultValue float32) float32 {
	// Look up ModelTypeValue to find the hit value that corresponds to the
	// model type whose default value matches the provided defaultValue.
	for modelType, hitVal := range modelfeature.ModelTypeValue {
		defVal, exists := modelfeature.ModelTypeDefaultValue[modelType]
		if exists && defVal == defaultValue {
			return hitVal
		}
	}
	// Fallback: if default is 1.0, hit is 0.0; if default is 0.0, hit is 1.0
	if defaultValue == 1.0 {
		return 0.0
	}
	return 1.0
}

// buildKeys generates all permutation keys from multi-valued features.
// Returns the Cartesian product of all feature value lists, joined by keyDelimiter.
// Capped at maxKeys (100). This is the same logic as ModelResultHandler.BuildKeys.
func buildKeys(modelFeatures []interfaces.ModelFeature) []string {
	if len(modelFeatures) == 0 {
		return []string{}
	}

	// Check for empty value lists
	for _, feature := range modelFeatures {
		if len(feature.Values) == 0 {
			return []string{}
		}
	}

	// Compute Cartesian product iteratively
	keys := []string{""}
	for _, feature := range modelFeatures {
		var newKeys []string
		for _, prefix := range keys {
			for _, value := range feature.Values {
				var key string
				if prefix == "" {
					key = value
				} else {
					key = prefix + keyDelimiter + value
				}
				newKeys = append(newKeys, key)
				if len(newKeys) >= maxKeys {
					return newKeys
				}
			}
		}
		keys = newKeys
	}
	return keys
}
