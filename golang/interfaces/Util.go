// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package interfaces

import "time"

// Interface to provide time.
type TimeProvider interface {
	// Returns the current time, using the "time" package.
	Now() time.Time
}
