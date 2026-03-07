// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import com.amazon.demanddriventrafficevaluator.evaluation.experiment.ExperimentContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class EvaluationContextTest {

    private EvaluationContext evaluationContext;

    @Mock
    private OpenRtbRequestContext mockOpenRtbRequestContext;

    @Mock
    private ExperimentContext mockExperimentContext;

    @Mock
    private ModelEvaluatorOutput mockModelEvaluatorOutput;

    @Mock
    private AggregatedModelEvaluationResult mockAggregatedModelEvaluationResult;

    @BeforeEach
    void setUp() {
        evaluationContext = new EvaluationContext();
    }

    @Test
    void testInitialization() {
        assertNotNull(evaluationContext.getDebugInfo());
        assertTrue(evaluationContext.getDebugInfo().isEmpty());
        assertNull(evaluationContext.getOpenRtbRequestContext());
        assertNull(evaluationContext.getRequestId());
        assertNull(evaluationContext.getExperimentContext());
        assertNull(evaluationContext.getModelEvaluatorOutputs());
        assertNull(evaluationContext.getAggregatedModelEvaluationResult());
    }

    @Test
    void testSetOpenRtbRequestContext() {
        evaluationContext.setOpenRtbRequestContext(mockOpenRtbRequestContext);
        assertEquals(mockOpenRtbRequestContext, evaluationContext.getOpenRtbRequestContext());
    }

    @Test
    void testSetRequestId() {
        String requestId = "test-request-id";
        evaluationContext.setRequestId(requestId);
        assertEquals(requestId, evaluationContext.getRequestId());
    }

    @Test
    void testSetExperimentContext() {
        evaluationContext.setExperimentContext(mockExperimentContext);
        assertEquals(mockExperimentContext, evaluationContext.getExperimentContext());
    }

    @Test
    void testSetModelEvaluatorOutputs() {
        List<ModelEvaluatorOutput> outputs = List.of(mockModelEvaluatorOutput);
        evaluationContext.setModelEvaluatorOutputs(outputs);
        assertEquals(outputs, evaluationContext.getModelEvaluatorOutputs());
    }

    @Test
    void testSetAggregatedModelEvaluationResult() {
        evaluationContext.setAggregatedModelEvaluationResult(mockAggregatedModelEvaluationResult);
        assertEquals(mockAggregatedModelEvaluationResult, evaluationContext.getAggregatedModelEvaluationResult());
    }

    @Test
    void testAddDebug() {
        String debugInfo = "Debug information";
        evaluationContext.addDebug(debugInfo);
        List<String> debugInfoList = evaluationContext.getDebugInfo();
        assertEquals(1, debugInfoList.size());
        assertEquals("[Debug] " + debugInfo + "\n", debugInfoList.get(0));
    }

    @Test
    void testAddInfo() {
        String infoMessage = "Info message";
        evaluationContext.addInfo(infoMessage);
        List<String> debugInfoList = evaluationContext.getDebugInfo();
        assertEquals(1, debugInfoList.size());
        assertEquals("[Info] " + infoMessage + "\n", debugInfoList.get(0));
    }

    @Test
    void testAddError() {
        String errorMessage = "Error message";
        evaluationContext.addError(errorMessage);
        List<String> debugInfoList = evaluationContext.getDebugInfo();
        assertEquals(1, debugInfoList.size());
        assertEquals("[Error] " + errorMessage + "\n", debugInfoList.get(0));
    }

    @Test
    void testMultipleDebugInfoAdditions() {
        evaluationContext.addDebug("Debug 1");
        evaluationContext.addInfo("Info 1");
        evaluationContext.addError("Error 1");
        evaluationContext.addDebug("Debug 2");

        List<String> debugInfoList = evaluationContext.getDebugInfo();
        assertEquals(4, debugInfoList.size());
        assertEquals("[Debug] Debug 1\n", debugInfoList.get(0));
        assertEquals("[Info] Info 1\n", debugInfoList.get(1));
        assertEquals("[Error] Error 1\n", debugInfoList.get(2));
        assertEquals("[Debug] Debug 2\n", debugInfoList.get(3));
    }

    @Test
    void testDebugInfoImmutability() {
        evaluationContext.addDebug("Debug 1");
        List<String> debugInfoList = evaluationContext.getDebugInfo();

        // Attempt to modify the returned list
        assertThrows(UnsupportedOperationException.class, () -> debugInfoList.add("Should not be added"));

        assertEquals(1, evaluationContext.getDebugInfo().size());
    }
}
