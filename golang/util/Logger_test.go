// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package util

import (
	"bytes"
	"encoding/json"
	"sync"
	"testing"

	"github.com/rs/zerolog"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

type LoggerTestSuite struct {
	suite.Suite
	buf *bytes.Buffer
}

func (suite *LoggerTestSuite) SetupTest() {
	// Reset global variables
	globalLogger = zerolog.Logger{}
	once = sync.Once{}

	// Create a buffer for capturing log output
	suite.buf = &bytes.Buffer{}

	// Override the global logger to write to our buffer
	_ = GetLogger()
	globalLogger = zerolog.New(suite.buf).
		With().
		Timestamp().
		Str("library", "demand-driven-traffic-evaluator").
		Logger().
		Level(zerolog.DebugLevel)

	// Reset global level to Debug for testing
	zerolog.SetGlobalLevel(zerolog.DebugLevel)
}

func (suite *LoggerTestSuite) TestGetLogger() {
	logger := GetLogger()
	assert.NotNil(suite.T(), logger)

	logger.Info().Msg("Test message")

	var logEntry map[string]interface{}
	err := json.Unmarshal(suite.buf.Bytes(), &logEntry)
	assert.NoError(suite.T(), err)
	assert.Contains(suite.T(), logEntry, "time")
	assert.Equal(suite.T(), "demand-driven-traffic-evaluator", logEntry["library"])
}

func (suite *LoggerTestSuite) TestSetGlobalLevel() {
	logger := GetLogger()
	SetGlobalLevel(zerolog.InfoLevel)
	assert.Equal(suite.T(), zerolog.InfoLevel, zerolog.GlobalLevel())

	suite.buf.Reset() // Clear the buffer

	// Debug messages should not be logged
	logger.Debug().Msg("This should not be logged")
	assert.Empty(suite.T(), suite.buf.String())

	// Info messages should be logged
	logger.Info().Msg("This should be logged")
	assert.NotEmpty(suite.T(), suite.buf.String())
}

func (suite *LoggerTestSuite) TestWithComponent() {
	WithComponent("test-component")
	logger := GetLogger()
	logger.Info().Msg("Test message")

	var logEntry map[string]interface{}
	err := json.Unmarshal(suite.buf.Bytes(), &logEntry)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "test-component", logEntry["component"])
}

func TestLoggerSuite(t *testing.T) {
	suite.Run(t, new(LoggerTestSuite))
}
