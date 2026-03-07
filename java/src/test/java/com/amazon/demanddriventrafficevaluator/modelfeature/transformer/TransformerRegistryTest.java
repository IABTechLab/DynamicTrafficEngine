// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature.transformer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class TransformerRegistryTest {

    @Mock
    private Transformer mockTransformer1;

    @Mock
    private Transformer mockTransformer2;

    private Map<String, Transformer> transformerMap;

    @BeforeEach
    void setUp() {
        transformerMap = new HashMap<>();
        transformerMap.put("transformer1", mockTransformer1);
        transformerMap.put("transformer2", mockTransformer2);
    }

    @Test
    void testConstructor() {
        // Act
        TransformerRegistry registry = new TransformerRegistry(transformerMap);

        // Assert
        assertNotNull(registry);
        // Assuming Registry has a method to get all records
        assertEquals(transformerMap, registry.getRecords());
    }

    @Test
    void testGetRegistryType() {
        // Arrange
        TransformerRegistry registry = new TransformerRegistry(transformerMap);

        // Act
        String registryType = registry.getRegistryType();

        // Assert
        assertEquals("Transformer", registryType);
    }

    @Test
    void testConstructorWithEmptyMap() {
        // Arrange
        Map<String, Transformer> emptyMap = new HashMap<>();

        // Act
        TransformerRegistry registry = new TransformerRegistry(emptyMap);

        // Assert
        assertNotNull(registry);
        assertTrue(registry.getRecords().isEmpty());
    }

    @Test
    void testInheritedMethods() {
        // Arrange
        TransformerRegistry registry = new TransformerRegistry(transformerMap);

        // Act & Assert
        // Assuming Registry has these methods
        assertTrue(registry.getRecords().containsKey("transformer1"));
        assertFalse(registry.getRecords().containsKey("nonexistent"));
        assertEquals(mockTransformer1, registry.get("transformer1"));
        assertThrows(IllegalArgumentException.class, () -> registry.get("nonexistent"));
    }
}
