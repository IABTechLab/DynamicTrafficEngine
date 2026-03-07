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

class FeatureTransformerNameTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testEnumValues() {
        assertEquals(5, FeatureTransformerName.values().length);
        assertArrayEquals(
                new FeatureTransformerName[]{
                        FeatureTransformerName.ApplyMappings,
                        FeatureTransformerName.ConcatenateByPair,
                        FeatureTransformerName.GetFirstNotEmpty,
                        FeatureTransformerName.Exists,
                        FeatureTransformerName.IncludeDefaultValue
                },
                FeatureTransformerName.values()
        );
    }

    @ParameterizedTest
    @EnumSource(FeatureTransformerName.class)
    void testFromString(FeatureTransformerName name) {
        assertEquals(name, FeatureTransformerName.fromString(name.name()));
    }

    @Test
    void testFromString_WithInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> FeatureTransformerName.fromString("InvalidName"));
    }

    @Test
    void testFromString_WithNullValue() {
        assertThrows(NullPointerException.class, () -> FeatureTransformerName.fromString(null));
    }

    @ParameterizedTest
    @EnumSource(FeatureTransformerName.class)
    void testGetValue(FeatureTransformerName name) {
        assertEquals(name.name(), name.getValue());
    }

    @ParameterizedTest
    @EnumSource(FeatureTransformerName.class)
    void testJsonSerialization(FeatureTransformerName name) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(name);
        assertEquals("\"" + name.name() + "\"", json);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ApplyMappings", "ConcatenateByPair", "GetFirstNotEmpty", "Exists", "IncludeDefaultValue"})
    void testJsonDeserialization(String name) throws JsonProcessingException {
        FeatureTransformerName transformerName = objectMapper.readValue("\"" + name + "\"", FeatureTransformerName.class);
        assertEquals(FeatureTransformerName.valueOf(name), transformerName);
    }

    @Test
    void testJsonDeserialization_WithInvalidValue() {
        assertThrows(JsonProcessingException.class, () ->
                objectMapper.readValue("\"InvalidName\"", FeatureTransformerName.class)
        );
    }

    @Test
    void testEquality() {
        assertEquals(FeatureTransformerName.ApplyMappings, FeatureTransformerName.ApplyMappings);
        assertNotEquals(FeatureTransformerName.ApplyMappings, FeatureTransformerName.ConcatenateByPair);
    }

    @Test
    void testHashCode() {
        assertEquals(FeatureTransformerName.ApplyMappings.hashCode(), FeatureTransformerName.ApplyMappings.hashCode());
        assertNotEquals(FeatureTransformerName.ApplyMappings.hashCode(), FeatureTransformerName.ConcatenateByPair.hashCode());
    }

    @Test
    void testToString() {
        assertEquals("ApplyMappings", FeatureTransformerName.ApplyMappings.toString());
        assertEquals("ConcatenateByPair", FeatureTransformerName.ConcatenateByPair.toString());
        assertEquals("GetFirstNotEmpty", FeatureTransformerName.GetFirstNotEmpty.toString());
        assertEquals("Exists", FeatureTransformerName.Exists.toString());
        assertEquals("IncludeDefaultValue", FeatureTransformerName.IncludeDefaultValue.toString());
    }
}
