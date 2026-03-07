// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.experiment;

import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ReusableDigest {

    static final int SHA256_OUTPUT_LENGTH = 32;

    static final String SHA256 = "SHA-256";

    private final MessageDigest messageDigest;
    private final byte[] outputBytes;
    private final EfficientHexEncoder hexEncoder;
    private final char[] hexWorkingBuffer;

    private ReusableDigest(MessageDigest messageDigest, int outputLength) {
        this.messageDigest = messageDigest;

        outputBytes = new byte[outputLength];

        hexEncoder = new EfficientHexEncoder();
        hexWorkingBuffer = EfficientHexEncoder.getBufferForStringLength(outputLength);
    }

    /**
     * Returns a drop in replacement for SHA-256 digest
     *
     * @return {@link ReusableDigest}
     */
    public static ReusableDigest sha256() {
        return new ReusableDigest(getDigest(SHA256), SHA256_OUTPUT_LENGTH);
    }

    static MessageDigest getDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Hashes the input bytes into a hex string
     *
     * @param input input bytes
     * @return hash as hex string
     * @throws DigestException thrown on hashing exception
     */
    public String hashToHexString(byte[] input) throws DigestException {
        messageDigest.update(input);
        // the digest call finalizes the output and resets the internal state for new data
        messageDigest.digest(outputBytes, 0, outputBytes.length);
        return hexEncoder.encodeToHex(outputBytes, hexWorkingBuffer);
    }
}
