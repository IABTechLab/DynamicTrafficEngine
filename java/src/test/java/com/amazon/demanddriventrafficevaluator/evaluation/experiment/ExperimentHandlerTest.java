// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.experiment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentHandlerTest {
    private ExperimentHandler experimentHandler;

    @BeforeEach
    void setUp() {
        experimentHandler = new ExperimentHandler();
    }

    @Test
    void testInitialState() {
        assertTrue(experimentHandler.getExperimentArrangement().isEmpty());
    }

    @Test
    void testAssignTreatmentOnExperiment() {
        experimentHandler.assignTreatmentOnExperiment("exp1", "T");

        Map<String, String> arrangement = experimentHandler.getExperimentArrangement();
        assertEquals(1, arrangement.size());
        assertEquals("T", arrangement.get("exp1"));
    }

    @Test
    void testAssignMultipleTreatments() {
        experimentHandler.assignTreatmentOnExperiment("exp1", "T");
        experimentHandler.assignTreatmentOnExperiment("exp2", "C");

        Map<String, String> arrangement = experimentHandler.getExperimentArrangement();
        assertEquals(2, arrangement.size());
        assertEquals("T", arrangement.get("exp1"));
        assertEquals("C", arrangement.get("exp2"));
    }

    @Test
    void testOverwriteExistingTreatment() {
        experimentHandler.assignTreatmentOnExperiment("exp1", "T");
        experimentHandler.assignTreatmentOnExperiment("exp1", "C");

        Map<String, String> arrangement = experimentHandler.getExperimentArrangement();
        assertEquals(1, arrangement.size());
        assertEquals("C", arrangement.get("exp1"));
    }

    @Test
    void testAssignNullExperimentName() {
        assertDoesNotThrow(() -> experimentHandler.assignTreatmentOnExperiment(null, "T"));

        Map<String, String> arrangement = experimentHandler.getExperimentArrangement();
        assertEquals(1, arrangement.size());
        assertTrue(arrangement.containsKey(null));
        assertEquals("T", arrangement.get(null));
    }

    @Test
    void testAssignNullTreatmentCode() {
        assertDoesNotThrow(() -> experimentHandler.assignTreatmentOnExperiment("exp1", null));

        Map<String, String> arrangement = experimentHandler.getExperimentArrangement();
        assertEquals(1, arrangement.size());
        assertNull(arrangement.get("exp1"));
    }

    @Test
    void testGetExperimentArrangementImmutability() {
        experimentHandler.assignTreatmentOnExperiment("exp1", "T");

        Map<String, String> arrangement = experimentHandler.getExperimentArrangement();
        assertThrows(UnsupportedOperationException.class, () -> arrangement.put("exp2", "C"));

        Map<String, String> updatedArrangement = experimentHandler.getExperimentArrangement();
        assertEquals(1, updatedArrangement.size());
        assertFalse(updatedArrangement.containsKey("exp2"));
    }
}
