// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class ModelEvaluationContext {
    private final EvaluationContext evaluationContext;

    private final List<String> debugInfo = new ArrayList<>();

    public ModelEvaluationContext(EvaluationContext evaluationContext) {
        this.evaluationContext = evaluationContext;
    }

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
