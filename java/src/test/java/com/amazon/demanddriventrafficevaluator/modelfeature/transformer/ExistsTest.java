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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExistsTest {

    @Mock
    private ModelFeature modelFeature;

    @Mock
    private FeatureConfiguration featureConfiguration;

    private Exists exists;

    @BeforeEach
    void setUp() {
        exists = new Exists();
    }

    @Test
    void testTransform_WithNonEmptyValues() {
        // Arrange
        List<String> originalValues = Arrays.asList("value1", "value2", "value3");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = exists.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("1", "1", "1"), result.getValues());
        assertEquals("ModelFeature(configuration=featureConfiguration, values=[1, 1, 1])", result.toString());
    }

    @Test
    void testTransform_WithEmptyValues() {
        // Arrange
        List<String> originalValues = Arrays.asList("", "", "");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = exists.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("0", "0", "0"), result.getValues());
    }

    @Test
    void testTransform_WithMixedValues() {
        // Arrange
        List<String> originalValues = Arrays.asList("value1", "", "value3", "");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = exists.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("1", "0", "1", "0"), result.getValues());
    }

    @Test
    void testTransform_WithEmptyList() {
        // Arrange
        List<String> originalValues = Collections.emptyList();
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = exists.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertTrue(result.getValues().isEmpty());
    }

    @Test
    void testTransform_WithNullValues() {
        // Arrange
        List<String> originalValues = Arrays.asList("value1", null, "value3");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act & Assert
        ModelFeature result = exists.transform(modelFeature);

        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("1", "1"), result.getValues());
    }

    @Test
    void testTransform_WithLargeNumberOfValues() {
        // Arrange
        List<String> originalValues = Arrays.asList("value1", "", "value3", "", "value5", "value6", "", "value8", "value9", "");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = exists.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("1", "0", "1", "0", "1", "1", "0", "1", "1", "0"), result.getValues());
    }

    @Test
    void testTransform_WithWhitespaceValues() {
        // Arrange
        List<String> originalValues = Arrays.asList("value1", " ", "  ", "\t", "\n");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = exists.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("1", "1", "1", "1", "1"), result.getValues());
    }
}
