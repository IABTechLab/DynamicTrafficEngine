// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package modelfeature

import (
	"testing"

	"github.com/stretchr/testify/suite"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
)

func TestModelConfigurationSuite(t *testing.T) {
	suite.Run(t, new(ModelConfigurationTestSuite))
}

type ModelConfigurationTestSuite struct {
	suite.Suite
}

func (suite *ModelConfigurationTestSuite) TestIncludeDefaultValueTransformer() {
	tests := []struct {
		name           string
		inputValues    []string
		defaultValue   string
		expectedValues []string
	}{
		{
			name:           "all non-empty values with non-empty default appends default",
			inputValues:    []string{"dealA", "dealB", "dealC"},
			defaultValue:   "no_deal",
			expectedValues: []string{"dealA", "dealB", "dealC", "no_deal"},
		},
		{
			name:           "all empty values with non-empty default returns only default",
			inputValues:    []string{"", "", ""},
			defaultValue:   "no_deal",
			expectedValues: []string{"no_deal"},
		},
		{
			name:           "mixed empty and non-empty values preserves order and appends default",
			inputValues:    []string{"dealA", "", "dealB", "", "dealC"},
			defaultValue:   "no_deal",
			expectedValues: []string{"dealA", "dealB", "dealC", "no_deal"},
		},
		{
			name:           "empty default value returns only filtered non-empty values",
			inputValues:    []string{"dealA", "", "dealB"},
			defaultValue:   "",
			expectedValues: []string{"dealA", "dealB"},
		},
		{
			name:           "nil input values with non-empty default returns only default",
			inputValues:    nil,
			defaultValue:   "no_deal",
			expectedValues: []string{"no_deal"},
		},
		{
			name:           "empty input values with non-empty default returns only default",
			inputValues:    []string{},
			defaultValue:   "no_deal",
			expectedValues: []string{"no_deal"},
		},
		{
			name:           "nil input values with empty default returns nil",
			inputValues:    nil,
			defaultValue:   "",
			expectedValues: nil,
		},
		{
			name:           "empty input values with empty default returns nil",
			inputValues:    []string{},
			defaultValue:   "",
			expectedValues: nil,
		},
	}

	for _, tt := range tests {
		suite.Run(tt.name, func() {
			configuration := &interfaces.FeatureConfiguration{
				MappingDefaultValue: tt.defaultValue,
			}
			input := &interfaces.ModelFeature{
				Configuration: configuration,
				Values:        tt.inputValues,
			}

			result, err := IncludeDefaultValueTransformer(input)

			suite.Nil(err, "IncludeDefaultValueTransformer should not return an error")
			suite.Equal(tt.expectedValues, result.Values, "transformed values should match expected")
			suite.Same(configuration, result.Configuration, "Configuration reference should be preserved")
		})
	}
}

// TestExistsTransformer verifies the Exists transformer maps empty values to "0" and
// non-empty values to "1", matching the Java implementation.
func (suite *ModelConfigurationTestSuite) TestExistsTransformer() {
	tests := []struct {
		name           string
		inputValues    []string
		expectedValues []string
	}{
		{
			name:           "non-empty values become 1",
			inputValues:    []string{"a", "b", "c"},
			expectedValues: []string{"1", "1", "1"},
		},
		{
			name:           "empty values become 0",
			inputValues:    []string{"", "", ""},
			expectedValues: []string{"0", "0", "0"},
		},
		{
			name:           "mixed values map positionally",
			inputValues:    []string{"a", "", "c", ""},
			expectedValues: []string{"1", "0", "1", "0"},
		},
		{
			// A missing definite JSONPath field arrives as [""] (see extractField), so
			// Exists produces ["0"] rather than dropping the value entirely.
			name:           "single empty string (missing field) becomes 0",
			inputValues:    []string{""},
			expectedValues: []string{"0"},
		},
	}

	for _, tt := range tests {
		suite.Run(tt.name, func() {
			input := &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{},
				Values:        tt.inputValues,
			}
			result, err := ExistsTransformer(input)
			suite.Nil(err)
			suite.Equal(tt.expectedValues, result.Values)
		})
	}
}

// TestExistsThenApplyMappings_IsMobilePattern verifies the canonical "isMobile" feature
// chain: Exists followed by ApplyMappings with {"0":"site","1":"app"}. Crucially, an
// absent $.app field (extracted as [""]) must resolve to "site", and a present $.app
// (extracted as a non-empty value) must resolve to "app".
func (suite *ModelConfigurationTestSuite) TestExistsThenApplyMappings_IsMobilePattern() {
	tests := []struct {
		name        string
		inputValues []string
		expected    []string
	}{
		{
			// $.app present (object rendered to a non-empty string) → "1" → "app".
			name:        "app present maps to app",
			inputValues: []string{"map[bundle:com.example]"},
			expected:    []string{"app"},
		},
		{
			// $.app absent → extractField returns [""] → Exists "0" → ApplyMappings "site".
			name:        "app absent maps to site",
			inputValues: []string{""},
			expected:    []string{"site"},
		},
	}

	for _, tt := range tests {
		suite.Run(tt.name, func() {
			configuration := &interfaces.FeatureConfiguration{
				Name:            "isMobile",
				Fields:          []string{"$.app"},
				Transformations: []interfaces.TransformerName{Exists, ApplyMappings},
				Mapping:         map[string]string{"0": "site", "1": "app"},
			}
			feature := &interfaces.ModelFeature{
				Configuration: configuration,
				Values:        tt.inputValues,
			}

			afterExists, err := ExistsTransformer(feature)
			suite.Nil(err)
			result, err := ApplyMappingsTransformer(afterExists)
			suite.Nil(err)
			suite.Equal(tt.expected, result.Values)
		})
	}
}
