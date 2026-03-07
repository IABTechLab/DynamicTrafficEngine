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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncludeDefaultValueTest {

    @Mock
    private ModelFeature modelFeature;

    @Mock
    private FeatureConfiguration featureConfiguration;

    private IncludeDefaultValue includeDefaultValue;

    @BeforeEach
    void setUp() {
        includeDefaultValue = new IncludeDefaultValue();
    }

    @Test
    void testTransform_WithDefault() {
        // Arrange
        List<String> mixedValues = Arrays.asList("value1", "value2");
        when(modelFeature.getValues()).thenReturn(mixedValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);
        when(featureConfiguration.getMappingDefaultValue()).thenReturn("default");

        // Act
        ModelFeature result = includeDefaultValue.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("value1", "value2", "default"), result.getValues());
    }

    @Test
    void testTransform_WithEmptyValues() {
        // Arrange
        List<String> emptyValues = Collections.emptyList();
        when(modelFeature.getValues()).thenReturn(emptyValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = includeDefaultValue.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertTrue(result.getValues().isEmpty());
    }

    @Test
    void testTransform_WithMixedValues() {
        // Arrange
        List<String> mixedValues = Arrays.asList("value1", "", null, "value2");
        when(modelFeature.getValues()).thenReturn(mixedValues);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = includeDefaultValue.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("value1", "value2"), result.getValues());
    }

    @Test
    void testTransform_WithAllEmptyStrings() {
        // Arrange
        List<String> emptyStrings = Arrays.asList("", "", "");
        when(modelFeature.getValues()).thenReturn(emptyStrings);
        when(modelFeature.getConfiguration()).thenReturn(featureConfiguration);

        // Act
        ModelFeature result = includeDefaultValue.transform(modelFeature);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertTrue(result.getValues().isEmpty());
    }

    @Test
    void testTransform_WithNullModelFeature() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            includeDefaultValue.transform(null);
        });
    }

}
