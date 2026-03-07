// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import com.amazon.demanddriventrafficevaluator.evaluation.experiment.ExperimentContext;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@NoArgsConstructor
@Getter
public class EvaluationContext {
    private final List<String> debugInfo = new ArrayList<>();
    @Setter
    private OpenRtbRequestContext openRtbRequestContext;
    @Setter
    private String requestId;
    @Setter
    private ExperimentContext experimentContext;
    @Setter
    private List<ModelEvaluatorOutput> modelEvaluatorOutputs;
    @Setter
    private AggregatedModelEvaluationResult aggregatedModelEvaluationResult;

    public void addDebug(String info) {
        debugInfo.add("[Debug] " + info + "\n");
    }

    public void addInfo(String info) {
        debugInfo.add("[Info] " + info + "\n");
    }

    public void addError(String info) {
        debugInfo.add("[Error] " + info + "\n");
    }

    // Return an unmodifiable view of the debug info
    public List<String> getDebugInfo() {
        return Collections.unmodifiableList(debugInfo);
    }
}
