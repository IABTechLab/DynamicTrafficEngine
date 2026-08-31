// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package evaluation

import (
	"fmt"
	"testing"

	"github.com/stretchr/testify/suite"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
)

func TestDelegatingModelEvaluatorSuite(t *testing.T) {
	suite.Run(t, new(DelegatingModelEvaluatorSuite))
}

type DelegatingModelEvaluatorSuite struct {
	suite.Suite
	mockRuleBasedEvaluator   *mockInterfaces.ModelEvaluator
	mockBloomFilterEvaluator *mockInterfaces.ModelEvaluator
	delegatingEvaluator      *DelegatingModelEvaluator
}

func (s *DelegatingModelEvaluatorSuite) SetupTest() {
	s.mockRuleBasedEvaluator = mockInterfaces.NewModelEvaluator(s.T())
	s.mockBloomFilterEvaluator = mockInterfaces.NewModelEvaluator(s.T())

	evaluators := map[string]interfaces.ModelEvaluator{
		interfaces.ModelFormatRuleBased:   s.mockRuleBasedEvaluator,
		interfaces.ModelFormatBloomFilter: s.mockBloomFilterEvaluator,
	}
	s.delegatingEvaluator = NewDelegatingModelEvaluator(evaluators)
}

func (s *DelegatingModelEvaluatorSuite) TestEvaluate_BloomFilterFormat_RoutesToBloomFilterEvaluator() {
	modelDef := &interfaces.ModelDefinition{
		Identifier:  "adsp_rsp_v1",
		ModelFormat: interfaces.ModelFormatBloomFilter,
	}
	input := interfaces.ModelEvaluatorInput{
		Context:         interfaces.NewContext(),
		ModelDefinition: modelDef,
	}
	expectedOutput := &interfaces.ModelEvaluatorOutput{
		Status:          interfaces.ModelEvaluationStatusSuccess,
		ModelDefinition: *modelDef,
		ModelResult: interfaces.ModelResult{
			Value: 0.0,
			Key:   "pub123|USA",
		},
	}

	s.mockBloomFilterEvaluator.EXPECT().
		Evaluate(input).
		Return(expectedOutput, nil).
		Once()

	output, err := s.delegatingEvaluator.Evaluate(input)

	s.NoError(err)
	s.Equal(expectedOutput, output)
}

func (s *DelegatingModelEvaluatorSuite) TestEvaluate_RuleBasedFormat_RoutesToRuleBasedEvaluator() {
	modelDef := &interfaces.ModelDefinition{
		Identifier:  "adsp_low-value_v2",
		ModelFormat: interfaces.ModelFormatRuleBased,
	}
	input := interfaces.ModelEvaluatorInput{
		Context:         interfaces.NewContext(),
		ModelDefinition: modelDef,
	}
	expectedOutput := &interfaces.ModelEvaluatorOutput{
		Status:          interfaces.ModelEvaluationStatusSuccess,
		ModelDefinition: *modelDef,
		ModelResult: interfaces.ModelResult{
			Value: 1.0,
			Key:   "pub456|GBR",
		},
	}

	s.mockRuleBasedEvaluator.EXPECT().
		Evaluate(input).
		Return(expectedOutput, nil).
		Once()

	output, err := s.delegatingEvaluator.Evaluate(input)

	s.NoError(err)
	s.Equal(expectedOutput, output)
}

func (s *DelegatingModelEvaluatorSuite) TestEvaluate_EmptyFormat_FallsBackToRuleBasedEvaluator() {
	modelDef := &interfaces.ModelDefinition{
		Identifier:  "adsp_legacy_model",
		ModelFormat: "", // empty format
	}
	input := interfaces.ModelEvaluatorInput{
		Context:         interfaces.NewContext(),
		ModelDefinition: modelDef,
	}
	expectedOutput := &interfaces.ModelEvaluatorOutput{
		Status:          interfaces.ModelEvaluationStatusSuccess,
		ModelDefinition: *modelDef,
		ModelResult: interfaces.ModelResult{
			Value: 1.0,
			Key:   "pub789|DEU",
		},
	}

	s.mockRuleBasedEvaluator.EXPECT().
		Evaluate(input).
		Return(expectedOutput, nil).
		Once()

	output, err := s.delegatingEvaluator.Evaluate(input)

	s.NoError(err)
	s.Equal(expectedOutput, output)
}

func (s *DelegatingModelEvaluatorSuite) TestEvaluate_UnrecognizedFormat_FallsBackToRuleBasedEvaluator() {
	modelDef := &interfaces.ModelDefinition{
		Identifier:  "adsp_unknown_model",
		ModelFormat: "ONNX_MODEL", // unrecognized format
	}
	input := interfaces.ModelEvaluatorInput{
		Context:         interfaces.NewContext(),
		ModelDefinition: modelDef,
	}
	expectedOutput := &interfaces.ModelEvaluatorOutput{
		Status:          interfaces.ModelEvaluationStatusSuccess,
		ModelDefinition: *modelDef,
		ModelResult: interfaces.ModelResult{
			Value: 1.0,
			Key:   "pub000|JPN",
		},
	}

	s.mockRuleBasedEvaluator.EXPECT().
		Evaluate(input).
		Return(expectedOutput, nil).
		Once()

	output, err := s.delegatingEvaluator.Evaluate(input)

	s.NoError(err)
	s.Equal(expectedOutput, output)
}

func (s *DelegatingModelEvaluatorSuite) TestEvaluate_ResultPassedThroughUnchangedFromDelegate() {
	// Verifies that errors and outputs from the delegate are returned as-is,
	// without any transformation by the delegating evaluator.
	modelDef := &interfaces.ModelDefinition{
		Identifier:  "adsp_rsp_v1",
		ModelFormat: interfaces.ModelFormatBloomFilter,
	}
	input := interfaces.ModelEvaluatorInput{
		Context:         interfaces.NewContext(),
		ModelDefinition: modelDef,
	}
	expectedErr := fmt.Errorf("bloom filter lookup failed: model not loaded")
	expectedOutput := &interfaces.ModelEvaluatorOutput{
		Status:          interfaces.ModelEvaluationStatusError,
		ModelDefinition: *modelDef,
		ModelFeatures: []interfaces.ModelFeature{
			{
				Configuration: &interfaces.FeatureConfiguration{Name: "publisherId"},
				Values:        []string{"539014228"},
			},
		},
	}

	s.mockBloomFilterEvaluator.EXPECT().
		Evaluate(input).
		Return(expectedOutput, expectedErr).
		Once()

	output, err := s.delegatingEvaluator.Evaluate(input)

	s.Equal(expectedErr, err)
	s.Equal(expectedOutput, output)
	s.Equal(interfaces.ModelEvaluationStatusError, output.Status)
	s.Equal(*modelDef, output.ModelDefinition)
	s.Equal([]interfaces.ModelFeature{
		{
			Configuration: &interfaces.FeatureConfiguration{Name: "publisherId"},
			Values:        []string{"539014228"},
		},
	}, output.ModelFeatures)
}
