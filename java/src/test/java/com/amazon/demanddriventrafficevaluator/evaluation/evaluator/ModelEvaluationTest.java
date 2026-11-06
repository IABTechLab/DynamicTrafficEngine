// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import com.amazon.demanddriventrafficevaluator.repository.entity.ModelDefinition;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelResult;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelValueType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelEvaluationTest {

    private static ModelDefinition definition() {
        ModelDefinition definition = new ModelDefinition();
        definition.setIdentifier("adsp_low-value_v2");
        definition.setName("low-value");
        definition.setVersion("v2");
        definition.setType(ModelValueType.LowValue);
        return definition;
    }

    @Test
    void from_success_copiesAllFields() {
        ModelEvaluatorOutput output = ModelEvaluatorOutput.builder()
                .status(ModelEvaluationStatus.SUCCESS)
                .modelDefinition(definition())
                .modelResult(ModelResult.builder()
                        .value(0.0)
                        .values(new ArrayList<>(List.of(0.0, 1.0)))
                        .keys(new ArrayList<>(List.of("USA", "CAN")))
                        .build())
                .build();

        ModelEvaluation evaluation = ModelEvaluation.from(output);

        assertEquals("adsp_low-value_v2", evaluation.getIdentifier());
        assertEquals("low-value", evaluation.getName());
        assertEquals("v2", evaluation.getVersion());
        assertEquals(ModelValueType.LowValue, evaluation.getType());
        assertEquals(ModelEvaluationStatus.SUCCESS, evaluation.getStatus());
        assertEquals(Double.valueOf(0.0), evaluation.getValue());
        assertEquals(List.of(0.0, 1.0), evaluation.getValues());
        assertEquals(List.of("USA", "CAN"), evaluation.getKeys());
    }

    @ParameterizedTest
    @EnumSource(value = ModelEvaluationStatus.class, names = {"ERROR", "TIMEOUT"})
    void from_withoutModelResult_hasNullValueAndEmptyLists(ModelEvaluationStatus status) {
        ModelEvaluatorOutput output = ModelEvaluatorOutput.builder()
                .status(status)
                .modelDefinition(definition())
                .build();

        ModelEvaluation evaluation = ModelEvaluation.from(output);

        assertEquals(status, evaluation.getStatus());
        assertEquals("adsp_low-value_v2", evaluation.getIdentifier());
        assertNull(evaluation.getValue());
        assertTrue(evaluation.getValues().isEmpty());
        assertTrue(evaluation.getKeys().isEmpty());
    }

    @Test
    void from_emptyOutput_toleratesNulls() {
        ModelEvaluation evaluation = ModelEvaluation.from(ModelEvaluatorOutput.builder().build());

        assertNull(evaluation.getIdentifier());
        assertNull(evaluation.getName());
        assertNull(evaluation.getVersion());
        assertNull(evaluation.getType());
        assertNull(evaluation.getStatus());
        assertNull(evaluation.getValue());
        assertTrue(evaluation.getValues().isEmpty());
        assertTrue(evaluation.getKeys().isEmpty());
    }

    @Test
    void from_nullElementsInKeys_tolerated() {
        ModelEvaluatorOutput output = ModelEvaluatorOutput.builder()
                .status(ModelEvaluationStatus.SUCCESS)
                .modelResult(ModelResult.builder().value(1.0).keys(Arrays.asList("a", null)).build())
                .build();

        assertEquals(Arrays.asList("a", null), ModelEvaluation.from(output).getKeys());
    }

    @Test
    void from_isDetachedFromSourceAndUnmodifiable() {
        ModelDefinition definition = definition();
        List<Double> values = new ArrayList<>(List.of(0.0));
        List<String> keys = new ArrayList<>(List.of("USA"));
        ModelEvaluatorOutput output = ModelEvaluatorOutput.builder()
                .status(ModelEvaluationStatus.SUCCESS)
                .modelDefinition(definition)
                .modelResult(ModelResult.builder().value(0.0).values(values).keys(keys).build())
                .build();

        ModelEvaluation evaluation = ModelEvaluation.from(output);

        // Mutating the sources (including the shared, mutable model definition) must not show through.
        values.add(1.0);
        keys.add("CAN");
        definition.setIdentifier("changed");
        definition.setName("changed");

        assertEquals("adsp_low-value_v2", evaluation.getIdentifier());
        assertEquals("low-value", evaluation.getName());
        assertEquals(List.of(0.0), evaluation.getValues());
        assertEquals(List.of("USA"), evaluation.getKeys());

        assertThrows(UnsupportedOperationException.class, () -> evaluation.getValues().add(2.0));
        assertThrows(UnsupportedOperationException.class, () -> evaluation.getKeys().add("x"));
    }

    @Test
    void builder_copiesListsAndDefaultsValue() {
        List<String> keys = new ArrayList<>(List.of("k"));
        ModelEvaluation evaluation = ModelEvaluation.builder()
                .identifier("id")
                .status(ModelEvaluationStatus.SUCCESS)
                .keys(keys)
                .build();
        keys.add("other");

        assertEquals(List.of("k"), evaluation.getKeys());
        assertNull(evaluation.getValue());
        assertTrue(evaluation.getValues().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> evaluation.getKeys().add("x"));
    }

    @Test
    void serializesWithPlainObjectMapper() throws Exception {
        ModelEvaluation ok = ModelEvaluation.builder().identifier("id").status(ModelEvaluationStatus.SUCCESS)
                .value(0.0).keys(List.of("k")).build();
        ModelEvaluation error = ModelEvaluation.builder().identifier("id").status(ModelEvaluationStatus.ERROR).build();

        String json = new ObjectMapper().writeValueAsString(List.of(ok, error));

        assertTrue(json.contains("\"value\":0.0"), json);
        assertTrue(json.contains("\"value\":null"), json);
        assertTrue(json.contains("\"keys\":[\"k\"]"), json);
    }

    @Test
    void toString_omitsRequestData() {
        ModelEvaluation evaluation = ModelEvaluation.builder()
                .identifier("adsp_low-value_v2")
                .status(ModelEvaluationStatus.SUCCESS)
                .value(0.0)
                .keys(List.of("SECRET-KEY"))
                .build();

        assertTrue(evaluation.toString().contains("adsp_low-value_v2"), evaluation.toString());
        assertFalse(evaluation.toString().contains("SECRET"), evaluation.toString());
    }
}
