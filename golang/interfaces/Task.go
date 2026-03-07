// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package interfaces

// An interface that represents a repeatable task that is meant to run periodically.
type Task interface {
	// Initialize a routine that schedules periodic executions of the task.
	Run() error

	// Stops the routine that schedules periodic executions of the task.
	Stop()

	// Executes the task logic.
	ExecuteTask() error
}
