// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.dao;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.io.InputStream;
import java.util.Optional;

/**
 * A Data Access Object (DAO) implementation for interacting with Amazon S3 objects.
 * <p>
 * This class provides methods to retrieve objects from Amazon S3 as input streams.
 * It implements the Dao interface, specifying String as the key type (for S3 object keys)
 * and InputStream as the value type (for S3 object content).
 * </p>
 * <p>
 * Note: This implementation only supports retrieving objects from S3. The put operation
 * is not supported and will throw an UnsupportedOperationException if called.
 * </p>
 */
@Log4j2
public class S3ObjectDao implements Dao<String, InputStream> {

    private static final long MAX_S3_OBJECT_SIZE_BYTES = 30L * 1024 * 1024; // 30 MB

    private final S3Client s3Client;

    public S3ObjectDao(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Retrieves an object from Amazon S3 as an InputStream.
     * <p>
     * This method attempts to fetch the object specified by the bucket name and key from S3.
     * If the object exceeds {@link #MAX_S3_OBJECT_SIZE_BYTES} (30 MB), it logs a warning
     * and returns an empty Optional without reading the content.
     * If successful, it returns the object's content as an InputStream wrapped in an Optional.
     * If an error occurs during the retrieval process, it logs the error and returns an empty Optional.
     * </p>
     *
     * @param bucketName The name of the S3 bucket containing the object.
     * @param key        The key of the object in the S3 bucket.
     * @return An Optional containing the InputStream of the S3 object if found and within size limits,
     *         or an empty Optional if not found, too large, or if an error occurs.
     */
    @Override
    public Optional<InputStream> get(String bucketName, String key) {
        return getIfChanged(bucketName, key, null).getValue();
    }

    /**
     * Retrieves an object from Amazon S3 unless its ETag matches {@code knownVersion}.
     * <p>
     * The ETag comes from the HeadObject request that already checks the size limit, so an
     * unchanged object costs that one request and no download.
     * </p>
     *
     * @param bucketName   The name of the S3 bucket containing the object.
     * @param key          The key of the object in the S3 bucket.
     * @param knownVersion The ETag of the copy the caller already has, or null if none.
     * @return The object's content, not modified if its ETag equals {@code knownVersion}, or not found
     *         if it is missing, too large, or an error occurs.
     */
    @Override
    public ConditionalGetResult<InputStream> getIfChanged(String bucketName, String key, String knownVersion) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            HeadObjectResponse headResponse = s3Client.headObject(headRequest);
            Long contentLength = headResponse.contentLength();
            if (contentLength != null && contentLength > MAX_S3_OBJECT_SIZE_BYTES) {
                log.warn("S3 object {}/{} exceeds max size: {} bytes (limit: {} bytes). Skipping download.",
                        bucketName, key, contentLength, MAX_S3_OBJECT_SIZE_BYTES);
                return ConditionalGetResult.notFound();
            }
            if (knownVersion != null && knownVersion.equals(headResponse.eTag())) {
                log.debug("S3 object {}/{} is unchanged (ETag {}). Skipping download.", bucketName, key, knownVersion);
                return ConditionalGetResult.notModified();
            }

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            return ConditionalGetResult.found(s3Client.getObject(getObjectRequest));
        } catch (Exception e) {
            log.error("Error getting object with key {} from S3", key, e);
            return ConditionalGetResult.notFound();
        }
    }

    /**
     * This operation is not supported for S3 objects in this implementation.
     *
     * @param bucketName The name of the S3 bucket.
     * @param key        The key of the object in the S3 bucket.
     * @param value      The InputStream to be stored.
     * @throws UnsupportedOperationException always, as this operation is not supported.
     */
    @Override
    public void put(String bucketName, String key, InputStream value) {
        throw new UnsupportedOperationException();
    }

    /**
     * This operation is not supported for S3 objects in this implementation.
     *
     * @param bucketName The name of the S3 bucket.
     * @throws UnsupportedOperationException always, as this operation is not supported.
     */
    @Override
    public void clear(String bucketName) {
        throw new UnsupportedOperationException();
    }
}
