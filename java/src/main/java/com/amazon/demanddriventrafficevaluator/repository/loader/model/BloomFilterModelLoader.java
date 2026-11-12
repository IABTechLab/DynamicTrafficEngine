// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.loader.model;

import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import com.amazon.demanddriventrafficevaluator.repository.entity.S3PathMode;
import com.amazon.demanddriventrafficevaluator.repository.loader.DefaultLoader;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.sso.model.ResourceNotFoundException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * A loader class for loading bloom filter models from S3 and storing them in memory.
 * <p>
 * This class extends DefaultLoader and specializes in loading serialized Guava BloomFilter
 * instances from S3, deserializing them, and storing them via the BloomFilterDao.
 * It reuses the existing ETag-based shouldRefresh mechanism to avoid unnecessary reloads.
 * </p>
 */
@Log4j2
public class BloomFilterModelLoader extends DefaultLoader<ModelResultLoaderInput> {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
            .withLocale(Locale.ROOT)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("HH")
            .withLocale(Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private final Dao<String, InputStream> fileDao;
    private final Dao<String, BloomFilter<String>> bloomFilterDao;

    public BloomFilterModelLoader(
            Dao<String, String> fileIdentifierCacheDao,
            Dao<String, BloomFilter<String>> bloomFilterDao,
            Dao<String, InputStream> fileDao
    ) {
        super.fileIdentifierCacheDao = fileIdentifierCacheDao;
        this.bloomFilterDao = bloomFilterDao;
        this.fileDao = fileDao;
    }

    /**
     * Loads a bloom filter model from S3 and stores it in memory.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Retrieves the S3 object key for the bloom filter file</li>
     *   <li>Fetches the file from S3</li>
     *   <li>Checks if the model needs to be refreshed based on ETag</li>
     *   <li>If refresh is needed, deserializes the bloom filter and stores it via bloomFilterDao</li>
     * </ol>
     * </p>
     *
     * @param input The input containing necessary information for loading the bloom filter model.
     * @return true if a new bloom filter was loaded and stored, false if no refresh was needed
     *         or the file was not found or deserialization failed.
     */
    @Override
    public boolean load(ModelResultLoaderInput input) {
        String modelIdentifier = input.getModelIdentifier();
        String fileKey = getS3ObjectKey(input);
        boolean refreshed = false;
        boolean loaded = false;

        try (InputStream inputStream = fileDao.get(input.getS3Bucket(), fileKey)
                .orElseThrow(() -> ResourceNotFoundException.builder()
                        .message("Bloom filter model file not found: " + fileKey).build())) {
            if (!shouldRefresh(modelIdentifier, inputStream)) {
                log.debug("BloomFilterModelLoader is not refreshed for model {}", modelIdentifier);
                return false;
            }
            refreshed = true;

            @SuppressWarnings("UnstableApiUsage")
            BloomFilter<String> bloomFilter = BloomFilter.readFrom(
                    inputStream, Funnels.stringFunnel(StandardCharsets.UTF_8));
            bloomFilterDao.put(modelIdentifier, modelIdentifier, bloomFilter);
            loaded = true;
        } catch (ResourceNotFoundException e) {
            log.warn("Bloom filter model file not found for model {}: {}", modelIdentifier, fileKey);
            return false;
        } catch (Exception e) {
            log.error("Failed to deserialize bloom filter model for model {}: {}", modelIdentifier, fileKey, e);
            return false;
        } finally {
            if (refreshed && !loaded) {
                resetFileIdentifier(modelIdentifier);
            }
        }
        log.info("Loaded bloom filter model for model {}", modelIdentifier);
        return true;
    }

    /**
     * Generates the S3 object key for the bloom filter model file.
     * <p>
     * When the S3PathMode is DYNAMIC, the key is generated based on the current date and hour
     * in UTC, along with vendor information and the S3 object key provided in the input.
     * When the S3PathMode is STATIC, the literal s3ObjectKey value is returned unchanged.
     * The caller is responsible for including any path prefix (e.g., {@code models/}) in the key.
     * </p>
     *
     * @param input The input containing necessary information for generating the S3 key.
     * @return The S3 object key as a String.
     */
    @Override
    public String getS3ObjectKey(ModelResultLoaderInput input) {
        if (input.getS3PathMode() == S3PathMode.STATIC) {
            return input.getVendor() + "/models/" + input.getS3ObjectKey() + ".bloom";
        }
        return buildDynamicS3ObjectKey(input);
    }

    @VisibleForTesting
    String buildDynamicS3ObjectKey(ModelResultLoaderInput input) {
        ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);
        return new StringBuilder(input.getVendor())
                .append('/')
                .append(now.format(DATE_FORMATTER))
                .append('/')
                .append(now.format(HOUR_FORMATTER))
                .append('/')
                .append(input.getS3ObjectKey())
                .toString();
    }
}
