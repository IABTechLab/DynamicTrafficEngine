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

    /**
     * Returns the JSON extension string for backward compatibility.
     * Callers that previously used {@code getExt()} can use this instead.
     *
     * @return JSON string like {@code {"amazontest":{"learning":1}}}
     */
    public String getExt() {
        return toExt();
    }

    /**
     * Generates the JSON extension representation of this response.
     *
     * @return JSON string with learning value wrapped in amazontest namespace
     */
    public String toExt() {
        return ResponseUtil.buildExtension(Map.of(ResponseUtil.EXTENSION_KEYWORD_LEARNING, learning));
    }

    /**
     * Generates the protobuf representation of this response.
     *
     * @return ResponseMetadata protobuf message containing learning and slot decisions
     */
    public ResponseMetadata toExtProto() {
        return ResponseMetadata.newBuilder()
                .setLearning(learning)
                .addAllSlots(slots.stream().map(Slot::toExtProto).toList())
                .build();
    }
}
