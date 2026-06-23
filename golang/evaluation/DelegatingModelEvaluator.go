// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package evaluation

import (
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
)

// DelegatingModelEvaluator routes Evaluate calls to the appropriate evaluator
// based on ModelDefinition.ModelFormat.
type DelegatingModelEvaluator struct {
	evaluators       map[string]interfaces.ModelEvaluator
	defaultEvaluator interfaces.ModelEvaluator
}

// NewDelegatingModelEvaluator creates a DelegatingModelEvaluator with the given evaluator map.
// The default evaluator is set to the evaluator registered for ModelFormatRuleBased.
func NewDelegatingModelEvaluator(evaluators map[string]interfaces.ModelEvaluator) *DelegatingModelEvaluator {
	return &DelegatingModelEvaluator{
		evaluators:       evaluators,
		defaultEvaluator: evaluators[interfaces.ModelFormatRuleBased],
	}
}

// Evaluate delegates to the evaluator registered for the model's format.
// Falls back to defaultEvaluator (RULE_BASED) if format is unrecognized or empty.
func (d *DelegatingModelEvaluator) Evaluate(input interfaces.ModelEvaluatorInput) (*interfaces.ModelEvaluatorOutput, error) {
	modelFormat := input.ModelDefinition.ModelFormat

	if modelFormat != "" {
		if evaluator, exists := d.evaluators[modelFormat]; exists {
			return evaluator.Evaluate(input)
		}
	}

	return d.defaultEvaluator.Evaluate(input)
}
