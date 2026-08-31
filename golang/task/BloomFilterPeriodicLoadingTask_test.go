// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package task

import (
	"errors"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
	"golang.a2z.com/demanddriventrafficevaluator/bloomfilter"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
)

type BloomFilterPeriodicLoadingTaskTestSuite struct {
	suite.Suite
	task                   *BloomFilterPeriodicLoadingTask
	mockModelConfigHandler *mockInterfaces.ModelConfigurationHandlerInterface
	mockDaoFactory         *mockInterfaces.DaoFactoryInterface
	mockLocalCacheFactory  *mockInterfaces.LocalCacheFactoryInterface
	mockTimeProvider       *mockInterfaces.TimeProvider
}

func (suite *BloomFilterPeriodicLoadingTaskTestSuite) SetupTest() {
	suite.mockModelConfigHandler = mockInterfaces.NewModelConfigurationHandlerInterface(suite.T())
	suite.mockDaoFactory = mockInterfaces.NewDaoFactoryInterface(suite.T())
	suite.mockLocalCacheFactory = mockInterfaces.NewLocalCacheFactoryInterface(suite.T())
	suite.mockTimeProvider = mockInterfaces.NewTimeProvider(suite.T())

	store := bloomfilter.NewBloomFilterStore()
	loader := bloomfilter.NewBloomFilterLoader(
		store,
		suite.mockDaoFactory,
		suite.mockLocalCacheFactory,
		suite.mockModelConfigHandler,
		suite.mockTimeProvider,
	)

	suite.task = NewBloomFilterPeriodicLoadingTask(
		"testSSP",
		"s3://test-bucket",
		loader,
		50,
	)
}

func (suite *BloomFilterPeriodicLoadingTaskTestSuite) TearDownTest() {
	if suite.task != nil {
		suite.task.Stop()
	}
	// Wait for goroutines to complete
	time.Sleep(50 * time.Millisecond)
}

func (suite *BloomFilterPeriodicLoadingTaskTestSuite) TestNewBloomFilterPeriodicLoadingTask() {
	assert.NotNil(suite.T(), suite.task)
	assert.NotNil(suite.T(), suite.task.PeriodicTaskWithRandomizedStart)
	assert.Equal(suite.T(), "testSSP", suite.task.sspIdentifier)
	assert.Equal(suite.T(), "BloomFilterPeriodicLoadingTask", suite.task.taskName)
	assert.Equal(suite.T(), "s3://test-bucket", suite.task.folderPrefix)
	assert.Equal(suite.T(), 50, suite.task.refreshIntervalMs)
	assert.NotNil(suite.T(), suite.task.bloomFilterLoader)
}

func (suite *BloomFilterPeriodicLoadingTaskTestSuite) TestExecuteTaskCallsLoaderLoadWithCorrectParameters() {
	// When Provide returns a configuration with no bloom filter models,
	// Load iterates and finds nothing to load — returns nil
	modelConfig := &interfaces.ModelConfiguration{
		ModelDefinitionByIdentifier: map[string]interfaces.ModelDefinition{},
	}
	suite.mockModelConfigHandler.On("Provide").Return(modelConfig, nil)

	err := suite.task.ExecuteTask()
	assert.NoError(suite.T(), err)

	suite.mockModelConfigHandler.AssertExpectations(suite.T())
}

func (suite *BloomFilterPeriodicLoadingTaskTestSuite) TestExecuteTaskReturnsErrorWhenLoaderFails() {
	// When Provide returns an error, Load fails and ExecuteTask wraps it
	expectedError := errors.New("fail to provide modelConfiguration")
	suite.mockModelConfigHandler.On("Provide").Return(nil, expectedError)

	err := suite.task.ExecuteTask()
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "error loading bloom filter")

	suite.mockModelConfigHandler.AssertExpectations(suite.T())
}

func (suite *BloomFilterPeriodicLoadingTaskTestSuite) TestRunExecutesTaskAndReturnsNilOnSuccess() {
	// When Load succeeds, Run should return nil after the initial sleep
	modelConfig := &interfaces.ModelConfiguration{
		ModelDefinitionByIdentifier: map[string]interfaces.ModelDefinition{},
	}
	suite.mockModelConfigHandler.On("Provide").Return(modelConfig, nil)

	err := suite.task.Run()
	assert.NoError(suite.T(), err)

	// Wait a bit to allow the periodic task to start
	time.Sleep(50 * time.Millisecond)

	suite.mockModelConfigHandler.AssertExpectations(suite.T())

	// Stop the task before test ends
	suite.task.Stop()
}

func (suite *BloomFilterPeriodicLoadingTaskTestSuite) TestRunReturnsErrorWhenLoaderFails() {
	// When Load fails, Run should return the wrapped error
	expectedError := errors.New("fail to provide modelConfiguration")
	suite.mockModelConfigHandler.On("Provide").Return(nil, expectedError)

	err := suite.task.Run()
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "error executing BloomFilterPeriodicLoadingTask")

	suite.mockModelConfigHandler.AssertExpectations(suite.T())

	// Stop the task before test ends
	suite.task.Stop()
}

func TestBloomFilterPeriodicLoadingTaskTestSuite(t *testing.T) {
	suite.Run(t, new(BloomFilterPeriodicLoadingTaskTestSuite))
}
