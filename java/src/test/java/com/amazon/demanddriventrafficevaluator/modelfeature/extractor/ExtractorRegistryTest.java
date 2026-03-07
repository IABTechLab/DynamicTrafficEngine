// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature.extractor;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.OpenRtbRequestContext;
import com.amazon.demanddriventrafficevaluator.modelfeature.FeatureExtractorType;
import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;
import com.amazon.demanddriventrafficevaluator.repository.entity.FeatureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ExtractorRegistryTest {
    private ExtractorRegistry registry;
    private Map<String, Extractor> mockExtractors;

    @Mock
    private Extractor mockExtractor1;

    @Mock
    private Extractor mockExtractor2;

    @BeforeEach
    void setUp() {
        // Initialize the map
        mockExtractors = new HashMap<>();
        mockExtractors.put("extractor1", mockExtractor1);
        mockExtractors.put("extractor2", mockExtractor2);

        // Create registry instance
        registry = new ExtractorRegistry(mockExtractors);
    }

    @Test
    void testConstructor() {
        // Test that registry is created with correct extractors
        assertNotNull(registry);
        assertEquals(2, mockExtractors.size());
        assertTrue(mockExtractors.containsKey("extractor1"));
        assertTrue(mockExtractors.containsKey("extractor2"));
    }

    @Test
    void testGetRegistryType() {
        // Test that registry type is correct
        assertEquals("Extractor", registry.getRegistryType());
    }

    @Test
    void testConstructorWithEmptyMap() {
        // Test constructor with empty map
        ExtractorRegistry emptyRegistry = new ExtractorRegistry(new HashMap<>());
        assertNotNull(emptyRegistry);
    }

    @Test
    void testRegisterClass() {
        mockExtractors = new HashMap<>();

        // Create registry instance
        registry = new ExtractorRegistry(mockExtractors);

        registry.register("jsonExtractor", JsonExtractor.class);

        assertEquals(1, mockExtractors.size());
        assertEquals(JsonExtractor.class, mockExtractors.get("jsonExtractor").getClass());
    }

    @Test
    void testRegisterDuplicatedClass() {
        assertThrows(IllegalArgumentException.class, () -> registry.register("extractor1", JsonExtractor.class));
    }

    @Test
    void testRegisterClassWithoutNoArgumentConstructor() {
        assertThrows(IllegalArgumentException.class, () -> registry.register("extractor3", TestExtractor.class));
    }

    @Test
    void testRegisterObject() {
        mockExtractors = new HashMap<>();

        // Create registry instance
        registry = new ExtractorRegistry(mockExtractors);

        registry.register("jsonExtractor", new JsonExtractor());

        assertEquals(1, mockExtractors.size());
        assertEquals(JsonExtractor.class, mockExtractors.get("jsonExtractor").getClass());
    }

    @Test
    void testRegisterDuplicatedObject() {
        assertThrows(IllegalArgumentException.class, () -> registry.register("extractor1", new JsonExtractor()));
    }

    private static class TestExtractor implements Extractor {
        @Override
        public ModelFeature extract(OpenRtbRequestContext documentContext, FeatureConfiguration featureConfiguration) {
            return null;
        }

        @Override
        public FeatureExtractorType getType() {
            return null;
        }
    }
}
