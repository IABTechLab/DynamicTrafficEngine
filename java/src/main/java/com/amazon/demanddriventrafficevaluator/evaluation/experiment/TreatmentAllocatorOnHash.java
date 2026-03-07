// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.experiment;

import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentDefinition;
import com.amazon.demanddriventrafficevaluator.repository.entity.TreatmentDefinition;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.codec.digest.DigestUtils;

import java.security.DigestException;

/**
 * An abstract base class for hash-based treatment allocation strategies.
 * <p>
 * This class provides a framework for allocating treatments based on hashing of id.
 * It includes methods for generating hash-based slices and determining treatments based on
 * these slices.
 * </p>
 */
@Log4j2
public abstract class TreatmentAllocatorOnHash implements TreatmentAllocator {

    private final ThreadLocal<ReusableDigest> sha256ThreadLocal = ThreadLocal.withInitial(ReusableDigest::sha256);

    /**
     * Abstract method to be implemented by subclasses to determine the treatment code.
     *
     * @param id                   The unique identifier for the request.
     * @param experimentDefinition The definition of the experiment.
     * @return The allocated treatment code.
     */
    public abstract String getTreatmentCode(String id, ExperimentDefinition experimentDefinition);

    /**
     * Calculates a slice number based on the provided ID and experiment parameters.
     *
     * @param id            The identifier to be sliced.
     * @param isHashEnabled Whether to apply hashing to the ID.
     * @param expCode       The experiment code.
     * @param salt          A salt value for hashing.
     * @return An integer representing the slice, or -1 if an error occurs.
     */
    protected int getSlice(String id, boolean isHashEnabled, String expCode, String salt) {
        if (id == null) {
            return -1;
        } else if (id.length() == 0) {
            return -1;
        } else {
            if (isHashEnabled) {
                id = hash(id, expCode, salt);
            }
            int slice;
            try {
                // id has to be a string with at least 3 hexadecimal characters 
                slice = Integer.parseInt(id.substring(0, 3), 16);
            } catch (Exception e) {
                log.error("Caught exception parsing int. Returning -1 for the value of slice.", e);
                return -1;
            }
            return slice;
        }
    }

    /**
     * Generates a hash of the combined ID, experiment code, and salt.
     *
     * @param id      The identifier to be hashed.
     * @param expCode The experiment code.
     * @param salt    A salt value for hashing.
     * @return A hexadecimal string representation of the hash.
     */
    protected String hash(String id, String expCode, String salt) {
        ReusableDigest reusableDigest = sha256ThreadLocal.get();
        String combinedId = id + expCode + salt;
        try {
            return reusableDigest.hashToHexString(StringUtils.getBytesUtf8(combinedId));
        } catch (DigestException e) {
            log.error("Caught exception hashing id", e);
            return DigestUtils.sha1Hex(combinedId);
        }
    }

    /**
     * Determines the treatment for a given ID based on the experiment definition.
     *
     * @param id                   The identifier used to determine the treatment.
     * @param experimentDefinition The definition of the experiment.
     * @return The allocated treatment code, or null if the ID is out of the experiment's range.
     */
    protected String getTreatmentById(String id, ExperimentDefinition experimentDefinition) {
        if (id == null) {
            return null;
        }

        int sliceNum = getSlice(id, experimentDefinition.isHashEnabled(), experimentDefinition.getName(),
                experimentDefinition.getSalt());
        if (sliceNum < 0) {
            // something is wrong
            return null;
        }

        log.debug("sliceNum: {}", sliceNum);

        int sliceStart = experimentDefinition.getAllocationIdStart();
        int sliceEnd = experimentDefinition.getAllocationIdEnd();

        // [start, end]
        if ((sliceNum < sliceStart) || (sliceNum > sliceEnd)) {
            // this deviceId is out of the defined segment
            return null;
        }

        // now we are sure that this id falls in the experiment segment, assign treatment by sub-slice
        String result = null;
        for (TreatmentDefinition t : experimentDefinition.getTreatmentDefinitions()) {
            if (sliceNum >= t.getIdStart() && sliceNum <= t.getIdEnd()) {
                result = t.getTreatmentCode();
                break;
            }
        }
        return result;
    }
}
