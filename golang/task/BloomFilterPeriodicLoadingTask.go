// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package task

import (
	"fmt"
	"time"

	"golang.a2z.com/demanddriventrafficevaluator/bloomfilter"
)

// BloomFilterPeriodicLoadingTask implements Task. Used for periodically loading bloom filter models from S3.
type BloomFilterPeriodicLoadingTask struct {
	*PeriodicTaskWithRandomizedStart
	bloomFilterLoader *bloomfilter.BloomFilterLoader
	sspIdentifier     string
	folderPrefix      string
}

func NewBloomFilterPeriodicLoadingTask(sspIdentifier string, folderPrefix string, bloomFilterLoader *bloomfilter.BloomFilterLoader, refreshIntervalMs int) *BloomFilterPeriodicLoadingTask {
	task := &BloomFilterPeriodicLoadingTask{
		bloomFilterLoader: bloomFilterLoader,
		sspIdentifier:     sspIdentifier,
		folderPrefix:      folderPrefix,
	}
	task.PeriodicTaskWithRandomizedStart = NewPeriodicTaskWithRandomizedStart(sspIdentifier, "BloomFilterPeriodicLoadingTask", folderPrefix, refreshIntervalMs, task.ExecuteTask)
	return task
}

func (t *BloomFilterPeriodicLoadingTask) ExecuteTask() error {
	Logger.Info().Msgf("BloomFilterPeriodicLoadingTask ExecuteTask")
	err := t.bloomFilterLoader.Load(t.sspIdentifier, t.folderPrefix)
	if err != nil {
		return fmt.Errorf("error loading bloom filter: %v", err)
	}
	return nil
}

func (t *BloomFilterPeriodicLoadingTask) Stop() {
	t.PeriodicTaskWithRandomizedStart.Stop()
}

func (t *BloomFilterPeriodicLoadingTask) Run() error {
	Logger.Info().Msgf("BloomFilterPeriodicLoadingTask running")

	// sleep before initial execution to ensure model configuration is loaded
	time.Sleep(time.Millisecond * 250)

	err := t.ExecuteTask()
	if err != nil {
		return fmt.Errorf("error executing BloomFilterPeriodicLoadingTask: %v", err)
	}
	t.schedulePeriodicallyWithRandomizedStart()
	return nil
}
