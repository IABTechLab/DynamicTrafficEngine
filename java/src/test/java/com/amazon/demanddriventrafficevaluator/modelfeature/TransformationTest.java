// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature;

import com.amazon.demanddriventrafficevaluator.modelfeature.transformer.Transformer;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransformationTest {

    @Mock
    private Registry<Transformer> mockTransformerRegistry;

    @Mock
    private ModelFeature mockInputFeature;

    @Mock
    private FeatureConfiguration mockConfiguration;

    @Mock
    private Transformer mockTransformer1;

    @Mock
    private Transformer mockTransformer2;

    private Transformation transformation;

    @BeforeEach
    void setUp() {
        transformation = new Transformation(mockTransformerRegistry);
    }

    @Test
    void testTransform_WithSingleTransformer() {
        // Arrange
        List<FeatureTransformerName> transformations = Collections.singletonList(FeatureTransformerName.ApplyMappings);
        when(mockInputFeature.getConfiguration()).thenReturn(mockConfiguration);
        when(mockConfiguration.getTransformations()).thenReturn(transformations);
        when(mockTransformerRegistry.get("ApplyMappings")).thenReturn(mockTransformer1);
        when(mockTransformer1.transform(mockInputFeature)).thenReturn(mockInputFeature);

        // Act
        ModelFeature result = transformation.transform(mockInputFeature);

        // Assert
        assertNotNull(result);
        verify(mockTransformer1).transform(mockInputFeature);
    }

    @Test
    void testTransform_WithMultipleTransformers() {
        // Arrange
        List<FeatureTransformerName> transformations = Arrays.asList(FeatureTransformerName.ApplyMappings, FeatureTransformerName.ConcatenateByPair);
        when(mockInputFeature.getConfiguration()).thenReturn(mockConfiguration);
        when(mockConfiguration.getTransformations()).thenReturn(transformations);
        when(mockTransformerRegistry.get("ApplyMappings")).thenReturn(mockTransformer1);
        when(mockTransformerRegistry.get("ConcatenateByPair")).thenReturn(mockTransformer2);
        when(mockTransformer1.transform(mockInputFeature)).thenReturn(mockInputFeature);
        when(mockTransformer2.transform(mockInputFeature)).thenReturn(mockInputFeature);

        // Act
        ModelFeature result = transformation.transform(mockInputFeature);

        // Assert
        assertNotNull(result);
        verify(mockTransformer1).transform(mockInputFeature);
        verify(mockTransformer2).transform(mockInputFeature);
    }

    @Test
    void testTransform_WithNoTransformers() {
        // Arrange
        when(mockInputFeature.getConfiguration()).thenReturn(mockConfiguration);
        when(mockConfiguration.getTransformations()).thenReturn(Collections.emptyList());

        // Act
        ModelFeature result = transformation.transform(mockInputFeature);

        // Assert
        assertNotNull(result);
        assertEquals(mockInputFeature, result);
        verifyNoInteractions(mockTransformerRegistry);
    }

    @Test
    void testTransform_WithNonExistentTransformer() {
        // Arrange
        List<FeatureTransformerName> transformations = Collections.singletonList(FeatureTransformerName.ApplyMappings);
        when(mockInputFeature.getConfiguration()).thenReturn(mockConfiguration);
        when(mockConfiguration.getTransformations()).thenReturn(transformations);
        when(mockTransformerRegistry.get("ApplyMappings")).thenReturn(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> transformation.transform(mockInputFeature));
        assertEquals("No transformer found for FeatureTransformerName: ApplyMappings", exception.getMessage());
    }

    @Test
    void testTransform_WithNullConfiguration() {
        // Arrange
        when(mockInputFeature.getConfiguration()).thenReturn(null);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> transformation.transform(mockInputFeature));
    }

    @Test
    void testTransform_WithNullTransformations() {
        // Arrange
        when(mockInputFeature.getConfiguration()).thenReturn(mockConfiguration);
        when(mockConfiguration.getTransformations()).thenReturn(null);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> transformation.transform(mockInputFeature));
    }
}
