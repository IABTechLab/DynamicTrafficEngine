// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package modelfeature

import (
	"fmt"
	"sync"
	"sync/atomic"
	"time"

	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
)

const TreatmentGroupT = "T"
const ExperimentTypeSoftFilter = "soft-filter"

// Implements TrafficAllocatorInterface.
type TrafficAllocator struct {
	experimentConfiguration *interfaces.ExperimentConfiguration
	experimentThresholds    map[string][]uint32
	seed                    uint64
	mu                      sync.RWMutex
}

func NewTrafficAllocator() *TrafficAllocator {
	// Use Unix timestamp with nanoseconds to avoid negative values
	// UnixNano() can be negative for dates before 1970, but Unix() is always positive for current time
	now := time.Now()
	seed := uint64(now.Unix())*1e9 + uint64(now.Nanosecond())
	return &TrafficAllocator{
		seed: seed,
	}
}

func (t *TrafficAllocator) UpdateConfiguration(experimentConfiguration *interfaces.ExperimentConfiguration) error {
	t.mu.Lock()
	defer t.mu.Unlock()

	Logger.Debug().Msgf("Updating experiment configuration: %v", experimentConfiguration)
	t.experimentThresholds = make(map[string][]uint32)
	t.experimentConfiguration = experimentConfiguration
	for experimentName, experimentDefinition := range t.experimentConfiguration.ExperimentDefinitionByName {
		var totalWeight uint32
		for _, t := range experimentDefinition.Treatments {
			totalWeight += t.Weight
		}

		if totalWeight != 100 {
			return fmt.Errorf("total weight must be 100, got %d", totalWeight)
		}

		thresholds := make([]uint32, len(experimentDefinition.Treatments))

		var cumulativeWeight uint32
		for i, t := range experimentDefinition.Treatments {
			cumulativeWeight += t.Weight
			thresholds[i] = cumulativeWeight
		}
		t.experimentThresholds[experimentName] = thresholds
	}

	return nil
}

func (t *TrafficAllocator) GetTrafficAllocationContext() interfaces.TrafficAllocationContextInterface {
	experimentArrangement := make(map[string]string)
	for experimentName, experimentDef := range t.experimentConfiguration.ExperimentDefinitionByName {
		thresholds, exist := t.experimentThresholds[experimentName]
		if !exist {
			Logger.Error().Msgf("No thresholds found for experiment %s", experimentName)
			continue
		}
		treatmentCode, err := t.GetTreatmentCode(thresholds, experimentDef.Treatments)
		if err != nil {
			Logger.Error().Msgf("Failed to get treatment code for experiment %s: %v", experimentName, err)
			continue
		}
		experimentArrangement[experimentName] = treatmentCode
	}
	return NewTrafficAllocationContext(experimentArrangement, t.experimentConfiguration)
}

func (t *TrafficAllocator) GetTreatmentCode(thresholds []uint32, treatments []interfaces.Treatment) (string, error) {
	t.mu.RLock()
	defer t.mu.RUnlock()

	if len(treatments) == 0 {
		return "", fmt.Errorf("no treatments configured")
	}

	r := t.Rand() % 100

	// Binary search
	i, j := 0, len(thresholds)-1
	for i < j {
		// Use safe midpoint calculation to avoid integer overflow
		h := i + (j-i)/2
		if r >= thresholds[h] {
			i = h + 1
		} else {
			j = h
		}
	}
	return treatments[i].TreatmentCode, nil
}

// Rand xorshift64+ algorithm
func (t *TrafficAllocator) Rand() uint32 {
	for {
		old := atomic.LoadUint64(&t.seed)
		x := old
		x ^= x << 21
		x ^= x >> 35
		x ^= x << 4
		if atomic.CompareAndSwapUint64(&t.seed, old, x) {
			// Explicitly mask to 32 bits to avoid overflow warning
			// This is intentional - we only need 32 bits of randomness
			return uint32(x & 0xFFFFFFFF)
		}
	}
}

// Implements TrafficAllocationContextInterface.
type TrafficAllocationContext struct {
	experimentArrangement      map[string]string
	experimentDefinitionByName map[string]interfaces.ExperimentDefinition
	modelToExperiment          map[string]string
	modelsByExperiment         map[string][]string
}

func NewTrafficAllocationContext(experimentArrangement map[string]string, configuration *interfaces.ExperimentConfiguration) *TrafficAllocationContext {
	modelsByExperiment := make(map[string][]string)
	for model, experiment := range configuration.ModelToExperiment {
		modelsByExperiment[experiment] = append(modelsByExperiment[experiment], model)
	}

	return &TrafficAllocationContext{
		experimentArrangement:      experimentArrangement,
		experimentDefinitionByName: configuration.ExperimentDefinitionByName,
		modelToExperiment:          configuration.ModelToExperiment,
		modelsByExperiment:         modelsByExperiment,
	}
}

func (t *TrafficAllocationContext) GetModelIdentifiers() []string {
	models := make([]string, 0, len(t.modelToExperiment))
	for model := range t.modelToExperiment {
		models = append(models, model)
	}
	return models
}

func (t *TrafficAllocationContext) GetExperimentArrangement() map[string]string {
	return t.experimentArrangement
}

func (t *TrafficAllocationContext) GetTreatmentCode(experimentName string) string {
	if code, ok := t.experimentArrangement[experimentName]; ok {
		return code
	}
	return ""
}

func (t *TrafficAllocationContext) GetTreatmentCodeInInt(experimentName string) int8 {
	if t.experimentArrangement[experimentName] == TreatmentGroupT {
		return 0
	}
	return 1
}

func (t *TrafficAllocationContext) GetExperimentDefinition(experimentName string) interfaces.ExperimentDefinition {
	return t.experimentDefinitionByName[experimentName]
}

func (t *TrafficAllocationContext) GetExperimentDefinitionByModel(model string) interfaces.ExperimentDefinition {
	experimentName := t.modelToExperiment[model]
	return t.experimentDefinitionByName[experimentName]
}

func (t *TrafficAllocationContext) GetExperimentDefinitionByType(experimentType string) (*interfaces.ExperimentDefinition, error) {
	for _, def := range t.experimentDefinitionByName {
		if def.Type == experimentType {
			return &def, nil
		}
	}
	return nil, fmt.Errorf("ExperimentDefinition with type [%s] not found", experimentType)
}

func (t *TrafficAllocationContext) GetModelsByExperiment() map[string][]string {
	return t.modelsByExperiment
}
