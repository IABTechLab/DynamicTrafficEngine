// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.task;

import lombok.Getter;

@Getter
public abstract class InitializerTask implements Runnable {
    private final String name;
    private final int maximumAttempts;
    private final long minDelayBeforeAttemptMs;
    private final long maxDelayBeforeAttemptMs;

    protected InitializerTask(
            String name,
            int maximumAttempts,
            long minDelayBeforeAttemptMs,
            long maxDelayBeforeAttemptMs
    ) {
        this.name = name;
        this.maximumAttempts = maximumAttempts;
        this.minDelayBeforeAttemptMs = minDelayBeforeAttemptMs;
        this.maxDelayBeforeAttemptMs = maxDelayBeforeAttemptMs;
    }
}
