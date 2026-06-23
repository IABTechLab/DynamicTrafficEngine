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
