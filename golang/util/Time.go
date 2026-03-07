// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package util

import (
	"time"

	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
)

type RealTimeProvider struct{}

func (RealTimeProvider) Now() time.Time {
	return time.Now()
}

func NewRealTimeProvider() interfaces.TimeProvider {
	return &RealTimeProvider{}
}
