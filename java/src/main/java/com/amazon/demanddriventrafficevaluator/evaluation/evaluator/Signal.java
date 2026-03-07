// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class Signal {
    private final String name;
    private final String version;
    private final String status;
    @Builder.Default
    private final String debugInfo = "";
}
