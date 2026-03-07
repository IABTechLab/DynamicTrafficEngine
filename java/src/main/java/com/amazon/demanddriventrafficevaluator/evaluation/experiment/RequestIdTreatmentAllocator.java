// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.experiment;

import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentDefinition;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.codec.digest.DigestUtils;

import java.security.DigestException;

/**
 * A treatment allocator that determines treatments based on the SHA256 hash of the request ID.
 * <p>
 * This class extends TreatmentAllocatorOnHash and implements a strategy where
 * treatments are allocated based on an SHA256 hash of the request ID. It uses a
 * thread-local SHA256 digest for efficient hashing in multi-threaded environments.
 * </p>
 */
@Log4j2
public class RequestIdTreatmentAllocator extends TreatmentAllocatorOnHash {

    private final ThreadLocal<ReusableDigest> sha256ThreadLocal = ThreadLocal.withInitial(ReusableDigest::sha256);

    /**
     * Updates the configuration for the allocator.
     * <p>
     * This operation is not supported in this implementation since it is unnecessary.
     * </p>
     *
     * @param experimentConfiguration The new configuration for experiments.
     * @throws UnsupportedOperationException always, as this operation is not supported.
     */
    @Override
    public void updateConfiguration(ExperimentConfiguration experimentConfiguration) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines the treatment code for a given request ID and experiment definition.
     * <p>
     * This method hashes the request ID using SHA256 and then uses the hashed value
     * to determine the appropriate treatment based on the experiment definition.
     * </p>
     *
     * @param requestId            The unique identifier for the request.
     * @param experimentDefinition The definition of the experiment.
     * @return The allocated treatment code, or null if the request ID is null or
     * if no treatment is applicable.
     */
    @Override
    public String getTreatmentCode(String requestId, ExperimentDefinition experimentDefinition) {
        log.debug("requestId: {}", requestId);
        if (requestId == null) {
            return null;
        }
        String hashedRequestId = sha256HexId(requestId);
        log.debug("sha256(requestId): {}", hashedRequestId);
        return getTreatmentById(hashedRequestId, experimentDefinition);
    }

    /**
     * Generates an SHA256 hash of the given ID.
     * <p>
     * This method uses a thread-local SHA256 digest for efficient hashing. If an exception
     * occurs during hashing, it falls back to using Apache Commons Codec's DigestUtils.
     * </p>
     *
     * @param id The string to be hashed.
     * @return A hexadecimal string representation of the SHA256 hash of the input.
     */
    String sha256HexId(String id) {
        try {
            return sha256ThreadLocal.get().hashToHexString(StringUtils.getBytesUtf8(id));
        } catch (DigestException e) {
            log.error("Caught exception sha256 hashing id", e);
            // fallback to {@link org.apache.commons.codec.binary.Hex#encodeHex(byte[])}
            return DigestUtils.sha256Hex(id);
        }
    }
}
