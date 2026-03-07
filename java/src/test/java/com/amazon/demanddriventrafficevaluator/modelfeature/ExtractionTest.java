// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.OpenRtbRequestContext;
import com.amazon.demanddriventrafficevaluator.modelfeature.extractor.Extractor;
import com.amazon.demanddriventrafficevaluator.repository.entity.FeatureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractionTest {

    @Mock
    private Registry<Extractor> mockExtractorRegistry;

    @Mock
    private OpenRtbRequestContext mockOpenRtbRequest;

    @Mock
    private FeatureConfiguration mockConfiguration;

    @Mock
    private Extractor mockExtractor;

    @Mock
    private ModelFeature mockModelFeature;

    private Extraction extraction;

    @BeforeEach
    void setUp() {
        extraction = new Extraction(mockExtractorRegistry);
    }

    @Test
    void testExtract_WithValidExtractor() {
        // Arrange
        FeatureExtractorType extractorType = FeatureExtractorType.JsonExtractor;
        when(mockExtractorRegistry.get(extractorType.toString())).thenReturn(mockExtractor);
        when(mockExtractor.extract(mockOpenRtbRequest, mockConfiguration)).thenReturn(mockModelFeature);

        // Act
        ModelFeature result = extraction.extract(mockOpenRtbRequest, mockConfiguration, extractorType);

        // Assert
        assertNotNull(result);
        assertEquals(mockModelFeature, result);
        verify(mockExtractorRegistry).get(extractorType.toString());
        verify(mockExtractor).extract(mockOpenRtbRequest, mockConfiguration);
    }

    @Test
    void testExtract_WithNonExistentExtractor() {
        // Arrange
        FeatureExtractorType extractorType = FeatureExtractorType.JsonExtractor;
        when(mockExtractorRegistry.get(extractorType.toString())).thenReturn(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> extraction.extract(mockOpenRtbRequest, mockConfiguration, extractorType));

        assertEquals("No extractor found for feature extraction type: " + extractorType, exception.getMessage());
        verify(mockExtractorRegistry).get(extractorType.toString());
        verify(mockExtractor, never()).extract(any(), any());
    }

    @Test
    void testExtract_WithNullExtractorType() {
        // Act & Assert
        assertThrows(NullPointerException.class,
                () -> extraction.extract(mockOpenRtbRequest, mockConfiguration, null));
    }
}
