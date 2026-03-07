// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.experiment;

import com.google.common.base.Preconditions;

/**
 * This class is a more efficient implementation of {@link org.apache.commons.codec.binary.Hex#encodeHex(byte[])}. It forces you to pass
 * in a pre-allocated char[] buffer to facilitate the conversion from byte[] to a hex String.
 */
public class EfficientHexEncoder {
    private static final char[] DIGITS_LOWER = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    private static final char[] DIGITS_UPPER = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    /**
     * Creates and returns a char[] working buffer for a byte array of indicated length
     *
     * @param length length of byte array this buffer is for
     * @return char[] working buffer
     */
    public static char[] getBufferForStringLength(int length) {
        return new char[length * 2];
    }

    protected static String encodeHex(byte[] data, char[] workBuffer, char[] toDigits) {
        Preconditions.checkArgument(data.length * 2 == workBuffer.length);

        for (int bufferIdx = 0, i = 0; i < data.length; ++i) {
            workBuffer[bufferIdx++] = toDigits[(240 & data[i]) >>> 4];
            workBuffer[bufferIdx++] = toDigits[15 & data[i]];
        }

        return new String(workBuffer);
    }

    /**
     * Encode input byte array to hex string in lower case.
     *
     * @param input      input data
     * @param workBuffer char[] working buffer
     * @return hex string in lower case
     */
    public String encodeToHex(byte[] input, char[] workBuffer) {
        return encodeToHex(input, workBuffer, true);
    }

    /**
     * Encode input byte array to hex string in the casing of your choice.
     *
     * @param input       input data
     * @param workBuffer  char[] working buffer
     * @param toLowerCase if true, hex string output will be in lower case, if false, it will be in upper case
     * @return hex string
     */
    public String encodeToHex(byte[] input, char[] workBuffer, boolean toLowerCase) {
        return encodeHex(input, workBuffer, toLowerCase ? DIGITS_LOWER : DIGITS_UPPER);
    }
}
