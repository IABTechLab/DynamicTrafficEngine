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

    /**
     * Returns the JSON extension string for backward compatibility.
     * Callers that previously used {@code getExt()} can use this instead.
     *
     * @return JSON string like {@code {"amazontest":{"decision":0.0}}}
     */
    public String getExt() {
        return toExt();
    }

    /**
     * Generates the JSON extension representation of this slot.
     *
     * @return JSON string with decision value wrapped in amazontest namespace
     */
    public String toExt() {
        return ResponseUtil.buildExtension(Map.of(ResponseUtil.EXTENSION_KEYWORD_DECISION, decision));
    }

    /**
     * Generates the protobuf representation of this slot.
     *
     * @return SlotMetadata protobuf message containing the decision value
     */
    public SlotMetadata toExtProto() {
        return SlotMetadata.newBuilder().setDecision(decision).build();
    }
}
