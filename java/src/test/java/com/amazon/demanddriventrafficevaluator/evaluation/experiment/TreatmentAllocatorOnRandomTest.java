// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.experiment;

import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentDefinition;
import com.amazon.demanddriventrafficevaluator.repository.entity.TreatmentDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreatmentAllocatorOnRandomTest {

    private TreatmentAllocatorOnRandom allocator;

    @Mock
    private ExperimentDefinition mockExperimentDefinition;

    @Mock
    private ExperimentConfiguration mockExperimentConfiguration;

    @BeforeEach
    void setUp() {
        allocator = new TreatmentAllocatorOnRandom();
    }

    @Test
    void testGetTreatmentCode() {
        String experimentName = "TestExperiment";
        when(mockExperimentDefinition.getName()).thenReturn(experimentName);

        List<TreatmentDefinition> treatments = Arrays.asList(
                TreatmentDefinition.builder().treatmentCode("A").weight(50).build(),
                TreatmentDefinition.builder().treatmentCode("B").weight(50).build(),
                TreatmentDefinition.builder().treatmentCode("C").weight(0).build()
        );
        when(mockExperimentDefinition.getTreatmentDefinitions()).thenReturn(treatments);

        allocator.updateConfiguration(createMockExperimentConfiguration(experimentName, treatments));

        for (int i = 0; i < 10; i++) {
            String treatmentCode = allocator.getTreatmentCode("requestId", mockExperimentDefinition);
            assertTrue(treatmentCode.equals("A") || treatmentCode.equals("B"), "Invalid treatment code: " + treatmentCode);
        }
    }

    @Test
    void testGetTreatmentCodeWithNoThresholds() {
        // Arrange
        String experimentName = "TestExperiment";
        when(mockExperimentDefinition.getName()).thenReturn(experimentName);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                allocator.getTreatmentCode("request", mockExperimentDefinition)
        );
        assertEquals("No thresholds found for experiment: TestExperiment", exception.getMessage());
    }

    @Test
    void testGetTreatmentCodeWithNoTreatments() {
        String experimentName = "TestExperiment";
        List<TreatmentDefinition> treatments = Arrays.asList(
                TreatmentDefinition.builder().treatmentCode("A").weight(50).build(),
                TreatmentDefinition.builder().treatmentCode("B").weight(50).build()
        );
        allocator.updateConfiguration(createMockExperimentConfiguration(experimentName, treatments));

        when(mockExperimentDefinition.getName()).thenReturn(experimentName);
        when(mockExperimentDefinition.getTreatmentDefinitions()).thenReturn(Collections.emptyList());
        assertThrows(IllegalStateException.class, () ->
                allocator.getTreatmentCode("request", mockExperimentDefinition));
    }

    @Test
    void testUpdateConfigurationWithInvalidWeights() {
        // Arrange
        String experimentName = "TestExperiment";
        List<TreatmentDefinition> treatments = Arrays.asList(
                TreatmentDefinition.builder().treatmentCode("A").weight(30).build(),
                TreatmentDefinition.builder().treatmentCode("B").weight(60).build()
        );
        ExperimentConfiguration config = createMockExperimentConfiguration(experimentName, treatments);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                allocator.updateConfiguration(config)
        );
        assertEquals("total weight must be 100, got 90", exception.getMessage());
    }

    @Test
    void testRandDistribution() {
        // This test checks if the random number generation is roughly uniform
        int[] buckets = new int[10];
        int iterations = 1000000;

        for (int i = 0; i < iterations; i++) {
            long rand = allocator.rand();
            int bucket = (int) (rand % 10);
            buckets[bucket]++;
        }

        for (int count : buckets) {
            // Each bucket should have roughly 10% of the iterations
            assertTrue(Math.abs(count - iterations / 10.0) / (iterations / 10.0) < 0.05);
        }
    }

    private ExperimentConfiguration createMockExperimentConfiguration(String experimentName, List<TreatmentDefinition> treatments) {
        ExperimentDefinition experimentDefinition = mock(ExperimentDefinition.class);
        when(experimentDefinition.getTreatmentDefinitions()).thenReturn(treatments);

        Map<String, ExperimentDefinition> experimentDefinitionMap = new HashMap<>();
        experimentDefinitionMap.put(experimentName, experimentDefinition);

        ExperimentConfiguration config = mock(ExperimentConfiguration.class);
        when(config.getExperimentDefinitionByName()).thenReturn(experimentDefinitionMap);

        return config;
    }
}
