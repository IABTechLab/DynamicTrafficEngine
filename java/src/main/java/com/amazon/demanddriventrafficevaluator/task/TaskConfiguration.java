// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class TaskConfiguration {
    private final long periodMs;
    private final int maximumAttempts;
    private final long minDelayBeforeAttemptMs;
    private final long maxDelayBeforeAttemptMs;
}
