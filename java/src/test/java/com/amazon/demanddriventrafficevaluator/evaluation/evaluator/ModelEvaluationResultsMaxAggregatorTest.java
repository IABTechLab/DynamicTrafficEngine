// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import com.amazon.demanddriventrafficevaluator.evaluation.experiment.ExperimentContext;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentDefinition;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelDefinition;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelEvaluationResultsMaxAggregatorTest {
    @Mock
    private EvaluationContext mockContext;
    @Mock
    private ExperimentContext mockExperimentContext;
    @Mock
    private ExperimentDefinition mockExperimentDefinition;

    private ModelEvaluationResultsMaxAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new ModelEvaluationResultsMaxAggregator();
    }

    @Test
    void testAggregateSuccess() {
        // Arrange
        String experimentName = "TestExperiment";
        String treatmentCode = "C";
        int treatmentCodeInInt = 1;
        List<String> modelsInExperiment = Arrays.asList("Model1", "Model2");

        when(mockContext.getExperimentContext()).thenReturn(mockExperimentContext);
        when(mockExperimentContext.getExperimentDefinitionByType("soft-filter")).thenReturn(mockExperimentDefinition);
        when(mockExperimentDefinition.getName()).thenReturn(experimentName);
        when(mockExperimentContext.getModelsByExperiment()).thenReturn(Map.of(experimentName, modelsInExperiment));
        when(mockExperimentContext.getTreatmentCodeInInt(experimentName)).thenReturn(treatmentCodeInInt);
        when(mockExperimentContext.getTreatmentCode(experimentName)).thenReturn(treatmentCode);

        List<ModelEvaluatorOutput> outputs = Arrays.asList(
                createModelEvaluatorOutput("Model1", 0.5, ModelEvaluationStatus.SUCCESS),
                createModelEvaluatorOutput("Model2", 0.8, ModelEvaluationStatus.SUCCESS)
        );
        when(mockContext.getModelEvaluatorOutputs()).thenReturn(outputs);

        // Act
        AggregatedModelEvaluationResult result = aggregator.aggregate(mockContext);

        // Assert
        assertNotNull(result);
        assertEquals(experimentName, result.getExperimentName());
        assertEquals("soft-filter", result.getExperimentType());
        assertEquals(treatmentCode, result.getTreatmentCode());
        assertEquals(treatmentCodeInInt, result.getTreatmentCodeInInt());
        assertEquals(0.8, result.getScore());
        assertEquals(1.0, result.getScoreWithTreatment());
        assertEquals("max", result.getAggregationType());
    }

    @Test
    void testAggregateWithNoModels() {
        // Arrange
        String experimentName = "TestExperiment";
        when(mockContext.getExperimentContext()).thenReturn(mockExperimentContext);
        when(mockExperimentContext.getExperimentDefinitionByType("soft-filter")).thenReturn(mockExperimentDefinition);
        when(mockExperimentDefinition.getName()).thenReturn(experimentName);
        when(mockExperimentContext.getModelsByExperiment()).thenReturn(Map.of(experimentName, Collections.emptyList()));

        // Act
        AggregatedModelEvaluationResult result = aggregator.aggregate(mockContext);

        // Assert
        assertNotNull(result);
        assertEquals(experimentName, result.getExperimentName());
        assertEquals(1.0, result.getScore());
        assertEquals(1.0, result.getScoreWithTreatment());
        verify(mockContext).addError(anyString());
    }

    @Test
    void testAggregateWithNoSuccessfulEvaluations() {
        // Arrange
        String experimentName = "TestExperiment";
        List<String> modelsInExperiment = Arrays.asList("Model1", "Model2");

        when(mockContext.getExperimentContext()).thenReturn(mockExperimentContext);
        when(mockExperimentContext.getExperimentDefinitionByType("soft-filter")).thenReturn(mockExperimentDefinition);
        when(mockExperimentDefinition.getName()).thenReturn(experimentName);
        when(mockExperimentContext.getModelsByExperiment()).thenReturn(Map.of(experimentName, modelsInExperiment));

        List<ModelEvaluatorOutput> outputs = Arrays.asList(
                createModelEvaluatorOutput("Model1", 0.0, ModelEvaluationStatus.ERROR),
                createModelEvaluatorOutput("Model2", 0.0, ModelEvaluationStatus.ERROR)
        );
        when(mockContext.getModelEvaluatorOutputs()).thenReturn(outputs);

        // Act
        AggregatedModelEvaluationResult result = aggregator.aggregate(mockContext);

        // Assert
        assertNotNull(result);
        assertEquals(experimentName, result.getExperimentName());
        assertEquals(1.0, result.getScore());
        assertEquals(1.0, result.getScoreWithTreatment());
        verify(mockContext).addError(anyString());
    }

    @Test
    void testAggregateWithException() {
        // Arrange
        when(mockContext.getExperimentContext()).thenThrow(new RuntimeException("Test exception"));

        // Act
        AggregatedModelEvaluationResult result = aggregator.aggregate(mockContext);

        // Assert
        assertNotNull(result);
        assertEquals("UnknownExperiment", result.getExperimentName());
        assertEquals(1.0, result.getScore());
        assertEquals(1.0, result.getScoreWithTreatment());
        verify(mockContext).addError(anyString());
    }

    private ModelEvaluatorOutput createModelEvaluatorOutput(String modelId, double value, ModelEvaluationStatus status) {
        ModelDefinition modelDefinition = new ModelDefinition();
        modelDefinition.setIdentifier(modelId);
        ModelResult modelResult = ModelResult.builder().value(value).build();
        return ModelEvaluatorOutput.builder()
                .modelDefinition(modelDefinition)
                .modelResult(modelResult)
                .status(status)
                .build();
    }
}
