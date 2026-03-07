// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.util;

import org.apache.commons.configuration2.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class PropertiesUtilTest {
    private static File tempFile;

    @BeforeAll
    static void setUp() throws IOException {
        // Create a temporary properties file
        tempFile = File.createTempFile("test-library", ".properties");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile, StandardCharsets.UTF_8)) {
            writer.write("file-sharing-s3-bucket.key1=value1\n");
            writer.write("file-sharing-s3-bucket.key2=value2\n");
            writer.write("task.key1=taskvalue1\n");
            writer.write("task.key2=taskvalue2\n");
            writer.write("other.key=othervalue\n");
        }

        // Set the system property to use our test file
        System.setProperty("test.properties", tempFile.getAbsolutePath());
        PropertiesUtil.reloadProperties();
    }

    @AfterAll
    static void tearDown() {
        // Remove the system property
        System.clearProperty("test.properties");
    }

    @Test
    void testGetProperties() {
        Configuration config = PropertiesUtil.getProperties();
        assertNotNull(config);
        assertEquals("value1", config.getString("file-sharing-s3-bucket.key1"));
        assertEquals("taskvalue1", config.getString("task.key1"));
        assertEquals("othervalue", config.getString("other.key"));
    }

    @Test
    void testGetFileSharingS3BucketProperties() {
        Configuration config = PropertiesUtil.getFileSharingS3BucketProperties();
        assertNotNull(config);
        assertEquals("value1", config.getString("key1"));
        assertEquals("value2", config.getString("key2"));
        assertNull(config.getString("task.key1"));
    }

    @Test
    void testGetTaskProperties() {
        Configuration config = PropertiesUtil.getTaskProperties();
        assertNotNull(config);
        assertEquals("taskvalue1", config.getString("key1"));
        assertEquals("taskvalue2", config.getString("key2"));
        assertNull(config.getString("file-sharing-s3-bucket.key1"));
    }

    @Test
    void testPrivateConstructor() {
        assertThrows(IllegalAccessException.class, () -> {
            PropertiesUtil.class.getDeclaredConstructor().newInstance();
        });
    }
}
