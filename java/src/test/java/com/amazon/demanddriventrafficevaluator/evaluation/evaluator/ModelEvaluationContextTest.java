// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ModelEvaluationContextTest {
    @Mock
    private EvaluationContext mockEvaluationContext;

    private ModelEvaluationContext modelEvaluationContext;

    @BeforeEach
    void setUp() {
        modelEvaluationContext = new ModelEvaluationContext(mockEvaluationContext);
    }

    @Test
    void testConstructor() {
        assertNotNull(modelEvaluationContext);
        assertEquals(mockEvaluationContext, modelEvaluationContext.getEvaluationContext());
        assertNotNull(modelEvaluationContext.getDebugInfo());
        assertTrue(modelEvaluationContext.getDebugInfo().isEmpty());
    }

    @Test
    void testAddDebug() {
        String debugMessage = "Debug message";
        modelEvaluationContext.addDebug(debugMessage);

        List<String> debugInfo = modelEvaluationContext.getDebugInfo();
        assertEquals(1, debugInfo.size());
        assertEquals("[Debug] " + debugMessage + "\n", debugInfo.get(0));
    }

    @Test
    void testAddInfo() {
        String infoMessage = "Info message";
        modelEvaluationContext.addInfo(infoMessage);

        List<String> debugInfo = modelEvaluationContext.getDebugInfo();
        assertEquals(1, debugInfo.size());
        assertEquals("[Info] " + infoMessage + "\n", debugInfo.get(0));
    }

    @Test
    void testAddError() {
        String errorMessage = "Error message";
        modelEvaluationContext.addError(errorMessage);

        List<String> debugInfo = modelEvaluationContext.getDebugInfo();
        assertEquals(1, debugInfo.size());
        assertEquals("[Error] " + errorMessage + "\n", debugInfo.get(0));
    }

    @Test
    void testMultipleAdditions() {
        modelEvaluationContext.addDebug("Debug 1");
        modelEvaluationContext.addInfo("Info 1");
        modelEvaluationContext.addError("Error 1");
        modelEvaluationContext.addDebug("Debug 2");

        List<String> debugInfo = modelEvaluationContext.getDebugInfo();
        assertEquals(4, debugInfo.size());
        assertEquals("[Debug] Debug 1\n", debugInfo.get(0));
        assertEquals("[Info] Info 1\n", debugInfo.get(1));
        assertEquals("[Error] Error 1\n", debugInfo.get(2));
        assertEquals("[Debug] Debug 2\n", debugInfo.get(3));
    }

    @Test
    void testDebugInfoImmutability() {
        modelEvaluationContext.addDebug("Debug 1");
        List<String> debugInfo = modelEvaluationContext.getDebugInfo();

        // Attempt to modify the returned list
        assertThrows(UnsupportedOperationException.class, () -> debugInfo.add("Should not be added"));

        assertEquals(1, modelEvaluationContext.getDebugInfo().size());
    }
}
