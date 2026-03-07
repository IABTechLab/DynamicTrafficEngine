// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

public abstract class BaseTestCase {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    protected abstract Class<?> getResourceClass();

    public String readJsonResourceAsString(String resourcePath) {
        try (InputStream inputStream = getResourceClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return objectMapper.readTree(inputStream).toPrettyString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource: " + resourcePath, e);
        }
    }


    public <T> T readJsonResourceAsPojo(String resourcePath, Class<T> type) {
        try (InputStream inputStream = getResourceClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return objectMapper.readValue(inputStream, type);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource: " + resourcePath, e);
        }
    }

    public InputStream readResourceAsInputStream(String resourcePath) {
        return getResourceClass().getResourceAsStream(resourcePath);
    }
}
