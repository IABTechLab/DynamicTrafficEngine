// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.loader.model;

import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import com.amazon.demanddriventrafficevaluator.repository.loader.DefaultLoader;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.sso.model.ResourceNotFoundException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * A loader class for loading rule-based model results from S3 and caching them.
 * <p>
 * This class extends DefaultLoader and specializes in loading model results from S3,
 * processing them line by line, and storing them in a cache. It keeps track of the
 * number of items loaded and their total size.
 * </p>
 */
@Log4j2
public class RuleBasedModelResultLoader extends DefaultLoader<ModelResultLoaderInput> {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
            .withLocale(Locale.ROOT)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("HH")
            .withLocale(Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private final Dao<String, InputStream> fileDao;
    private final Dao<String, Double> modelResultsCacheDao;

    long putItemCounter = 0;
    long putItemTotalSize = 0;

    public RuleBasedModelResultLoader(
            Dao<String, String> fileIdentifierCacheDao,
            Dao<String, Double> modelResultsCacheDao,
            Dao<String, InputStream> fileDao
    ) {
        super.fileIdentifierCacheDao = fileIdentifierCacheDao;
        this.modelResultsCacheDao = modelResultsCacheDao;
        this.fileDao = fileDao;
    }

    /**
     * Loads model results from S3 and caches them.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Retrieves the S3 object key for the model results file</li>
     *   <li>Fetches the file from S3</li>
     *   <li>Checks if the results need to be refreshed</li>
     *   <li>If refresh is needed, reads the file line by line and caches each result</li>
     *   <li>Keeps track of the number of items loaded and their total size</li>
     * </ol>
     * </p>
     *
     * @param input The input containing necessary information for loading the model results.
     * @return true if new results were loaded and cached, false if no refresh was needed or the file was not found.
     * @throws IllegalStateException if there's an error during the loading process.
     */
    @Override
    public boolean load(ModelResultLoaderInput input) {
        putItemCounter = 0L;
        putItemTotalSize = 0L;
        String modelIdentifier = input.getModelIdentifier();
        String fileKey = getS3ObjectKey(input);

        // write a function to split resultLocation into identifier and key
        try (InputStream inputStream = fileDao.get(input.getS3Bucket(), fileKey)
                .orElseThrow(() -> ResourceNotFoundException.builder().message("Model result file not found: " + fileKey).build());
             BufferedReader reader = getBufferedReader(inputStream)) {
            if (!shouldRefresh(modelIdentifier, inputStream)) {
                log.debug("RuleBasedModelResultLoader is not refreshed");
                return false;
            }

            // invalidate cache since new model is detected
            modelResultsCacheDao.clear(modelIdentifier);

            String modelResult;
            while ((modelResult = reader.readLine()) != null) {
                modelResultsCacheDao.put(modelIdentifier, modelResult, input.getModelType().getCacheValue());
                putItemCounter++;
                putItemTotalSize += modelResult.length();
            }
        } catch (ResourceNotFoundException e) {
            log.warn(e);
            return false;
        } catch (IOException e) {
            log.error("Failed to read model result file due to I/O for file {}", fileKey, e);
            return false;
        } catch (Exception e) {
            throw new IllegalStateException("Fail to load model result file due to unknown issue: " + fileKey, e);
        }
        log.info("Loaded {} model results, total size: {}", putItemCounter, putItemTotalSize);
        return true;
    }

    /**
     * Generates the S3 object key for the model results file.
     * <p>
     * The key is generated based on the current date and hour in UTC, along with
     * vendor information and the S3 object key provided in the input.
     * </p>
     *
     * @param input The input containing necessary information for generating the S3 key.
     * @return The S3 object key as a String.
     */
    @Override
    public String getS3ObjectKey(ModelResultLoaderInput input) {
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

    @VisibleForTesting
    protected BufferedReader getBufferedReader(InputStream inputStream) {
        return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }
}
