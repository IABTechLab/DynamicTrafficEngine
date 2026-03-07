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
class ConcatenateByPairTest {

    @Mock
    private ModelFeature modelFeature;

    @Mock
    private FeatureConfiguration featureConfiguration;

    private ConcatenateByPair concatenateByPair;

    @BeforeEach
    void setUp() {
        concatenateByPair = new ConcatenateByPair();
    }

    @Test
    void testTransform_WithValidPairs() {
        // Arrange
        List<String> originalValues = Arrays.asList("a", "1", "b", "2", "c", "3");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = concatenateByPair.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("ax1", "bx2", "cx3"), result.getValues());
    }

    @Test
    void testTransform_WithOddNumberOfValues() {
        // Arrange
        List<String> originalValues = Arrays.asList("a", "1", "b", "2", "c");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = concatenateByPair.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("ax1", "bx2"), result.getValues());
    }

    @Test
    void testTransform_WithEmptyValues() {
        // Arrange
        List<String> originalValues = Collections.emptyList();
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = concatenateByPair.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertTrue(result.getValues().isEmpty());
    }

    @Test
    void testTransform_WithNullValues() {
        // Arrange
        List<String> originalValues = Arrays.asList("a", null, "b", "2", null, "3");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = concatenateByPair.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(List.of("bx2"), result.getValues());
    }

    @Test
    void testTransform_WithEmptyStrings() {
        // Arrange
        List<String> originalValues = Arrays.asList("a", "", "b", "2", "", "3");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = concatenateByPair.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(List.of("bx2"), result.getValues());
    }

    @Test
    void testTransform_WithAllNullOrEmptyValues() {
        // Arrange
        List<String> originalValues = Arrays.asList(null, "", "", null, null, "");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = concatenateByPair.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertTrue(result.getValues().isEmpty());
    }

    @Test
    void testTransform_WithSingleValue() {
        // Arrange
        List<String> originalValues = Collections.singletonList("a");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = concatenateByPair.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertTrue(result.getValues().isEmpty());
    }

    @Test
    void testTransform_WithLargeNumberOfValues() {
        // Arrange
        List<String> originalValues = Arrays.asList("a", "1", "b", "2", "c", "3", "d", "4", "e", "5");
        when(modelFeature.getValues()).thenReturn(originalValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = concatenateByPair.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("ax1", "bx2", "cx3", "dx4", "ex5"), result.getValues());
    }
}
