// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package modelfeature

import (
	"fmt"
	"testing"

	"github.com/stretchr/testify/suite"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
)

func TestExperimentConfigurationHandlerSuite(t *testing.T) {
	suite.Run(t, new(ExperimentConfigurationHandlerTestSuite))
}

type ExperimentConfigurationHandlerTestSuite struct {
	suite.Suite
	trafficAllocator               *mockInterfaces.TrafficAllocatorInterface
	configurationHandler           *mockInterfaces.ConfigurationHandlerInterface[interfaces.ExperimentConfiguration]
	experimentConfigurationHandler *ExperimentConfigurationHandler
}

func (suite *ExperimentConfigurationHandlerTestSuite) SetupTest() {
	suite.trafficAllocator = mockInterfaces.NewTrafficAllocatorInterface(suite.T())
	suite.configurationHandler = mockInterfaces.NewConfigurationHandlerInterface[interfaces.ExperimentConfiguration](suite.T())

	suite.experimentConfigurationHandler = NewExperimentConfigurationHandler(suite.configurationHandler, suite.trafficAllocator)
}

func (suite *ExperimentConfigurationHandlerTestSuite) TestLoad_Success() {
	suite.configurationHandler.EXPECT().Load().Return(true, nil)
	config := &interfaces.ExperimentConfiguration{}
	suite.configurationHandler.EXPECT().Provide().Return(config, nil)
	suite.trafficAllocator.EXPECT().UpdateConfiguration(config).Return(nil)

	success, err := suite.experimentConfigurationHandler.Load()

	suite.NoError(err, "Should not be errored")
	suite.True(success, "Should be successful")
}

func (suite *ExperimentConfigurationHandlerTestSuite) TestLoad_ReturnError_ConfigurationHandlerLoadError() {
	suite.configurationHandler.EXPECT().Load().Return(false, fmt.Errorf("ConfigurationHandler Load error"))

	success, err := suite.experimentConfigurationHandler.Load()

	suite.EqualError(err, "failed to load experiment configuration: ConfigurationHandler Load error", "Should be errored")
	suite.False(success, "Should not be successful")
}

func (suite *ExperimentConfigurationHandlerTestSuite) TestLoad_ReturnFalse_NotLoad() {
	suite.configurationHandler.EXPECT().Load().Return(false, nil)

	success, err := suite.experimentConfigurationHandler.Load()

	suite.NoError(err, "Should not be errored")
	suite.False(success, "Should not be successful")
}

func (suite *ExperimentConfigurationHandlerTestSuite) TestLoad_ReturnError_ConfigurationHandlerProvideError() {
	suite.configurationHandler.EXPECT().Load().Return(true, nil)
	suite.configurationHandler.EXPECT().Provide().Return(nil, fmt.Errorf("ConfigurationHandler Load error"))

	success, err := suite.experimentConfigurationHandler.Load()

	suite.EqualError(err, "failed to provide experiment configuration: ConfigurationHandler Load error", "Should be errored")
	suite.False(success, "Should not be successful")
}

func (suite *ExperimentConfigurationHandlerTestSuite) TestLoad_ReturnError_UpdateConfigurationError() {
	suite.configurationHandler.EXPECT().Load().Return(true, nil)
	config := &interfaces.ExperimentConfiguration{}
	suite.configurationHandler.EXPECT().Provide().Return(config, nil)
	suite.trafficAllocator.EXPECT().UpdateConfiguration(config).Return(fmt.Errorf("UpdateConfiguration error"))

	success, err := suite.experimentConfigurationHandler.Load()

	suite.EqualError(err, "failed to update traffic allocator configuration: UpdateConfiguration error")
	suite.False(success, "Should not be successful")
}

func (suite *ExperimentConfigurationHandlerTestSuite) TestProvide_Success() {
	config := &interfaces.ExperimentConfiguration{}
	suite.configurationHandler.EXPECT().Provide().Return(config, nil)

	actualConfig, err := suite.experimentConfigurationHandler.Provide()

	suite.NoError(err, "Should not be errored")
	suite.Equal(config, actualConfig, "Should be equal")
}
