// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class BidRequestEvaluatorOutput {
    private final Response response;
}
