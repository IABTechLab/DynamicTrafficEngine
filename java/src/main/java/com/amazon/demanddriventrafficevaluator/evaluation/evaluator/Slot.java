// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.protobuf.SlotMetadata;
import com.amazon.demanddriventrafficevaluator.util.ResponseUtil;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Builder
@Data
public class Slot {
    private final double filterDecision;
    private final double decision;

    public String toExt() {
        return ResponseUtil.buildExtension(Map.of(ResponseUtil.EXTENSION_KEYWORD_DECISION, decision));
    }

    public SlotMetadata toExtProto() {
        return SlotMetadata.newBuilder().setDecision(decision).build();
    }
}
