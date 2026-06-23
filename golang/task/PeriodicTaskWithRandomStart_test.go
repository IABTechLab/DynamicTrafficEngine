// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package task

import (
	"errors"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/modelfeature"
)

type PeriodicTaskTestSuite struct {
	suite.Suite
	task *PeriodicTaskWithRandomizedStart
}

func (suite *PeriodicTaskTestSuite) SetupTest() {
	suite.task = NewPeriodicTaskWithRandomizedStart(
		"testSSP",
		"testTask",
		"/test/folder",
		50,
		func() error { return nil },
	)
}

func (suite *PeriodicTaskTestSuite) TearDownTest() {
	if suite.task != nil {
		if suite.task.stopChan != nil {
			close(suite.task.stopChan)
		}
		if suite.task.ticker != nil {
			suite.task.ticker.Stop()
		}
	}
	// Wait for goroutines to complete
	time.Sleep(50 * time.Millisecond)
}

func (suite *PeriodicTaskTestSuite) TestNewPeriodicTaskWithRandomizedStart() {
	assert.NotNil(suite.T(), suite.task)
	assert.Equal(suite.T(), "testSSP", suite.task.sspIdentifier)
	assert.Equal(suite.T(), "testTask", suite.task.taskName)
	assert.Equal(suite.T(), "/test/folder", suite.task.folderPrefix)
	assert.Equal(suite.T(), 50, suite.task.refreshIntervalMs)
	assert.NotNil(suite.T(), suite.task.executeTask)
}

func (suite *PeriodicTaskTestSuite) TestSchedulePeriodicallyWithRandomizedStart() {
	var executionCount int64
	suite.task.executeTask = func() error {
		atomic.AddInt64(&executionCount, 1)
		return nil
	}

	ticker, stopChan := suite.task.schedulePeriodicallyWithRandomizedStart()
	assert.NotNil(suite.T(), ticker)
	assert.NotNil(suite.T(), stopChan)

	// Wait for at least one execution
	time.Sleep(time.Duration(suite.task.refreshIntervalMs+1000) * time.Millisecond)

	close(stopChan)
	time.Sleep(20 * time.Millisecond) // Give some time for the goroutine to stop

	assert.GreaterOrEqual(suite.T(), atomic.LoadInt64(&executionCount), int64(1))
}

func (suite *PeriodicTaskTestSuite) TestSchedulePeriodicallyWithRandomizedStartError() {
	var errorCalled int64
	suite.task.executeTask = func() error {
		atomic.StoreInt64(&errorCalled, 1)
		return errors.New("test error")
	}

	ticker, stopChan := suite.task.schedulePeriodicallyWithRandomizedStart()
	assert.NotNil(suite.T(), ticker)
	assert.NotNil(suite.T(), stopChan)

	// Wait for the error to occur
	time.Sleep(time.Duration(suite.task.refreshIntervalMs+1000) * time.Millisecond)

	assert.Equal(suite.T(), int64(1), atomic.LoadInt64(&errorCalled))
	select {
	case <-stopChan:
		assert.Fail(suite.T(), "StopChan should not be closed")
	default:
		// This is the expected behavior
	}
}

func (suite *PeriodicTaskTestSuite) TestSchedulePeriodicallyWithRandomizedStartPanic() {
	var panicCalled int64
	suite.task.executeTask = func() error {
		atomic.StoreInt64(&panicCalled, 1)
		panic("test panic")
	}

	ticker, stopChan := suite.task.schedulePeriodicallyWithRandomizedStart()
	assert.NotNil(suite.T(), ticker)
	assert.NotNil(suite.T(), stopChan)

	// Wait for the panic to occur
	time.Sleep(time.Duration(suite.task.refreshIntervalMs+1000) * time.Millisecond)

	assert.Equal(suite.T(), int64(1), atomic.LoadInt64(&panicCalled))
	select {
	case <-stopChan:
		assert.Fail(suite.T(), "StopChan should not be closed")
	default:
		// This is the expected behavior
	}
}

func TestPeriodicTaskTestSuite(t *testing.T) {
	suite.Run(t, new(PeriodicTaskTestSuite))
}

type ModelConfigurationPeriodicLoadingTaskTestSuite struct {
	suite.Suite
	task        *ModelConfigurationPeriodicLoadingTask
	mockHandler *mockInterfaces.ModelConfigurationHandlerInterface
}

func (suite *ModelConfigurationPeriodicLoadingTaskTestSuite) SetupTest() {
	suite.mockHandler = mockInterfaces.NewModelConfigurationHandlerInterface(suite.T())
	suite.task = NewModelConfigurationPeriodicLoadingTask(
		"testSSP",
		"/test/folder",
		&modelfeature.ModelConfigurationHandler{}, // You might need to adjust this based on your actual implementation
		50,
	)
	suite.task.modelConfigurationHandler = suite.mockHandler
}

func (suite *ModelConfigurationPeriodicLoadingTaskTestSuite) TearDownTest() {
	if suite.task != nil {
		suite.task.Stop()
	}
	// Wait for goroutines to complete
	time.Sleep(50 * time.Millisecond)
}

func (suite *ModelConfigurationPeriodicLoadingTaskTestSuite) TestNewModelConfigurationPeriodicLoadingTask() {
	assert.NotNil(suite.T(), suite.task)
	assert.NotNil(suite.T(), suite.task.PeriodicTaskWithRandomizedStart)
	assert.Equal(suite.T(), "testSSP", suite.task.sspIdentifier)
	assert.Equal(suite.T(), "ModelConfigurationPeriodicLoadingTask", suite.task.taskName)
	assert.Equal(suite.T(), "/test/folder", suite.task.folderPrefix)
	assert.Equal(suite.T(), 50, suite.task.refreshIntervalMs)
	assert.NotNil(suite.T(), suite.task.modelConfigurationHandler)
}

func (suite *ModelConfigurationPeriodicLoadingTaskTestSuite) TestExecuteTask() {
	suite.mockHandler.On("Load").Return(true, nil)

	err := suite.task.ExecuteTask()
	assert.NoError(suite.T(), err)

	suite.mockHandler.AssertExpectations(suite.T())
}

func (suite *ModelConfigurationPeriodicLoadingTaskTestSuite) TestExecuteTaskError() {
	expectedError := errors.New("load error")
	suite.mockHandler.On("Load").Return(false, expectedError)

	err := suite.task.ExecuteTask()
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "error loading model configuration")

	suite.mockHandler.AssertExpectations(suite.T())
}

func (suite *ModelConfigurationPeriodicLoadingTaskTestSuite) TestRun() {
	suite.mockHandler.On("Load").Return(true, nil)

	err := suite.task.Run()
	assert.NoError(suite.T(), err)

	// Wait a bit to allow the periodic task to start
	time.Sleep(50 * time.Millisecond)

	suite.mockHandler.AssertExpectations(suite.T())

	// Stop the task before test ends
	suite.task.Stop()
}

func (suite *ModelConfigurationPeriodicLoadingTaskTestSuite) TestRunError() {
	expectedError := errors.New("load error")
	suite.mockHandler.On("Load").Return(false, expectedError)

	err := suite.task.Run()
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "error executing ModelConfigurationPeriodicLoadingTask")

	suite.mockHandler.AssertExpectations(suite.T())

	// Stop the task before test ends
	suite.task.Stop()
}

func TestModelConfigurationPeriodicLoadingTaskTestSuite(t *testing.T) {
	suite.Run(t, new(ModelConfigurationPeriodicLoadingTaskTestSuite))
}

type ExperimentConfigurationPeriodicLoadingTaskTestSuite struct {
	suite.Suite
	task        *ExperimentConfigurationPeriodicLoadingTask
	mockHandler *mockInterfaces.ExperimentConfigurationHandlerInterface
}

func (suite *ExperimentConfigurationPeriodicLoadingTaskTestSuite) SetupTest() {
	suite.mockHandler = mockInterfaces.NewExperimentConfigurationHandlerInterface(suite.T())
	suite.task = NewExperimentConfigurationPeriodicLoadingTask(
		"testSSP",
		"/test/folder",
		&modelfeature.ExperimentConfigurationHandler{}, // You might need to adjust this based on your actual implementation
		50,
	)
	suite.task.experimentConfigurationHandler = suite.mockHandler
}

func (suite *ExperimentConfigurationPeriodicLoadingTaskTestSuite) TearDownTest() {
	if suite.task != nil {
		suite.task.Stop()
	}
	// Wait for goroutines to complete
	time.Sleep(50 * time.Millisecond)
}

func (suite *ExperimentConfigurationPeriodicLoadingTaskTestSuite) TestNewExperimentConfigurationPeriodicLoadingTask() {
	assert.NotNil(suite.T(), suite.task)
	assert.NotNil(suite.T(), suite.task.PeriodicTaskWithRandomizedStart)
	assert.Equal(suite.T(), "testSSP", suite.task.sspIdentifier)
	assert.Equal(suite.T(), "ExperimentConfigurationPeriodicLoadingTask", suite.task.taskName)
	assert.Equal(suite.T(), "/test/folder", suite.task.folderPrefix)
	assert.Equal(suite.T(), 50, suite.task.refreshIntervalMs)
	assert.NotNil(suite.T(), suite.task.experimentConfigurationHandler)
}

func (suite *ExperimentConfigurationPeriodicLoadingTaskTestSuite) TestExecuteTask() {
	suite.mockHandler.On("Load").Return(true, nil)

	err := suite.task.ExecuteTask()
	assert.NoError(suite.T(), err)

	suite.mockHandler.AssertExpectations(suite.T())
}

func (suite *ExperimentConfigurationPeriodicLoadingTaskTestSuite) TestExecuteTaskError() {
	expectedError := errors.New("load error")
	suite.mockHandler.On("Load").Return(false, expectedError)

	err := suite.task.ExecuteTask()
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "error loading model configuration")

	suite.mockHandler.AssertExpectations(suite.T())
}

func (suite *ExperimentConfigurationPeriodicLoadingTaskTestSuite) TestRun() {
	suite.mockHandler.On("Load").Return(true, nil)

	err := suite.task.Run()
	assert.NoError(suite.T(), err)

	// Wait a bit to allow the periodic task to start
	time.Sleep(50 * time.Millisecond)

	suite.mockHandler.AssertExpectations(suite.T())

	// Stop the task before test ends
	suite.task.Stop()
}

func (suite *ExperimentConfigurationPeriodicLoadingTaskTestSuite) TestRunError() {
	expectedError := errors.New("load error")
	suite.mockHandler.On("Load").Return(false, expectedError)

	err := suite.task.Run()
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "error executing ExperimentConfigurationPeriodicLoadingTask")

	suite.mockHandler.AssertExpectations(suite.T())

	// Stop the task before test ends
	suite.task.Stop()
}

func TestExperimentConfigurationPeriodicLoadingTaskTestSuite(t *testing.T) {
	suite.Run(t, new(ExperimentConfigurationPeriodicLoadingTaskTestSuite))
}

type ModelResultPeriodicLoadingTaskTestSuite struct {
	suite.Suite
	task        *ModelResultPeriodicLoadingTask
	mockHandler *mockInterfaces.ModelResultHandlerInterface
}

func (suite *ModelResultPeriodicLoadingTaskTestSuite) SetupTest() {
	suite.mockHandler = mockInterfaces.NewModelResultHandlerInterface(suite.T())
	suite.task = NewModelResultPeriodicLoadingTask(
		"testSSP",
		"/test/folder",
		&modelfeature.ModelResultHandler{}, // You might need to adjust this based on your actual implementation
		50,
	)
	suite.task.modelResultHandler = suite.mockHandler
}

func (suite *ModelResultPeriodicLoadingTaskTestSuite) TearDownTest() {
	if suite.task != nil {
		suite.task.Stop()
	}
	// Wait for goroutines to complete
	time.Sleep(50 * time.Millisecond)
}

func (suite *ModelResultPeriodicLoadingTaskTestSuite) TestNewModelResultPeriodicLoadingTask() {
	assert.NotNil(suite.T(), suite.task)
	assert.NotNil(suite.T(), suite.task.PeriodicTaskWithRandomizedStart)
	assert.Equal(suite.T(), "testSSP", suite.task.sspIdentifier)
	assert.Equal(suite.T(), "ModelResultPeriodicLoadingTask", suite.task.taskName)
	assert.Equal(suite.T(), "/test/folder", suite.task.folderPrefix)
	assert.Equal(suite.T(), 50, suite.task.refreshIntervalMs)
	assert.NotNil(suite.T(), suite.task.modelResultHandler)
}

func (suite *ModelResultPeriodicLoadingTaskTestSuite) TestExecuteTask() {
	suite.mockHandler.On("Load", "testSSP").Return(nil)

	err := suite.task.ExecuteTask()
	assert.NoError(suite.T(), err)

	suite.mockHandler.AssertExpectations(suite.T())
}

func (suite *ModelResultPeriodicLoadingTaskTestSuite) TestExecuteTaskError() {
	expectedError := errors.New("load error")
	suite.mockHandler.On("Load", "testSSP").Return(expectedError)

	err := suite.task.ExecuteTask()
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "error loading model result")

	suite.mockHandler.AssertExpectations(suite.T())
}

func (suite *ModelResultPeriodicLoadingTaskTestSuite) TestRun() {
	suite.mockHandler.On("Load", "testSSP").Return(nil)

	err := suite.task.Run()
	assert.NoError(suite.T(), err)

	// Wait a bit to allow the periodic task to start
	time.Sleep(1100 * time.Millisecond)

	suite.mockHandler.AssertExpectations(suite.T())

	// Stop the task before test ends
	suite.task.Stop()
}

func (suite *ModelResultPeriodicLoadingTaskTestSuite) TestRunError() {
	expectedError := errors.New("load error")
	suite.mockHandler.On("Load", "testSSP").Return(expectedError)

	err := suite.task.Run()
	assert.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "error executing ModelResultPeriodicLoadingTask")

	suite.mockHandler.AssertExpectations(suite.T())

	// Stop the task before test ends
	suite.task.Stop()
}

func TestModelResultPeriodicLoadingTaskTestSuite(t *testing.T) {
	suite.Run(t, new(ModelResultPeriodicLoadingTaskTestSuite))
}
