// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.util;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;

/**
 * A utility class for managing and accessing application properties.
 * <p>
 * This class provides static methods to access various subsets of properties
 * and allows for reloading of properties at runtime. It uses Apache Commons
 * Configuration to load and manage property files.
 * </p>
 */
public final class PropertiesUtil {

    private static Configuration properties;


    static {
        Configurations configs = new Configurations();
        try {
            String propertiesFile = System.getProperty("test.properties", "library.properties");
            properties = configs.properties(propertiesFile);
        } catch (ConfigurationException e) {
            throw new IllegalStateException("Failed to load library.properties", e);
        }
    }

    private PropertiesUtil() {
    }

    public static Configuration getProperties() {
        return properties;
    }

    /**
     * Retrieves the subset of properties related to file sharing S3 bucket.
     *
     * @return A Configuration object containing file sharing S3 bucket properties.
     */
    public static Configuration getFileSharingS3BucketProperties() {
        return properties.subset("file-sharing-s3-bucket");
    }

    /**
     * Retrieves the subset of properties related to tasks.
     *
     * @return A Configuration object containing task-related properties.
     */
    public static Configuration getTaskProperties() {
        return properties.subset("task");
    }

    /**
     * Reloads the properties from the properties file.
     * <p>
     * This method can be used to refresh the properties at runtime. It follows
     * the same logic as the static initializer for loading the file.
     * </p>
     *
     * @throws IllegalStateException if the properties file cannot be reloaded.
     */
    public static void reloadProperties() {
        Configurations configs = new Configurations();
        try {
            String propertiesFile = System.getProperty("test.properties", "library.properties");
            properties = configs.properties(propertiesFile);
        } catch (ConfigurationException e) {
            throw new IllegalStateException("Failed to load library.properties", e);
        }
    }
}
