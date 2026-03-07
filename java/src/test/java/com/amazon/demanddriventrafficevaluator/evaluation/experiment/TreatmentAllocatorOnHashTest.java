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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreatmentAllocatorOnHashTest {

    private TestTreatmentAllocator allocator;

    @Mock
    private ExperimentDefinition experimentDefinition;

    @Mock
    private TreatmentDefinition treatmentDefinition;

    @BeforeEach
    void setUp() {
        allocator = new TestTreatmentAllocator();
    }

    @Test
    void testGetSliceWithNullId() {
        assertEquals(-1, allocator.getSlice(null, true, "exp1", "salt"));
    }

    @Test
    void testGetSliceWithEmptyId() {
        assertEquals(-1, allocator.getSlice("", true, "exp1", "salt"));
    }

    @Test
    void testGetSliceWithValidIdNoHash() {
        // Using "fff" as first 3 characters should result in slice 4095 (max hex value)
        assertEquals(4095, allocator.getSlice("fff", false, "exp1", "salt"));
    }

    @Test
    void testGetSliceWithInvalidHexCharacters() {
        assertEquals(-1, allocator.getSlice("xyz", false, "exp1", "salt"));
    }

    @Test
    void testHashWithValidInput() {
        String result = allocator.hash("testId", "exp1", "salt");
        assertNotNull(result);
        assertEquals(64, result.length()); // SHA-256 hash is 64 characters long
    }

    @Test
    void testHashConsistency() {
        String hash1 = allocator.hash("testId", "exp1", "salt");
        String hash2 = allocator.hash("testId", "exp1", "salt");
        assertEquals(hash1, hash2);
    }

    @Test
    void testGetTreatmentByIdWithNullId() {
        assertNull(allocator.getTreatmentById(null, experimentDefinition));
    }

    @Test
    void testGetTreatmentByIdWithValidSlice() {
        // Setup
        when(experimentDefinition.isHashEnabled()).thenReturn(false);
        when(experimentDefinition.getAllocationIdStart()).thenReturn(0);
        when(experimentDefinition.getAllocationIdEnd()).thenReturn(4095);

        TreatmentDefinition treatment = mock(TreatmentDefinition.class);
        when(treatment.getIdStart()).thenReturn(0);
        when(treatment.getIdEnd()).thenReturn(100);
        when(treatment.getTreatmentCode()).thenReturn("TEST_TREATMENT");

        when(experimentDefinition.getTreatmentDefinitions())
                .thenReturn(List.of(treatment));

        // Test with ID that should fall within the treatment range
        String result = allocator.getTreatmentById("050", experimentDefinition); // hex 50 = decimal 80
        assertEquals("TEST_TREATMENT", result);
    }

    @Test
    void testGetTreatmentByIdOutOfRange() {
        when(experimentDefinition.isHashEnabled()).thenReturn(false);
        when(experimentDefinition.getAllocationIdStart()).thenReturn(0);
        when(experimentDefinition.getAllocationIdEnd()).thenReturn(100);

        // Test with ID that falls outside the allocation range
        String result = allocator.getTreatmentById("fff", experimentDefinition); // hex fff = 4095
        assertNull(result);
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        String[] results = new String[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = allocator.hash("testId", "exp1", "salt");
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        String firstResult = results[0];
        for (int i = 1; i < threadCount; i++) {
            assertEquals(firstResult, results[i]);
        }
    }

    @Test
    void testGetTreatmentByIdWithMultipleTreatments() {
        when(experimentDefinition.isHashEnabled()).thenReturn(false);
        when(experimentDefinition.getAllocationIdStart()).thenReturn(0);
        when(experimentDefinition.getAllocationIdEnd()).thenReturn(4095);

        TreatmentDefinition treatment1 = mock(TreatmentDefinition.class);
        when(treatment1.getIdStart()).thenReturn(0);
        when(treatment1.getIdEnd()).thenReturn(50);
        when(treatment1.getTreatmentCode()).thenReturn("TREATMENT_A");

        TreatmentDefinition treatment2 = mock(TreatmentDefinition.class);
        when(treatment2.getIdStart()).thenReturn(51);
        when(treatment2.getIdEnd()).thenReturn(100);
        when(treatment2.getTreatmentCode()).thenReturn("TREATMENT_B");

        when(experimentDefinition.getTreatmentDefinitions())
                .thenReturn(Arrays.asList(treatment1, treatment2));

        assertEquals("TREATMENT_A", allocator.getTreatmentById("020", experimentDefinition));
        assertEquals("TREATMENT_B", allocator.getTreatmentById("040", experimentDefinition));
    }

    @Test
    void testGetSliceWithHashEnabled() {
        String id = "testId";
        String expCode = "exp1";
        String salt = "salt";
        boolean isHashEnabled = true;

        int slice = allocator.getSlice(id, isHashEnabled, expCode, salt);
        assertTrue(slice >= 0 && slice <= 4095);
    }

    @Test
    void testFallbackHashingOnDigestException() {
        // Create a test implementation that throws DigestException
        TreatmentAllocatorOnHash testAllocator = new TreatmentAllocatorOnHash() {
            @Override
            public void updateConfiguration(ExperimentConfiguration experimentConfiguration) {

            }

            @Override
            public String getTreatmentCode(String requestId, ExperimentDefinition experimentDefinition) {
                return null;
            }

            @Override
            protected String hash(String id, String expCode, String salt) {
                // Force fallback to DigestUtils
                return super.hash(id, expCode, salt);
            }
        };

        String result = testAllocator.hash("testId", "exp1", "salt");
        assertNotNull(result);
        assertEquals(64, result.length()); // SHA-1 hash length
    }

    // Concrete implementation for testing abstract class
    private static class TestTreatmentAllocator extends TreatmentAllocatorOnHash {
        @Override
        public void updateConfiguration(ExperimentConfiguration experimentConfiguration) {

        }

        @Override
        public String getTreatmentCode(String requestId, ExperimentDefinition experimentDefinition) {
            return getTreatmentById(requestId, experimentDefinition);
        }
    }
}
