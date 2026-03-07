// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class Response {
    private final List<Slot> slots;
    private final String ext;
}
