// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeatureExtractorTypeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testEnumValues() {
        assertEquals(2, FeatureExtractorType.values().length);
        assertArrayEquals(
                new FeatureExtractorType[]{FeatureExtractorType.JsonExtractor, FeatureExtractorType.ProtobufExtractor},
                FeatureExtractorType.values()
        );
    }

    @ParameterizedTest
    @EnumSource(FeatureExtractorType.class)
    void testFromString(FeatureExtractorType type) {
        assertEquals(type, FeatureExtractorType.fromString(type.name()));
    }

    @Test
    void testFromString_WithInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> FeatureExtractorType.fromString("InvalidType"));
    }

    @Test
    void testFromString_WithNullValue() {
        assertThrows(NullPointerException.class, () -> FeatureExtractorType.fromString(null));
    }

    @ParameterizedTest
    @EnumSource(FeatureExtractorType.class)
    void testGetValue(FeatureExtractorType type) {
        assertEquals(type.name(), type.getValue());
    }

    @ParameterizedTest
    @EnumSource(FeatureExtractorType.class)
    void testJsonSerialization(FeatureExtractorType type) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(type);
        assertEquals("\"" + type.name() + "\"", json);
    }

    @ParameterizedTest
    @ValueSource(strings = {"JsonExtractor", "ProtobufExtractor"})
    void testJsonDeserialization(String typeName) throws JsonProcessingException {
        FeatureExtractorType type = objectMapper.readValue("\"" + typeName + "\"", FeatureExtractorType.class);
        assertEquals(FeatureExtractorType.valueOf(typeName), type);
    }

    @Test
    void testJsonDeserialization_WithInvalidValue() {
        assertThrows(JsonProcessingException.class, () ->
                objectMapper.readValue("\"InvalidType\"", FeatureExtractorType.class)
        );
    }

    @Test
    void testEquality() {
        assertEquals(FeatureExtractorType.JsonExtractor, FeatureExtractorType.JsonExtractor);
        assertNotEquals(FeatureExtractorType.JsonExtractor, FeatureExtractorType.ProtobufExtractor);
    }

    @Test
    void testHashCode() {
        assertEquals(FeatureExtractorType.JsonExtractor.hashCode(), FeatureExtractorType.JsonExtractor.hashCode());
        assertNotEquals(FeatureExtractorType.JsonExtractor.hashCode(), FeatureExtractorType.ProtobufExtractor.hashCode());
    }
}
