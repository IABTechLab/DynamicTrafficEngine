// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class BidRequestEvaluatorInput {
    private final String openRtbRequest;
    private final Map<String, List<String>> openRtbRequestMap;
}
