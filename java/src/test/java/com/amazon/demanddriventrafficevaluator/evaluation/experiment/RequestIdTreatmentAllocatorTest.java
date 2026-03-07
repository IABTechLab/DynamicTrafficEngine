// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.experiment;

import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentDefinition;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestIdTreatmentAllocatorTest {

    @Spy
    private RequestIdTreatmentAllocator allocator;

    @Mock
    private ExperimentDefinition experimentDefinition;

    @Test
    void testUpdateConfiguration() {
        ExperimentConfiguration config = mock(ExperimentConfiguration.class);
        // Verify no exceptions are thrown
        assertThrows(UnsupportedOperationException.class, () -> allocator.updateConfiguration(config));
    }

    @Test
    void testGetTreatmentCodeWithNullRequestId() {
        assertNull(allocator.getTreatmentCode(null, experimentDefinition));
    }

    @Test
    void testGetTreatmentCodeWithValidRequestId() {
        String requestId = "test-request-id";
        String expectedHash = "bed4b732d4ec2c1a7049c7e6599569dc09394bb77900c5bb4c6d4e10f5663917"; // Known SHA256 hash for "test-request-id"

        // Mock the behavior of getTreatmentById
        when(allocator.getTreatmentById(expectedHash, experimentDefinition))
                .thenReturn("treatment-A");

        String result = allocator.getTreatmentCode(requestId, experimentDefinition);

        assertEquals("treatment-A", result);
    }

    @Test
    void testSha256HexIdWithValidInput() {
        String input = "test-input";
        String expectedHash = DigestUtils.sha256Hex(input);

        String result = allocator.sha256HexId(input);

        assertEquals(expectedHash, result);
    }

    @Test
    void testSha256HexIdWithEmptyString() {
        String input = "";
        String expectedHash = DigestUtils.sha256Hex(input);

        String result = allocator.sha256HexId(input);

        assertEquals(expectedHash, result);
    }

    @Test
    void testSha256HexIdWithSpecialCharacters() {
        String input = "!@#$%^&*()";
        String expectedHash = DigestUtils.sha256Hex(input);

        String result = allocator.sha256HexId(input);

        assertEquals(expectedHash, result);
    }

    @Test
    void testSha256HexIdWithUnicodeCharacters() {
        String input = "测试";
        String expectedHash = DigestUtils.sha256Hex(input);

        String result = allocator.sha256HexId(input);

        assertEquals(expectedHash, result);
    }

    @Test
    void testConsistencyOfHashing() {
        String input = "test-consistency";

        String firstHash = allocator.sha256HexId(input);
        String secondHash = allocator.sha256HexId(input);

        assertEquals(firstHash, secondHash);
    }

    @Test
    void testThreadLocalBehavior() throws InterruptedException {
        String input = "test-thread";
        String expectedHash = DigestUtils.sha256Hex(input);

        // Test in multiple threads
        Thread[] threads = new Thread[3];
        String[] results = new String[3];

        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = allocator.sha256HexId(input);
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify all results are correct
        for (String result : results) {
            assertEquals(expectedHash, result);
        }
    }

    @Test
    void testLargeInput() {
        String input = "test".repeat(10000);
        String expectedHash = DigestUtils.sha256Hex(input);

        String result = allocator.sha256HexId(input);

        assertEquals(expectedHash, result);
    }
}
