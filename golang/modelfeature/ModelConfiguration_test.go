// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package modelfeature

import (
	"encoding/json"
	"fmt"
	"os"
	"testing"

	"github.com/stretchr/testify/suite"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
)

var (
	AllUniqueFeatureFields = []string{
		"$.app",
		"$.imp[0].video",
		"$.site.publisher.id",
		"$.app.publisher.id",
		"$.device.geo.country",
		"$.imp[0].video.w",
		"$.imp[0].video.h",
		"$.imp[0].banner.w",
		"$.imp[0].banner.h",
		"$.imp[0].banner.pos",
		"$.imp[0].video.pos",
		"$.device.devicetype",
	}
)

func TestModelConfigurationHandlerSuite(t *testing.T) {
	suite.Run(t, new(ModelConfigurationHandlerTestSuite))
}

type ModelConfigurationHandlerTestSuite struct {
	suite.Suite
	modelConfigurationHandler *ModelConfigurationHandler
	configurationHandler      *mockInterfaces.ConfigurationHandlerInterface[interfaces.ModelConfiguration]
	modelConfiguration        interfaces.ModelConfiguration
}

func (suite *ModelConfigurationHandlerTestSuite) SetupSuite() {
	suite.configurationHandler = mockInterfaces.NewConfigurationHandlerInterface[interfaces.ModelConfiguration](suite.T())
	suite.modelConfigurationHandler = NewModelConfigurationHandler(suite.configurationHandler)

	dir, err := os.Getwd()
	suite.NoError(err, "Failed to get current working directory")
	testDataDir := dir + "/../testdata"
	modelConfigurationTestDataFilePath := testDataDir + "/ssp/configuration/model/config.json"
	modelConfigurationData, modelConfigurationDataErr := os.ReadFile(modelConfigurationTestDataFilePath)
	suite.NoError(modelConfigurationDataErr, "Failed to read model configuration test data file")
	jsonErr := json.Unmarshal(modelConfigurationData, &suite.modelConfiguration)
	suite.NoError(jsonErr, "Failed to unmarshal model configuration test data")
}

func (suite *ModelConfigurationHandlerTestSuite) TestLoad_Success() {
	suite.configurationHandler.EXPECT().Load().Return(true, nil).Once()

	success, err := suite.modelConfigurationHandler.Load()

	suite.NoError(err, "Should not be errored")
	suite.True(success, "Should be successful")
}

func (suite *ModelConfigurationHandlerTestSuite) TestProvide_Success() {
	config := &interfaces.ModelConfiguration{}
	suite.configurationHandler.EXPECT().Provide().Return(config, nil).Once()

	actualConfig, err := suite.modelConfigurationHandler.Provide()

	suite.NoError(err, "Should not be errored")
	suite.Equal(config, actualConfig, "Should be equal")
}

func (suite *ModelConfigurationHandlerTestSuite) TestGetAllUniqueFeatureFields_Success() {
	suite.configurationHandler.EXPECT().Provide().Return(&suite.modelConfiguration, nil).Once()

	allUniqueFeatureFields, err := suite.modelConfigurationHandler.GetAllUniqueFeatureFields()

	suite.NoError(err, "Should not be errored")
	suite.ElementsMatchf(AllUniqueFeatureFields, allUniqueFeatureFields, "Should be equal")
}

func (suite *ModelConfigurationHandlerTestSuite) TestGetAllUniqueFeatureFields_ReturnError_ConfigurationHandlerProvideError() {
	suite.configurationHandler.EXPECT().Provide().Return(nil, fmt.Errorf("ConfigurationHandlerProvide error")).Once()

	allUniqueFeatureFields, err := suite.modelConfigurationHandler.GetAllUniqueFeatureFields()

	suite.Error(err, "Should be errored")
	suite.Nil(allUniqueFeatureFields, "Should be nil")
}

func (suite *ModelConfigurationHandlerTestSuite) TestExistsTransformer() {
	tests := []struct {
		name           string
		input          *interfaces.ModelFeature
		expectedOutput *interfaces.ModelFeature
		expectedError  error
	}{
		{
			name: "All non-empty values",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test"},
				Values:        []string{"a", "b", "c"},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test"},
				Values:        []string{"1", "1", "1"},
			},
			expectedError: nil,
		},
		{
			name: "All empty values",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test"},
				Values:        []string{"", "", ""},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test"},
				Values:        []string{"0", "0", "0"},
			},
			expectedError: nil,
		},
		{
			name: "Mixed empty and non-empty values",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test"},
				Values:        []string{"", "b", "", "d", ""},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test"},
				Values:        []string{"0", "1", "0", "1", "0"},
			},
			expectedError: nil,
		},
		{
			name: "Empty input slice",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test"},
				Values:        []string{},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test"},
				Values:        []string{},
			},
			expectedError: nil,
		},
	}

	for _, tt := range tests {
		suite.Run(tt.name, func() {
			output, err := ExistsTransformer(tt.input)

			if tt.expectedError != nil {
				suite.Error(err)
				suite.Equal(tt.expectedError, err)
			} else {
				suite.NoError(err)
				suite.NotNil(output)
				suite.Equal(tt.expectedOutput.Configuration, output.Configuration)
				suite.ElementsMatch(tt.expectedOutput.Values, output.Values)
			}
		})
	}
}

func (suite *ModelConfigurationHandlerTestSuite) TestGetFirstNotEmptyTransformer() {
	tests := []struct {
		name           string
		input          *interfaces.ModelFeature
		expectedOutput *interfaces.ModelFeature
		expectedError  error
	}{
		{
			name: "First value non-empty",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test1"},
				Values:        []string{"a", "b", "c"},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test1"},
				Values:        []string{"a"},
			},
			expectedError: nil,
		},
		{
			name: "First non-empty value in middle",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test2"},
				Values:        []string{"", "", "b", "c", "d"},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test2"},
				Values:        []string{"b"},
			},
			expectedError: nil,
		},
		{
			name: "Last value non-empty",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test3"},
				Values:        []string{"", "", "", "d"},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test3"},
				Values:        []string{"d"},
			},
			expectedError: nil,
		},
		{
			name: "All values empty",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test4"},
				Values:        []string{"", "", ""},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test4"},
				Values:        []string{""},
			},
			expectedError: nil,
		},
		{
			name: "Empty input slice",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test5"},
				Values:        []string{},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test5"},
				Values:        []string{""},
			},
			expectedError: nil,
		},
	}

	for _, tt := range tests {
		suite.Run(tt.name, func() {
			output, err := GetFirstNotEmptyTransformer(tt.input)

			if tt.expectedError != nil {
				suite.Assert().Error(err)
				suite.Assert().Equal(tt.expectedError, err)
			} else {
				suite.Require().NoError(err)
				suite.Assert().NotNil(output)
				suite.Assert().Equal(tt.expectedOutput.Configuration, output.Configuration)
				suite.Assert().Equal(tt.expectedOutput.Values, output.Values)
			}
		})
	}
}

func (suite *ModelConfigurationHandlerTestSuite) TestApplyMappingsTransformer() {
	tests := []struct {
		name           string
		input          *interfaces.ModelFeature
		expectedOutput *interfaces.ModelFeature
		expectedError  error
	}{
		{
			name: "All values mapped",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{
					Name:                "test1",
					Mapping:             map[string]string{"a": "1", "b": "2", "c": "3"},
					MappingDefaultValue: "0",
				},
				Values: []string{"a", "b", "c"},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{
					Name:                "test1",
					Mapping:             map[string]string{"a": "1", "b": "2", "c": "3"},
					MappingDefaultValue: "0",
				},
				Values: []string{"1", "2", "3"},
			},
			expectedError: nil,
		},
		{
			name: "Some values not mapped",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{
					Name:                "test2",
					Mapping:             map[string]string{"a": "1", "b": "2"},
					MappingDefaultValue: "0",
				},
				Values: []string{"a", "b", "c", "d"},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{
					Name:                "test2",
					Mapping:             map[string]string{"a": "1", "b": "2"},
					MappingDefaultValue: "0",
				},
				Values: []string{"1", "2", "0", "0"},
			},
			expectedError: nil,
		},
		{
			name: "Empty mapping",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{
					Name:                "test3",
					Mapping:             map[string]string{},
					MappingDefaultValue: "default",
				},
				Values: []string{"a", "b", "c"},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{
					Name:                "test3",
					Mapping:             map[string]string{},
					MappingDefaultValue: "default",
				},
				Values: []string{"default", "default", "default"},
			},
			expectedError: nil,
		},
		{
			name: "Empty input values",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{
					Name:                "test4",
					Mapping:             map[string]string{"a": "1", "b": "2"},
					MappingDefaultValue: "0",
				},
				Values: []string{},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{
					Name:                "test4",
					Mapping:             map[string]string{"a": "1", "b": "2"},
					MappingDefaultValue: "0",
				},
				Values: nil,
			},
			expectedError: nil,
		},
	}

	for _, tt := range tests {
		suite.Run(tt.name, func() {
			output, err := ApplyMappingsTransformer(tt.input)

			if tt.expectedError != nil {
				suite.Assert().Error(err)
				suite.Assert().Equal(tt.expectedError, err)
			} else {
				suite.Require().NoError(err)
				suite.Assert().NotNil(output)
				suite.Assert().Equal(tt.expectedOutput.Configuration, output.Configuration)
				suite.Assert().Equal(tt.expectedOutput.Values, output.Values)
			}
		})
	}
}

func (suite *ModelConfigurationHandlerTestSuite) TestConcatenateByPairTransformer() {
	tests := []struct {
		name           string
		input          *interfaces.ModelFeature
		expectedOutput *interfaces.ModelFeature
		expectedError  error
	}{
		{
			name: "Even number of non-empty values",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test1"},
				Values:        []string{"a", "1", "b", "2", "c", "3"},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test1"},
				Values:        []string{"ax1", "bx2", "cx3"},
			},
			expectedError: nil,
		},
		{
			name: "Odd number of values",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test2"},
				Values:        []string{"a", "1", "b", "2", "c"},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test2"},
				Values:        []string{"ax1", "bx2"},
			},
			expectedError: nil,
		},
		{
			name: "Some empty values",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test3"},
				Values:        []string{"a", "1", "", "2", "c", ""},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test3"},
				Values:        []string{"ax1"},
			},
			expectedError: nil,
		},
		{
			name: "All empty values",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test4"},
				Values:        []string{"", "", "", "", "", ""},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test4"},
				Values:        nil,
			},
			expectedError: nil,
		},
		{
			name: "Empty input slice",
			input: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test5"},
				Values:        []string{},
			},
			expectedOutput: &interfaces.ModelFeature{
				Configuration: &interfaces.FeatureConfiguration{Name: "test5"},
				Values:        nil,
			},
			expectedError: nil,
		},
	}

	for _, tt := range tests {
		suite.Run(tt.name, func() {
			output, err := ConcatenateByPairTransformer(tt.input)

			if tt.expectedError != nil {
				suite.Assert().Error(err)
				suite.Assert().Equal(tt.expectedError, err)
			} else {
				suite.Require().NoError(err)
				suite.Assert().NotNil(output)
				suite.Assert().Equal(tt.expectedOutput.Configuration, output.Configuration)
				suite.Assert().Equal(tt.expectedOutput.Values, output.Values)
			}
		})
	}
}
