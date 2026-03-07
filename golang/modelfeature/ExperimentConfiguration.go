// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package modelfeature

import (
	"fmt"

	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
)

// Handles usages of a experiment configuration file.
type ExperimentConfigurationHandler struct {
	ConfigurationHandler interfaces.ConfigurationHandlerInterface[interfaces.ExperimentConfiguration]
	TrafficAllocator     interfaces.TrafficAllocatorInterface
}

func NewExperimentConfigurationHandler(configurationHandler interfaces.ConfigurationHandlerInterface[interfaces.ExperimentConfiguration], trafficAllocator interfaces.TrafficAllocatorInterface) *ExperimentConfigurationHandler {
	return &ExperimentConfigurationHandler{
		ConfigurationHandler: configurationHandler,
		TrafficAllocator:     trafficAllocator,
	}
}

func (t *ExperimentConfigurationHandler) Load() (bool, error) {
	load, err := t.ConfigurationHandler.Load()
	if err != nil {
		return false, fmt.Errorf("failed to load experiment configuration: %w", err)
	}

	if !load {
		return false, nil
	}

	Logger.Debug().Msg("Start providing")
	// there is a chance that provide does not work immediately after load due to the delay between cache set and get
	// to avoid it, adding the cache.Wait() after the cache.Set
	config, provideErr := t.ConfigurationHandler.Provide()
	if provideErr != nil {
		return false, fmt.Errorf("failed to provide experiment configuration: %w", provideErr)
	}

	if updateErr := t.TrafficAllocator.UpdateConfiguration(config); updateErr != nil {
		return false, fmt.Errorf("failed to update traffic allocator configuration: %w", updateErr)
	}

	return true, nil
}

func (t *ExperimentConfigurationHandler) Provide() (*interfaces.ExperimentConfiguration, error) {
	return t.ConfigurationHandler.Provide()
}
