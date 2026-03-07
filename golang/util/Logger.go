// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package util

import (
	"os"
	"sync"

	"github.com/rs/zerolog"
)

var (
	globalLogger zerolog.Logger
	once         sync.Once
)

func initLogger() {
	globalLogger = zerolog.New(os.Stdout).
		With().
		Timestamp().
		Str("library", "demand-driven-traffic-evaluator").
		Logger().
		Level(zerolog.ErrorLevel)
}

func GetLogger() zerolog.Logger {
	once.Do(initLogger)
	return globalLogger
}

func SetGlobalLevel(level zerolog.Level) {
	zerolog.SetGlobalLevel(level)
}

func WithComponent(component string) {
	globalLogger = GetLogger().With().Str("component", component).Logger()
}
