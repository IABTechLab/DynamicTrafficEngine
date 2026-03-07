// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package task

import (
	"fmt"
	"math"
	"sync"
	"time"

	"github.com/rs/zerolog"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/util"
)

var Logger zerolog.Logger

func init() {
	Logger = util.GetLogger()
	util.WithComponent("task")
}

// Initializes periodic execution of tasks.
type InitializerTask struct {
	Name                    string
	Task                    interfaces.Task
	MaximumAttempts         int
	MinDelayBeforeAttemptMs int64
	MaxDelayBeforeAttemptMs int64
}

func NewInitializerTask(name string, task interfaces.Task, maximumAttempts int, minDelayBeforeAttemptMs int64, maxDelayBeforeAttemptMs int64) *InitializerTask {
	return &InitializerTask{
		Name:                    name,
		Task:                    task,
		MaximumAttempts:         maximumAttempts,
		MinDelayBeforeAttemptMs: minDelayBeforeAttemptMs,
		MaxDelayBeforeAttemptMs: maxDelayBeforeAttemptMs,
	}
}

// Stores the overall configuration of scheduled tasks to be periodically executed.
type Initializer struct {
	StageOneTasks         []InitializerTask
	StageTwoTasks         []InitializerTask
	overallTimeoutMs      int64
	taskElapsedTime       int64
	workerWaitGroup       sync.WaitGroup
	taskResultChan        chan error
	initializerResultChan chan error
}

func NewInitializer(stageOneTasks []InitializerTask, stageTwoTasks []InitializerTask, overallTimeoutMs int64) *Initializer {
	return &Initializer{
		StageOneTasks:         stageOneTasks,
		StageTwoTasks:         stageTwoTasks,
		overallTimeoutMs:      overallTimeoutMs,
		taskElapsedTime:       int64(0),
		workerWaitGroup:       sync.WaitGroup{},
		taskResultChan:        make(chan error, len(stageOneTasks)+len(stageTwoTasks)),
		initializerResultChan: make(chan error, 2+len(stageOneTasks)+len(stageTwoTasks)), // it will hold its own 2 potential errors and all task errors
	}
}

// Initializes all tasks to be periodically executed.
func (i *Initializer) Init() {
	if len(i.StageOneTasks) > 0 {
		i.executeTasks(i.StageOneTasks, "StageOne", time.Now().UnixMilli())
		if len(i.StageTwoTasks) > 0 {
			i.executeTasks(i.StageTwoTasks, "StageTwo", time.Now().UnixMilli())
		}
	} else {
		Logger.Info().Msg("No initialization tasks defined.")
	}

	i.workerWaitGroup.Wait()
	close(i.initializerResultChan)

	for err := range i.initializerResultChan {
		if err != nil {
			Logger.Error().Msgf("Initialization failed: %v", err)
		}
	}
}

// Executes configured tasks for a specific stage.
func (i *Initializer) executeTasks(tasks []InitializerTask, stageName string, startTime int64) {
	Logger.Info().Msgf("Starting initialization of stage: %s", stageName)
	for _, task := range tasks {
		i.workerWaitGroup.Add(1)
		go func(t InitializerTask) {
			defer i.workerWaitGroup.Done()
			i.submitTask(startTime, t)
		}(task)
	}
	i.tasksResultIterator(tasks, startTime)
}

// Starts execution of a task.
func (i *Initializer) submitTask(started int64, task InitializerTask) {
	maxAttempts := task.MaximumAttempts
	attemptCount := 0
	var delay int64 = 0
	var err error = nil
	for {
		if attemptCount++; attemptCount > maxAttempts {
			i.taskResultChan <- fmt.Errorf("number of retries exceeded maxAttempts [%d] for task %s due to error: %s", maxAttempts, task.Name, err)
			return
		}

		delay = calculateDelay(attemptCount, delay, task.MinDelayBeforeAttemptMs, task.MaxDelayBeforeAttemptMs)
		time.Sleep(time.Duration(delay) * time.Millisecond)

		err = task.Task.Run()
		if err == nil {
			Logger.Info().Msgf("Task %s completed after %d attempt(s). Elapsed time is %.3f seconds", task.Name, attemptCount, float64(time.Now().UnixMilli()-started)/1000.0)
			i.taskResultChan <- nil
			return
		}
		Logger.Error().Msgf("Task %s attempt %d failed: %v", task.Name, attemptCount, err)
	}
}

// Handles completion or failure of tasks.
func (i *Initializer) tasksResultIterator(tasks []InitializerTask, startTime int64) {
	var executedTaskCount int

	for executedTaskCount < len(tasks) {
		elapsed := time.Since(time.UnixMilli(startTime)).Milliseconds()
		if elapsed >= i.overallTimeoutMs {
			break
		}
		select {
		case err := <-i.taskResultChan:
			if err != nil {
				i.initializerResultChan <- fmt.Errorf("task %s execution failed: %w", tasks[executedTaskCount].Name, err)
			}
			executedTaskCount++
		case <-time.After(time.Duration(i.overallTimeoutMs-elapsed) * time.Millisecond):
			i.initializerResultChan <- fmt.Errorf("timed out after %d tasks and %d ms", executedTaskCount, elapsed)
			return
		}
	}
	Logger.Info().Msgf("%s tasks initialization completed in %.3f seconds", "overall", float64(time.Since(time.UnixMilli(startTime)).Milliseconds())/1000.0)
}

// Calculates interval between task retries on failure.
func calculateDelay(timesAlreadyAttempted int, currentDelay, minDelay, maxDelay int64) int64 {
	switch timesAlreadyAttempted {
	case 1:
		return 0
	case 2:
		return minDelay
	default:
		return int64(math.Min(float64(2*currentDelay), float64(maxDelay)))
	}
}
