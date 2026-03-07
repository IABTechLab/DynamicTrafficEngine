// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature.transformer;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;
import com.amazon.demanddriventrafficevaluator.repository.entity.FeatureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetFirstNotEmptyTest {

    @Mock
    private ModelFeature modelFeature;

    @Mock
    private FeatureConfiguration featureConfiguration;

    private GetFirstNotEmpty getFirstNotEmpty;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        getFirstNotEmpty = new GetFirstNotEmpty();
    }

    @Test
    void testTransform_WithNonEmptyFirstValue() {
        // Arrange
        List<String> originalValues = Arrays.asList("value1", "value2", "value3");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = getFirstNotEmpty.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Collections.singletonList("value1"), result.getValues());
    }

    @Test
    void testTransform_WithEmptyFirstValue() {
        // Arrange
        List<String> originalValues = Arrays.asList("", "value2", "value3");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = getFirstNotEmpty.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Collections.singletonList("value2"), result.getValues());
    }

    @Test
    void testTransform_WithAllEmptyValues() {
        // Arrange
        List<String> originalValues = Arrays.asList("", "", "");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = getFirstNotEmpty.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Collections.singletonList(""), result.getValues());
    }

    @Test
    void testTransform_WithEmptyList() {
        // Arrange
        List<String> originalValues = Collections.emptyList();
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = getFirstNotEmpty.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Collections.singletonList(""), result.getValues());
    }

    @Test
    void testTransform_WithNullValues() {
        // Arrange
        List<String> originalValues = Arrays.asList(null, null, "value3");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = getFirstNotEmpty.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Collections.singletonList("value3"), result.getValues());
    }

    @Test
    void testTransform_WithMixedValues() {
        // Arrange
        List<String> originalValues = Arrays.asList(null, "", "value3", "value4");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = getFirstNotEmpty.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Collections.singletonList("value3"), result.getValues());
    }

    @Test
    void testTransform_WithAllNullValues() {
        // Arrange
        List<String> originalValues = Arrays.asList(null, null, null);
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = getFirstNotEmpty.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Collections.singletonList(""), result.getValues());
    }

    @Test
    void testTransform_WithWhitespaceValues() {
        // Arrange
        List<String> originalValues = Arrays.asList(" ", "  ", "\t", "value");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = getFirstNotEmpty.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Collections.singletonList(" "), result.getValues());
    }
}
