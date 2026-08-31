// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package evaluation

import (
	"fmt"
	"math"

	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/modelfeature"
)

// ConfigurableAggregator evaluates an AggregationNode tree against model evaluation outputs.
type ConfigurableAggregator struct{}

// NewConfigurableAggregator creates a new ConfigurableAggregator instance.
func NewConfigurableAggregator() *ConfigurableAggregator {
	return &ConfigurableAggregator{}
}

// Aggregate evaluates the aggregation schema tree and returns the combined result.
// It builds a scoreMap from SUCCESS outputs, evaluates the tree, and derives treatment code
// and experiment metadata from the context.
func (a *ConfigurableAggregator) Aggregate(
	schema *interfaces.AggregationNode,
	modelOutputs []interfaces.ModelEvaluatorOutput,
	context *interfaces.Context,
) (*interfaces.AggregatedModelEvaluationResult, error) {
	trafficAllocationContext := context.TrafficAllocationContext
	experimentDef, err := trafficAllocationContext.GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter)
	if err != nil {
		return nil, fmt.Errorf("error while aggregating model evaluation results due to [%+v]", err)
	}
	experimentName := experimentDef.Name

	// Build scoreMap from SUCCESS outputs only
	scoreMap := make(map[string]float32)
	for _, output := range modelOutputs {
		if output.Status == interfaces.ModelEvaluationStatusSuccess {
			scoreMap[output.ModelDefinition.Identifier] = output.ModelResult.Value
		}
	}
	Logger.Debug().Msgf("ConfigurableAggregator input scoreMap: %+v, schema: %+v", scoreMap, schema)

	// Evaluate the aggregation tree
	score := a.evaluateNode(schema, scoreMap)
	Logger.Debug().Msgf("ConfigurableAggregator evaluateNode result: score=%f", score)

	// Derive treatment code and experiment metadata from context
	treatmentCodeInInt := trafficAllocationContext.GetTreatmentCodeInInt(experimentName)
	aggregatedScoreWithTreatment := float32(math.Max(float64(score), float64(treatmentCodeInInt)))
	treatmentCode := trafficAllocationContext.GetTreatmentCode(experimentName)

	result := &interfaces.AggregatedModelEvaluationResult{
		ExperimentName:     "DemandDrivenTrafficEvaluatorSoftFilter",
		ExperimentType:     "soft-filter",
		TreatmentCode:      treatmentCode,
		TreatmentCodeInInt: treatmentCodeInInt,
		Score:              score,
		ScoreWithTreatment: aggregatedScoreWithTreatment,
		AggregationType:    "configurable",
	}
	Logger.Debug().Msgf("ConfigurableAggregator output: score=%f, scoreWithTreatment=%f, treatmentCode=%s", result.Score, result.ScoreWithTreatment, result.TreatmentCode)

	return result, nil
}

// evaluateNode recursively evaluates a single node in the aggregation tree.
// Leaf: lookup modelIdentifier in scoreMap, return score if found, 1.0 if missing (default-forward).
// OR: if any child evaluates to 0.0 → return 0.0; otherwise 1.0.
// AND: if all children evaluate to 0.0 → return 0.0; otherwise 1.0.
func (a *ConfigurableAggregator) evaluateNode(
	node *interfaces.AggregationNode,
	scoreMap map[string]float32,
) float32 {
	if node.IsLeaf() {
		score, exists := scoreMap[node.ModelIdentifier]
		if !exists {
			return 1.0 // default-forward for missing/failed models
		}
		return score
	}

	switch node.Operator {
	case interfaces.AggregationOperatorOR:
		for i := range node.Conditions {
			childScore := a.evaluateNode(&node.Conditions[i], scoreMap)
			if childScore == 0.0 {
				return 0.0
			}
		}
		return 1.0
	case interfaces.AggregationOperatorAND:
		allZero := true
		for i := range node.Conditions {
			childScore := a.evaluateNode(&node.Conditions[i], scoreMap)
			if childScore != 0.0 {
				allZero = false
			}
		}
		if allZero {
			return 0.0
		}
		return 1.0
	default:
		// Unknown operator — default-forward
		return 1.0
	}
}
