// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.experiment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.DigestException;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReusableDigestTest {
    private ReusableDigest sha256Digest;

    @BeforeEach
    void setUp() {
        sha256Digest = ReusableDigest.sha256();
    }

    @Test
    void testSha256Creation() {
        assertNotNull(sha256Digest);
        assertEquals(ReusableDigest.SHA256_OUTPUT_LENGTH, 32);
    }

    @Test
    void testGetDigestWithValidAlgorithm() {
        MessageDigest digest = ReusableDigest.getDigest(ReusableDigest.SHA256);
        assertNotNull(digest);
        assertEquals(ReusableDigest.SHA256, digest.getAlgorithm());
    }

    @Test
    void testGetDigestWithInvalidAlgorithm() {
        assertThrows(RuntimeException.class, () -> {
            ReusableDigest.getDigest("INVALID_ALGORITHM");
        });
    }

    @Test
    void testSha1HashToHexString() throws DigestException {
        String input = "Hello, World!";
        String hash = sha256Digest.hashToHexString(input.getBytes(StandardCharsets.UTF_8));

        // Known SHA-1 hash for "Hello, World!"
        String expectedHash = "dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f";
        assertEquals(expectedHash, hash);
    }

    @Test
    void testEmptyInputHash() throws DigestException {
        String sha1Hash = sha256Digest.hashToHexString(new byte[0]);

        // Known hashes for empty input
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha1Hash);
    }

    @Test
    void testMultipleHashesSha1() throws DigestException {
        String input1 = "First input";
        String input2 = "Second input";

        String hash1 = sha256Digest.hashToHexString(input1.getBytes(StandardCharsets.UTF_8));
        String hash2 = sha256Digest.hashToHexString(input2.getBytes(StandardCharsets.UTF_8));

        // Verify that the same input produces the same hash
        assertEquals(hash1, sha256Digest.hashToHexString(input1.getBytes(StandardCharsets.UTF_8)));
        assertEquals(hash2, sha256Digest.hashToHexString(input2.getBytes(StandardCharsets.UTF_8)));

        // Verify that different inputs produce different hashes
        assertNotEquals(hash1, hash2);
    }

    @Test
    void testLargeInput() throws DigestException {
        // Create a large input
        StringBuilder largeInput = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeInput.append("test");
        }

        // Verify that large input can be processed without exceptions
        assertDoesNotThrow(() -> {
            sha256Digest.hashToHexString(largeInput.toString().getBytes(StandardCharsets.UTF_8));
        });
    }
}
