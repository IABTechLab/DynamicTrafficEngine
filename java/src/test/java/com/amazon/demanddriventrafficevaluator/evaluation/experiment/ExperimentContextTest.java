// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.experiment;

import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperimentContextTest {
    @Mock
    private ExperimentConfiguration mockConfiguration;

    private ExperimentContext experimentContext;

    private Map<String, String> experimentArrangement;
    private Map<String, ExperimentDefinition> experimentDefinitionByName;
    private Map<String, String> modelToExperiment;

    @BeforeEach
    void setUp() {

        experimentArrangement = new HashMap<>();
        experimentArrangement.put("exp1", "T");
        experimentArrangement.put("exp2", "C");

        experimentDefinitionByName = new HashMap<>();
        experimentDefinitionByName.put("exp1", ExperimentDefinition.builder()
                .name("exp1")
                .type("type1")
                .build());
        experimentDefinitionByName.put("exp2", ExperimentDefinition.builder()
                .name("exp2")
                .type("type2")
                .build());

        modelToExperiment = new HashMap<>();
        modelToExperiment.put("model1", "exp1");
        modelToExperiment.put("model2", "exp1");
        modelToExperiment.put("model3", "exp2");

        when(mockConfiguration.getExperimentDefinitionByName()).thenReturn(experimentDefinitionByName);
        when(mockConfiguration.getModelToExperiment()).thenReturn(modelToExperiment);

        experimentContext = new ExperimentContext(experimentArrangement, mockConfiguration);
    }

    @Test
    void testGetModelIdentifiers() {
        List<String> modelIdentifiers = experimentContext.getModelIdentifiers();
        assertEquals(3, modelIdentifiers.size());
        assertTrue(modelIdentifiers.containsAll(Arrays.asList("model1", "model2", "model3")));
    }

    @Test
    void testGetTreatmentCode() {
        assertEquals("T", experimentContext.getTreatmentCode("exp1"));
        assertEquals("C", experimentContext.getTreatmentCode("exp2"));
        assertNull(experimentContext.getTreatmentCode("nonexistent"));
    }

    @Test
    void testGetTreatmentCodeInInt() {
        assertEquals(0, experimentContext.getTreatmentCodeInInt("exp1"));
        assertEquals(1, experimentContext.getTreatmentCodeInInt("exp2"));
        assertEquals(1, experimentContext.getTreatmentCodeInInt("nonexistent"));
    }

    @Test
    void testGetExperimentDefinition() {
        ExperimentDefinition exp1Definition = experimentContext.getExperimentDefinition("exp1");
        assertNotNull(exp1Definition);
        assertEquals("exp1", exp1Definition.getName());
        assertEquals("type1", exp1Definition.getType());

        assertNull(experimentContext.getExperimentDefinition("nonexistent"));
    }

    @Test
    void testGetExperimentDefinitionByModel() {
        ExperimentDefinition exp1Definition = experimentContext.getExperimentDefinitionByModel("model1");
        assertNotNull(exp1Definition);
        assertEquals("exp1", exp1Definition.getName());
        assertEquals("type1", exp1Definition.getType());

        ExperimentDefinition exp2Definition = experimentContext.getExperimentDefinitionByModel("model3");
        assertNotNull(exp2Definition);
        assertEquals("exp2", exp2Definition.getName());
        assertEquals("type2", exp2Definition.getType());

        assertNull(experimentContext.getExperimentDefinitionByModel("nonexistent"));
    }

    @Test
    void testGetExperimentDefinitionByType() {
        ExperimentDefinition type1Definition = experimentContext.getExperimentDefinitionByType("type1");
        assertNotNull(type1Definition);
        assertEquals("exp1", type1Definition.getName());
        assertEquals("type1", type1Definition.getType());

        assertThrows(IllegalStateException.class, () -> experimentContext.getExperimentDefinitionByType("nonexistent"));
    }

    @Test
    void testGetModelsByExperiment() {
        Map<String, List<String>> modelsByExperiment = experimentContext.getModelsByExperiment();
        assertEquals(2, modelsByExperiment.size());
        assertTrue(modelsByExperiment.containsKey("exp1"));
        assertTrue(modelsByExperiment.containsKey("exp2"));
        assertEquals(2, modelsByExperiment.get("exp1").size());
        assertEquals(1, modelsByExperiment.get("exp2").size());
        assertTrue(modelsByExperiment.get("exp1").containsAll(Arrays.asList("model1", "model2")));
        assertTrue(modelsByExperiment.get("exp2").contains("model3"));
    }

}
