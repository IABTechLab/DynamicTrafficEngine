// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.loader.configuration;

import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao;
import com.amazon.demanddriventrafficevaluator.repository.entity.Configuration;
import com.amazon.demanddriventrafficevaluator.repository.loader.DefaultLoader;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import lombok.extern.log4j.Log4j2;

import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_IDENTIFIER_CONFIGURATION;

/**
 * A generic loader class for loading and caching configurations from S3.
 * <p>
 * This class extends DefaultLoader and provides functionality to load configuration
 * files from S3, deserialize them into configuration objects, and cache these objects.
 * It supports any configuration type that extends the Configuration interface.
 * </p>
 *
 * @param <T> The type of Configuration this loader handles, must extend Configuration.
 */
@Log4j2
public class DefaultConfigurationLoader<T extends Configuration> extends DefaultLoader<ConfigurationLoaderInput> {

    private final Class<T> type;
    private final String configurationCacheKey;
    private final Dao<String, InputStream> fileDao;
    private final Dao<String, T> configurationCacheDao;
    private final ObjectMapper mapper;

    public DefaultConfigurationLoader(
            Dao<String, String> fileIdentifierCacheDao,
            Dao<String, InputStream> fileDao,
            LocalCacheDao<String, T> configurationCacheDao,
            String configurationCacheKey,
            Class<T> type,
            ObjectMapper mapper
    ) {
        super.fileIdentifierCacheDao = fileIdentifierCacheDao;
        this.fileDao = fileDao;
        this.configurationCacheDao = configurationCacheDao;
        this.configurationCacheKey = configurationCacheKey;
        this.type = type;
        this.mapper = mapper;
    }

    /**
     * Loads the configuration from S3 to the cache.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Retrieves the S3 object key for the configuration file</li>
     *   <li>Fetches the configuration file from S3</li>
     *   <li>Checks if the configuration needs to be refreshed</li>
     *   <li>If refresh is needed, deserializes the configuration and caches it</li>
     * </ol>
     * </p>
     *
     * @param input The input containing necessary information for loading the configuration.
     * @return true if a new configuration was loaded and cached, false if no refresh was needed.
     * @throws IllegalStateException    if the configuration cannot be loaded or deserialized.
     * @throws IllegalArgumentException if the S3 object cannot be accessed.
     */
    @Override
    public boolean load(ConfigurationLoaderInput input) {
        String fileKey = getS3ObjectKey(input);
        try (InputStream configurationStream = fileDao.get(input.getS3Bucket(), fileKey)
                .orElseThrow(() -> new IllegalArgumentException("Cannot Access to the Data with key: " + fileKey))) {
            if (!shouldRefresh(configurationCacheKey, configurationStream)) {
                log.debug("Configuration is not refreshed");
                return false;
            }
            T configuration = mapper.readValue(configurationStream, type);
            configurationCacheDao.put(CACHE_IDENTIFIER_CONFIGURATION, configurationCacheKey, configuration);
            return true;
        } catch (IOException e) {
            log.error("Cannot deserialize the Json to the POJO or due to I/O for file {}", fileKey, e);
            return false;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load Configurations into the cache", e);
        }
    }

    /**
     * Generates the S3 object key for the configuration file.
     *
     * @param input The input containing necessary information for generating the S3 key.
     * @return The S3 object key as a String.
     */
    @Override
    public String getS3ObjectKey(ConfigurationLoaderInput input) {
        return input.getVendor() + "/configuration/" + input.getConfigurationType() + "/config.json";
    }
}
