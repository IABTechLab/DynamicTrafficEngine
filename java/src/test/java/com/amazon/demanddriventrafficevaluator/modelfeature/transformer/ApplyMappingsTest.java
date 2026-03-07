// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature.transformer;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;
import com.amazon.demanddriventrafficevaluator.repository.entity.FeatureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyMappingsTest {

    @Mock
    private ModelFeature modelFeature;

    @Mock
    private FeatureConfiguration featureConfiguration;

    private ApplyMappings applyMappings;

    @BeforeEach
    void setUp() {
        applyMappings = new ApplyMappings();
    }

    @Test
    void testTransform_WithValidMappings() {
        // Arrange
        Map<String, String> mapping = new HashMap<>();
        mapping.put("value1", "mapped1");
        mapping.put("value2", "mapped2");

        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);
        when(modelFeature.getValues()).thenReturn(Arrays.asList("value1", "value2"));
        when(featureConfiguration.getMapping()).thenReturn(mapping);
        when(featureConfiguration.getMappingDefaultValue()).thenReturn("default");

        // Act
        ModelFeature result = applyMappings.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("mapped1", "mapped2"), result.getValues());
        verify(modelFeature, times(2)).getConfiguration();
        verify(modelFeature, times(1)).getValues();
        verify(featureConfiguration, times(1)).getMapping();
        verify(featureConfiguration, times(1)).getMappingDefaultValue();
    }

    @Test
    void testTransform_WithDefaultValue() {
        // Arrange
        Map<String, String> mapping = new HashMap<>();
        mapping.put("value1", "mapped1");

        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);
        when(modelFeature.getValues()).thenReturn(Arrays.asList("value1", "unmapped"));
        when(featureConfiguration.getMapping()).thenReturn(mapping);
        when(featureConfiguration.getMappingDefaultValue()).thenReturn("default");

        // Act
        ModelFeature result = applyMappings.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("mapped1", "default"), result.getValues());
    }

    @Test
    void testTransform_WithEmptyValues() {
        // Arrange
        Map<String, String> mapping = new HashMap<>();
        mapping.put("value1", "mapped1");

        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);
        when(modelFeature.getValues()).thenReturn(Collections.emptyList());
        when(featureConfiguration.getMapping()).thenReturn(mapping);
        when(featureConfiguration.getMappingDefaultValue()).thenReturn("default");

        // Act
        ModelFeature result = applyMappings.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertTrue(result.getValues().isEmpty());
    }

    @Test
    void testTransform_WithEmptyMapping() {
        // Arrange
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);
        when(modelFeature.getValues()).thenReturn(Arrays.asList("value1", "value2"));
        when(featureConfiguration.getMapping()).thenReturn(Collections.emptyMap());
        when(featureConfiguration.getMappingDefaultValue()).thenReturn("default");

        // Act
        ModelFeature result = applyMappings.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("default", "default"), result.getValues());
    }

    @Test
    void testTransform_WithNullDefaultValue() {
        // Arrange
        Map<String, String> mapping = new HashMap<>();
        mapping.put("value1", "mapped1");

        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);
        when(modelFeature.getValues()).thenReturn(Arrays.asList("value1", "unmapped"));
        when(featureConfiguration.getMapping()).thenReturn(mapping);
        when(featureConfiguration.getMappingDefaultValue()).thenReturn(null);

        // Act
        ModelFeature result = applyMappings.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("mapped1", null), result.getValues());
    }

    @Test
    void testTransform_WithNullValues() {
        // Arrange
        Map<String, String> mapping = new HashMap<>();
        mapping.put("value1", "mapped1");
        mapping.put(null, "mapped_null");

        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);
        when(modelFeature.getValues()).thenReturn(Arrays.asList("value1", null));
        when(featureConfiguration.getMapping()).thenReturn(mapping);
        when(featureConfiguration.getMappingDefaultValue()).thenReturn("default");

        // Act
        ModelFeature result = applyMappings.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("mapped1", "mapped_null"), result.getValues());
    }

    @Test
    void testTransform_WithNullMapping() {
        // Arrange
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);
        when(modelFeature.getValues()).thenReturn(Arrays.asList("value1", "value2"));
        when(featureConfiguration.getMapping()).thenReturn(null);
        when(featureConfiguration.getMappingDefaultValue()).thenReturn("default");

        // Act & Assert
        assertThrows(NullPointerException.class, () -> applyMappings.transform(modelFeature));
    }
}
