// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package task

import (
	"fmt"
	"math/rand"
	"time"

	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
)

// Implements Task. Used for periodically fetching model configuration files.
type ModelConfigurationPeriodicLoadingTask struct {
	*PeriodicTaskWithRandomizedStart
	modelConfigurationHandler interfaces.ModelConfigurationHandlerInterface
}

func NewModelConfigurationPeriodicLoadingTask(sspIdentifier string, folderPrefix string, modelConfigurationHandler interfaces.ModelConfigurationHandlerInterface, refreshIntervalMs int) *ModelConfigurationPeriodicLoadingTask {
	task := &ModelConfigurationPeriodicLoadingTask{
		modelConfigurationHandler: modelConfigurationHandler,
	}
	task.PeriodicTaskWithRandomizedStart = NewPeriodicTaskWithRandomizedStart(sspIdentifier, "ModelConfigurationPeriodicLoadingTask", folderPrefix, refreshIntervalMs, task.ExecuteTask)
	return task
}

func (t *ModelConfigurationPeriodicLoadingTask) ExecuteTask() error {
	_, err := t.modelConfigurationHandler.Load()
	if err != nil {
		return fmt.Errorf("error loading model configuration: %v", err)
	}
	return nil
}

func (t *ModelConfigurationPeriodicLoadingTask) Stop() {
	t.PeriodicTaskWithRandomizedStart.Stop()
}

func (t *ModelConfigurationPeriodicLoadingTask) Run() error {
	Logger.Info().Msgf("ModelConfigurationPeriodicLoadingTask running")
	err := t.ExecuteTask()
	if err != nil {
		return fmt.Errorf("error executing ModelConfigurationPeriodicLoadingTask: %v", err)
	}
	t.schedulePeriodicallyWithRandomizedStart()
	return nil
}

// Implements Task. Used for periodically fetching experiment configuration files.
type ExperimentConfigurationPeriodicLoadingTask struct {
	*PeriodicTaskWithRandomizedStart
	experimentConfigurationHandler interfaces.ExperimentConfigurationHandlerInterface
}

func NewExperimentConfigurationPeriodicLoadingTask(sspIdentifier string, folderPrefix string, experimentConfigurationHandler interfaces.ExperimentConfigurationHandlerInterface, refreshIntervalMs int) *ExperimentConfigurationPeriodicLoadingTask {
	task := &ExperimentConfigurationPeriodicLoadingTask{
		experimentConfigurationHandler: experimentConfigurationHandler,
	}
	task.PeriodicTaskWithRandomizedStart = NewPeriodicTaskWithRandomizedStart(sspIdentifier, "ExperimentConfigurationPeriodicLoadingTask", folderPrefix, refreshIntervalMs, task.ExecuteTask)
	return task
}

func (t *ExperimentConfigurationPeriodicLoadingTask) ExecuteTask() error {
	_, err := t.experimentConfigurationHandler.Load()
	if err != nil {
		return fmt.Errorf("error loading model configuration: %v", err)
	}
	return nil
}

func (t *ExperimentConfigurationPeriodicLoadingTask) Stop() {
	t.PeriodicTaskWithRandomizedStart.Stop()
}

func (t *ExperimentConfigurationPeriodicLoadingTask) Run() error {
	Logger.Info().Msgf("ExperimentConfigurationPeriodicLoadingTask running")
	err := t.ExecuteTask()
	if err != nil {
		return fmt.Errorf("error executing ExperimentConfigurationPeriodicLoadingTask: %v", err)
	}
	t.schedulePeriodicallyWithRandomizedStart()
	return nil
}

// Implements Task. Used for periodically fetching model output (result) files.
type ModelResultPeriodicLoadingTask struct {
	*PeriodicTaskWithRandomizedStart
	modelResultHandler interfaces.ModelResultHandlerInterface
}

func NewModelResultPeriodicLoadingTask(sspIdentifier string, folderPrefix string, modelResultHandler interfaces.ModelResultHandlerInterface, refreshIntervalMs int) *ModelResultPeriodicLoadingTask {
	task := &ModelResultPeriodicLoadingTask{
		modelResultHandler: modelResultHandler,
	}
	task.PeriodicTaskWithRandomizedStart = NewPeriodicTaskWithRandomizedStart(sspIdentifier, "ModelResultPeriodicLoadingTask", folderPrefix, refreshIntervalMs, task.ExecuteTask)
	return task
}

func (t *ModelResultPeriodicLoadingTask) ExecuteTask() error {
	Logger.Info().Msgf("ModelResultPeriodicLoadingTask ExecuteTask")
	err := t.modelResultHandler.Load(t.sspIdentifier)
	if err != nil {
		return fmt.Errorf("error loading model result: %v", err)
	}
	return nil
}

func (t *ModelResultPeriodicLoadingTask) Stop() {
	t.PeriodicTaskWithRandomizedStart.Stop()
}

func (t *ModelResultPeriodicLoadingTask) Run() error {
	Logger.Info().Msgf("ModelResultPeriodicLoadingTask running")

	// sleep before initial execution to ensure cache loaded
	time.Sleep(time.Millisecond * 250)

	err := t.ExecuteTask()
	if err != nil {
		return fmt.Errorf("error executing ModelResultPeriodicLoadingTask: %v", err)
	}
	t.schedulePeriodicallyWithRandomizedStart()
	return nil
}

type PeriodicTaskWithRandomizedStart struct {
	sspIdentifier     string
	taskName          string
	folderPrefix      string
	refreshIntervalMs int
	executeTask       func() error
	ticker            *time.Ticker
	stopChan          chan struct{}
}

func (t *PeriodicTaskWithRandomizedStart) Stop() {
	if t.stopChan != nil {
		close(t.stopChan)
	}
	if t.ticker != nil {
		t.ticker.Stop()
	}
}

func NewPeriodicTaskWithRandomizedStart(sspIdentifier string, taskName string, folderPrefix string, refreshIntervalMs int, executeTask func() error) *PeriodicTaskWithRandomizedStart {
	return &PeriodicTaskWithRandomizedStart{
		sspIdentifier:     sspIdentifier,
		taskName:          taskName,
		folderPrefix:      folderPrefix,
		refreshIntervalMs: refreshIntervalMs,
		executeTask:       executeTask,
	}
}

func (t *PeriodicTaskWithRandomizedStart) schedulePeriodicallyWithRandomizedStart() (*time.Ticker, chan struct{}) {
	// Seed the random number generator with the current time
	initialDelay := time.Duration(rand.Intn(1000))
	Logger.Info().Msgf("Task [%s] scheduling every [%dms], starting with delay [%d]", t.taskName, t.refreshIntervalMs, initialDelay)
	// Create a ticker that ticks every refreshIntervalMs seconds
	ticker := time.NewTicker(time.Duration(t.refreshIntervalMs) * time.Millisecond)
	stopChan := make(chan struct{})

	// Start a goroutine to handle the periodic refresh
	go func() {
		defer func() {
			if r := recover(); r != nil {
				err := fmt.Errorf("panic in task [%s]: %v", t.taskName, r)
				Logger.Error().Err(err).Msg("Error in periodic task")
			}
			ticker.Stop()
		}()

		// Sleep for the initial delay
		time.Sleep(initialDelay * time.Millisecond)

		for {
			select {
			case <-ticker.C:
				// This block will execute every refreshIntervalMs seconds
				if err := t.executeTask(); err != nil {
					Logger.Error().Msgf("Error in periodic task: %v", err)
				}
			case <-stopChan:
				return
			}
		}
	}()

	return ticker, stopChan
}
