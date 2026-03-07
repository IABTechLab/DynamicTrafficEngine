// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.experiment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EfficientHexEncoderTest {
    private EfficientHexEncoder hexEncoder;

    @BeforeEach
    void setUp() {
        hexEncoder = new EfficientHexEncoder();
    }

    @Test
    void testGetBufferForStringLength() {
        // Test various lengths
        assertEquals(2, EfficientHexEncoder.getBufferForStringLength(1).length);
        assertEquals(10, EfficientHexEncoder.getBufferForStringLength(5).length);
        assertEquals(32, EfficientHexEncoder.getBufferForStringLength(16).length);
        assertEquals(0, EfficientHexEncoder.getBufferForStringLength(0).length);
    }

    @Test
    void testEncodeToHexLowerCase() {
        byte[] input = new byte[]{(byte) 0xFF, (byte) 0x00, (byte) 0xAB, (byte) 0xCD};
        char[] workBuffer = EfficientHexEncoder.getBufferForStringLength(input.length);

        String result = hexEncoder.encodeToHex(input, workBuffer);
        assertEquals("ff00abcd", result);
    }

    @Test
    void testEncodeToHexUpperCase() {
        byte[] input = new byte[]{(byte) 0xFF, (byte) 0x00, (byte) 0xAB, (byte) 0xCD};
        char[] workBuffer = EfficientHexEncoder.getBufferForStringLength(input.length);

        String result = hexEncoder.encodeToHex(input, workBuffer, false);
        assertEquals("FF00ABCD", result);
    }

    @Test
    void testEncodeEmptyArray() {
        byte[] input = new byte[0];
        char[] workBuffer = EfficientHexEncoder.getBufferForStringLength(0);

        String result = hexEncoder.encodeToHex(input, workBuffer);
        assertEquals("", result);
    }

    @Test
    void testEncodeSingleByte() {
        byte[] input = new byte[]{(byte) 0x0A};
        char[] workBuffer = EfficientHexEncoder.getBufferForStringLength(1);

        String result = hexEncoder.encodeToHex(input, workBuffer);
        assertEquals("0a", result);

        result = hexEncoder.encodeToHex(input, workBuffer, false);
        assertEquals("0A", result);
    }

    @Test
    void testEncodeAllPossibleBytes() {
        byte[] input = new byte[256];
        for (int i = 0; i < 256; i++) {
            input[i] = (byte) i;
        }
        char[] workBuffer = EfficientHexEncoder.getBufferForStringLength(input.length);

        String result = hexEncoder.encodeToHex(input, workBuffer);
        // Verify first few bytes
        assertTrue(result.startsWith("000102030405"));
        // Verify last few bytes
        assertTrue(result.endsWith("fafbfcfdfeff"));
        assertEquals(512, result.length());
    }

    @Test
    void testInvalidBufferSize() {
        byte[] input = new byte[]{(byte) 0xFF, (byte) 0x00};
        char[] workBuffer = new char[3]; // Invalid buffer size (should be 4)

        assertThrows(IllegalArgumentException.class, () -> {
            hexEncoder.encodeToHex(input, workBuffer);
        });
    }

    @Test
    void testNegativeBytes() {
        byte[] input = new byte[]{(byte) -1, (byte) -128};
        char[] workBuffer = EfficientHexEncoder.getBufferForStringLength(input.length);

        String result = hexEncoder.encodeToHex(input, workBuffer);
        assertEquals("ff80", result);
    }

    @Test
    void testNullInput() {
        char[] workBuffer = EfficientHexEncoder.getBufferForStringLength(1);

        assertThrows(NullPointerException.class, () -> {
            hexEncoder.encodeToHex(null, workBuffer);
        });
    }

    @Test
    void testNullWorkBuffer() {
        byte[] input = new byte[]{(byte) 0xFF};

        assertThrows(NullPointerException.class, () -> {
            hexEncoder.encodeToHex(input, null);
        });
    }

    @Test
    void testConsistency() {
        byte[] input = new byte[]{(byte) 0xAB, (byte) 0xCD};
        char[] workBuffer = EfficientHexEncoder.getBufferForStringLength(input.length);

        // Test that multiple encodings of the same input produce the same result
        String result1 = hexEncoder.encodeToHex(input, workBuffer);
        String result2 = hexEncoder.encodeToHex(input, workBuffer);
        assertEquals(result1, result2);
    }

    @Test
    void testLargeInput() {
        byte[] input = new byte[1000];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i % 256);
        }
        char[] workBuffer = EfficientHexEncoder.getBufferForStringLength(input.length);

        String result = hexEncoder.encodeToHex(input, workBuffer);
        assertEquals(2000, result.length());
    }
}
