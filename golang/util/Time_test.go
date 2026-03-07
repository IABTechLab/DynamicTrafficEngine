// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package util

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

type TimeTestSuite struct {
	suite.Suite
}

func TestNewRealTimeProvider(t *testing.T) {
	provider := NewRealTimeProvider()

	// Check if the returned provider is not nil
	assert.NotNil(t, provider, "NewRealTimeProvider should not return nil")

	// Check if the returned provider is of type *RealTimeProvider
	_, ok := provider.(*RealTimeProvider)
	assert.True(t, ok, "NewRealTimeProvider should return a *RealTimeProvider")
}

func TestRealTimeProviderNow(t *testing.T) {
	provider := &RealTimeProvider{}

	// Get the current time before calling Now()
	before := time.Now()

	// Small delay to ensure time has passed
	time.Sleep(time.Millisecond)

	// Call Now() method
	result := provider.Now()

	// Small delay to ensure time has passed
	time.Sleep(time.Millisecond)

	// Get the current time after calling Now()
	after := time.Now()

	// Check if the returned time is between before and after
	assert.True(t, result.After(before) || result.Equal(before), "Now() should return a time after or equal to 'before'")
	assert.True(t, result.Before(after) || result.Equal(after), "Now() should return a time before or equal to 'after'")
}

func TestTimeSuite(t *testing.T) {
	suite.Run(t, new(TimeTestSuite))
}
