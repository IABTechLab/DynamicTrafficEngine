// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.protobuf.ResponseMetadata;
import com.amazon.demanddriventrafficevaluator.util.ResponseUtil;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Builder
@Data
public class Response {
    private final List<Slot> slots;
    private final int learning;

    public String toExt() {
        return ResponseUtil.buildExtension(Map.of(ResponseUtil.EXTENSION_KEYWORD_LEARNING, learning));
    }

    public ResponseMetadata toExtProto() {
        return ResponseMetadata.newBuilder().setLearning(learning).addAllSlots(slots.stream().map(s -> s.toExtProto()).toList()).build();
    }

}
