// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package task

import (
	"errors"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
)

type InitializerTestSuite struct {
	suite.Suite
	mockTask1 *mockInterfaces.Task
	mockTask2 *mockInterfaces.Task
}

func (suite *InitializerTestSuite) SetupTest() {
	suite.mockTask1 = mockInterfaces.NewTask(suite.T())
	suite.mockTask2 = mockInterfaces.NewTask(suite.T())
}

func (suite *InitializerTestSuite) TestNewInitializer() {
	stageOneTasks := []InitializerTask{
		*NewInitializerTask("Task1", suite.mockTask1, 3, 100, 1000),
	}
	stageTwoTasks := []InitializerTask{
		*NewInitializerTask("Task2", suite.mockTask2, 3, 100, 1000),
	}
	overallTimeoutMs := int64(5000)

	initializer := NewInitializer(stageOneTasks, stageTwoTasks, overallTimeoutMs)

	assert.NotNil(suite.T(), initializer)
	assert.Equal(suite.T(), stageOneTasks, initializer.StageOneTasks)
	assert.Equal(suite.T(), stageTwoTasks, initializer.StageTwoTasks)
	assert.Equal(suite.T(), overallTimeoutMs, initializer.overallTimeoutMs)
}

func (suite *InitializerTestSuite) TestInitWithSuccessfulTasks() {
	suite.mockTask1.EXPECT().Run().Return(nil)
	suite.mockTask2.EXPECT().Run().Return(nil)

	stageOneTasks := []InitializerTask{
		*NewInitializerTask("Task1", suite.mockTask1, 3, 100, 1000),
	}
	stageTwoTasks := []InitializerTask{
		*NewInitializerTask("Task2", suite.mockTask2, 3, 100, 1000),
	}

	initializer := NewInitializer(stageOneTasks, stageTwoTasks, 5000)
	initializer.Init()
}

func (suite *InitializerTestSuite) TestInitWithNoTasks() {
	var stageOneTasks []InitializerTask
	var stageTwoTasks []InitializerTask

	initializer := NewInitializer(stageOneTasks, stageTwoTasks, 5000)
	initializer.Init()
}

func (suite *InitializerTestSuite) TestInitWithFailingTask() {
	suite.mockTask1.EXPECT().Run().Return(errors.New("task failed"))
	suite.mockTask2.EXPECT().Run().Return(nil)
	stageOneTasks := []InitializerTask{
		*NewInitializerTask("Task1", suite.mockTask1, 3, 100, 1000),
	}
	stageTwoTasks := []InitializerTask{
		*NewInitializerTask("Task2", suite.mockTask2, 3, 100, 1000),
	}

	initializer := NewInitializer(stageOneTasks, stageTwoTasks, 5000)
	initializer.Init()

	suite.mockTask1.AssertExpectations(suite.T())
	suite.mockTask2.AssertExpectations(suite.T())
	// Assert that an error was logged (you might need to mock the logger to verify this)
}

func (suite *InitializerTestSuite) TestInitWithTimeout() {
	suite.mockTask1.EXPECT().Run().After(2 * time.Second).Return(nil)

	stageOneTasks := []InitializerTask{
		*NewInitializerTask("Task1", suite.mockTask1, 3, 100, 1000),
	}

	initializer := NewInitializer(stageOneTasks, nil, 1000) // 1 second timeout
	initializer.Init()
}

func (suite *InitializerTestSuite) TestCalculateDelay() {
	assert.Equal(suite.T(), int64(0), calculateDelay(1, 0, 100, 1000))
	assert.Equal(suite.T(), int64(100), calculateDelay(2, 0, 100, 1000))
	assert.Equal(suite.T(), int64(200), calculateDelay(3, 100, 100, 1000))
	assert.Equal(suite.T(), int64(400), calculateDelay(4, 200, 100, 1000))
	assert.Equal(suite.T(), int64(800), calculateDelay(5, 400, 100, 1000))
	assert.Equal(suite.T(), int64(1000), calculateDelay(6, 800, 100, 1000)) // Max delay reached
}

func TestInitializerSuite(t *testing.T) {
	suite.Run(t, new(InitializerTestSuite))
}
