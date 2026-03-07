// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.util;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.AggregatedModelEvaluationResult;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.EvaluationContext;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluationContext;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluationStatus;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluatorOutput;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.Signal;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.Slot;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResponseUtilTest {
    @Mock
    private EvaluationContext mockEvaluationContext;
    @Mock
    private AggregatedModelEvaluationResult mockAggregatedResult;
    @Mock
    private ModelEvaluationContext mockModelEvaluationContext;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testBuildSlots() {
        // Arrange
        when(mockEvaluationContext.getAggregatedModelEvaluationResult()).thenReturn(mockAggregatedResult);
        when(mockAggregatedResult.getScoreWithTreatment()).thenReturn(0.8);
        when(mockAggregatedResult.getScore()).thenReturn(0.7);

        // Act
        List<Slot> slots = ResponseUtil.buildSlots(mockEvaluationContext);

        // Assert
        assertNotNull(slots);
        assertEquals(1, slots.size());
        Slot slot = slots.get(0);
        assertEquals(0.8, slot.getFilterDecision());
        assertTrue(slot.getExt().contains("\"decision\":0.7"));
    }

    @Test
    void testBuildExtension() throws Exception {
        // Arrange
        Map<String, Object> extensionMapping = Map.of("key1", "value1", "key2", 42);

        // Act
        String extension = ResponseUtil.buildExtension(extensionMapping);

        // Assert
        assertNotNull(extension);
        JsonNode rootNode = objectMapper.readTree(extension);
        JsonNode amazontest = rootNode.get("amazontest");
        assertNotNull(amazontest);
        assertEquals("value1", amazontest.get("key1").asText());
        assertEquals(42, amazontest.get("key2").asInt());
    }

    @Test
    void testBuildExtensionWithException() {
        // Arrange
        Map<String, Object> extensionMapping = Map.of("key", new Object() {
            @Override
            public String toString() {
                throw new RuntimeException("Test exception");
            }
        });

        // Act
        String extension = ResponseUtil.buildExtension(extensionMapping);

        // Assert
        assertEquals("", extension);
    }

    @Test
    void testBuildSignals() {
        // Arrange
        ModelDefinition def1 = new ModelDefinition();
        def1.setName("Model1");
        def1.setVersion("1.0");
        ModelDefinition def2 = new ModelDefinition();
        def2.setName("Model2");
        def2.setVersion("2.0");
        ModelEvaluatorOutput output1 = ModelEvaluatorOutput.builder()
                .modelDefinition(def1)
                .status(ModelEvaluationStatus.SUCCESS)
                .build();
        ModelEvaluatorOutput output2 = ModelEvaluatorOutput.builder()
                .modelDefinition(def2)
                .status(ModelEvaluationStatus.ERROR)
                .build();
        List<ModelEvaluatorOutput> outputs = Arrays.asList(output1, output2);

        // Act
        List<Signal> signals = ResponseUtil.buildSignals(outputs);

        // Assert
        assertNotNull(signals);
        assertEquals(2, signals.size());
        assertEquals("Model1", signals.get(0).getName());
        assertEquals("1.0", signals.get(0).getVersion());
        assertEquals("SUCCESS", signals.get(0).getStatus());
        assertEquals("Model2", signals.get(1).getName());
        assertEquals("2.0", signals.get(1).getVersion());
        assertEquals("ERROR", signals.get(1).getStatus());
    }

    @Test
    void testGetDebugInfo() {
        // Arrange
        List<String> requestLevelDebugInfo = Arrays.asList("Request Debug 1", "Request Debug 2");
        List<String> modelLevelDebugInfo = Arrays.asList("Model Debug 1", "Model Debug 2");

        when(mockModelEvaluationContext.getEvaluationContext()).thenReturn(mockEvaluationContext);
        when(mockEvaluationContext.getDebugInfo()).thenReturn(requestLevelDebugInfo);
        when(mockModelEvaluationContext.getDebugInfo()).thenReturn(modelLevelDebugInfo);

        // Act
        String debugInfo = ResponseUtil.getDebugInfo(mockModelEvaluationContext);

        // Assert
        assertNotNull(debugInfo);
        assertTrue(debugInfo.contains("[RequestLevelDebugInfo]"));
        assertTrue(debugInfo.contains("Request Debug 1"));
        assertTrue(debugInfo.contains("Request Debug 2"));
        assertTrue(debugInfo.contains("[ModelLevelDebugInfo]"));
        assertTrue(debugInfo.contains("Model Debug 1"));
        assertTrue(debugInfo.contains("Model Debug 2"));
    }
}
