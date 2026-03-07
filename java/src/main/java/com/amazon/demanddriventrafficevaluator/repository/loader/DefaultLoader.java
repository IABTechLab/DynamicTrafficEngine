// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.loader;

import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStream;
import java.util.Optional;

import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_IDENTIFIER_FILE_IDENTIFIER;

/**
 * An abstract base class for implementing loaders with file refresh capabilities.
 * <p>
 * This class provides a framework for loading data from S3 and determining whether
 * the data should be refreshed based on file identifiers (ETags). It uses a cache
 * to store file identifiers for comparison.
 * </p>
 *
 * @param <T> The type of LoaderInput used by this loader, must extend LoaderInput.
 */
@Log4j2
public abstract class DefaultLoader<T extends LoaderInput> implements Loader<T> {

    public Dao<String, String> fileIdentifierCacheDao;

    /**
     * Loads data based on the provided input.
     * <p>
     * This method should be implemented by subclasses to define the specific
     * loading behavior.
     * </p>
     *
     * @param input The input containing necessary information for loading.
     * @return true if data was successfully loaded, false otherwise.
     */
    public abstract boolean load(T input);

    /**
     * Generates the S3 object key based on the provided input.
     * <p>
     * This method should be implemented by subclasses to define how the S3 object
     * key is constructed for a given input.
     * </p>
     *
     * @param input The input used to generate the S3 object key.
     * @return The S3 object key as a String.
     */

    public abstract String getS3ObjectKey(T input);

    /**
     * Determines whether the data should be refreshed based on the file identifier (ETag).
     * <p>
     * This method compares the ETag of the S3 object with the cached file identifier.
     * If they differ or if there's no cached identifier, it indicates that a refresh is needed.
     * If a refresh is needed, then the cache entry is updated to the newest file identifier.
     * </p>
     *
     * @param fileIdentifierCacheKey The key used to cache the file identifier.
     * @param inputStream            The input stream of the S3 object, expected to be a ResponseInputStream.
     * @return true if the data should be refreshed, false otherwise.
     */

    public boolean shouldRefresh(String fileIdentifierCacheKey, InputStream inputStream) {
        try {
            ResponseInputStream<GetObjectResponse> responseInputStream = (ResponseInputStream<GetObjectResponse>) inputStream;
            String fileIdentifier = responseInputStream.response().eTag();
            Optional<String> fileIdentifierInCache = fileIdentifierCacheDao.get(
                    CACHE_IDENTIFIER_FILE_IDENTIFIER,
                    fileIdentifierCacheKey);
            if (fileIdentifierInCache.isPresent() && fileIdentifierInCache.get().equals(fileIdentifier)) {
                return false;
            } else {
                fileIdentifierCacheDao.put(CACHE_IDENTIFIER_FILE_IDENTIFIER, fileIdentifierCacheKey, fileIdentifier);
                return true;
            }
        } catch (Exception e) {
            log.error("Fail to get fileIdentifier either from GetObjectResponse or the cache, so that not refresh", e);
            return false;
        }
    }
}
