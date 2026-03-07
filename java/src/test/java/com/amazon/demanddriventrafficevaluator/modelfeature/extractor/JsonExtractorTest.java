// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature.extractor;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.OpenRtbRequestContext;
import com.amazon.demanddriventrafficevaluator.modelfeature.FeatureExtractorType;
import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;
import com.amazon.demanddriventrafficevaluator.repository.entity.FeatureConfiguration;
import org.apache.commons.lang3.StringUtils;
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
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JsonExtractorTest {

    @Mock
    private OpenRtbRequestContext openRtbRequestContextContext;

    @Mock
    private FeatureConfiguration featureConfiguration;

    private JsonExtractor jsonExtractor;

    @BeforeEach
    void setUp() {
        jsonExtractor = new JsonExtractor();
    }

    @Test
    void testExtract_WithValidPath() {
        // Arrange
        List<String> fields = List.of("$.field1");
        when(featureConfiguration.getFields()).thenReturn(fields);
        when(openRtbRequestContextContext.findPath("$.field1")).thenReturn(List.of("value1"));

        // Act
        ModelFeature result = jsonExtractor.extract(openRtbRequestContextContext, featureConfiguration);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(List.of("value1"), result.getValues());
        verify(openRtbRequestContextContext, times(1)).findPath("$.field1");
    }

    @Test
    void testExtract_WithValidPaths() {
        // Arrange
        List<String> fields = Arrays.asList("$.field1", "$.field2");
        when(featureConfiguration.getFields()).thenReturn(fields);
        when(openRtbRequestContextContext.findPath("$.field1")).thenReturn(List.of("value1"));
        when(openRtbRequestContextContext.findPath("$.field2")).thenReturn(List.of("value2"));

        // Act
        ModelFeature result = jsonExtractor.extract(openRtbRequestContextContext, featureConfiguration);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("value1", "value2"), result.getValues());
        verify(openRtbRequestContextContext, times(1)).findPath("$.field1");
        verify(openRtbRequestContextContext, times(1)).findPath("$.field2");
    }

    @Test
    void testExtract_WithMultipleValues() {
        // Arrange
        List<String> fields = Arrays.asList("$.field1", "$.field2");
        when(featureConfiguration.getFields()).thenReturn(fields);
        when(openRtbRequestContextContext.findPath("$.field1")).thenReturn(Arrays.asList("value1", "value3", "value4"));
        when(openRtbRequestContextContext.findPath("$.field2")).thenReturn(List.of("value2"));

        // Act
        ModelFeature result = jsonExtractor.extract(openRtbRequestContextContext, featureConfiguration);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("value1", "value3", "value4", "value2"), result.getValues());
        verify(openRtbRequestContextContext, times(1)).findPath("$.field1");
        verify(openRtbRequestContextContext, times(1)).findPath("$.field2");
    }

    @Test
    void testExtract_WithEmptyFields() {
        // Arrange
        when(featureConfiguration.getFields()).thenReturn(Collections.emptyList());

        // Act
        ModelFeature result = jsonExtractor.extract(openRtbRequestContextContext, featureConfiguration);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertTrue(result.getValues().isEmpty());
        verify(openRtbRequestContextContext, never()).findPath(anyString());
    }

    @Test
    void testExtract_WithPathNotFoundException() {
        // Arrange
        List<String> fields = Arrays.asList("$.field1", "$.nonexistent");
        when(featureConfiguration.getFields()).thenReturn(fields);
        when(openRtbRequestContextContext.findPath("$.field1")).thenReturn(List.of("value1"));
        when(openRtbRequestContextContext.findPath("$.nonexistent")).thenReturn(List.of(StringUtils.EMPTY));

        // Act
        ModelFeature result = jsonExtractor.extract(openRtbRequestContextContext, featureConfiguration);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("value1", StringUtils.EMPTY), result.getValues());
        verify(openRtbRequestContextContext, times(1)).findPath("$.field1");
        verify(openRtbRequestContextContext, times(1)).findPath("$.nonexistent");
    }

    @Test
    void testExtract_WithNullValue() {
        // Arrange
        List<String> fields = Collections.singletonList("$.nullField");
        when(featureConfiguration.getFields()).thenReturn(fields);
        when(openRtbRequestContextContext.findPath("$.nullField")).thenReturn(List.of("null"));

        // Act
        ModelFeature result = jsonExtractor.extract(openRtbRequestContextContext, featureConfiguration);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(List.of("null"), result.getValues());
        verify(openRtbRequestContextContext, times(1)).findPath("$.nullField");
    }

    @Test
    void testGetType() {
        // Act
        FeatureExtractorType type = jsonExtractor.getType();

        // Assert
        assertEquals(FeatureExtractorType.JsonExtractor, type);
    }

    @Test
    void testExtract_WithMixedValues() {
        // Arrange
        List<String> fields = Arrays.asList("$.number", "$.boolean", "$.object");
        when(featureConfiguration.getFields()).thenReturn(fields);
        when(openRtbRequestContextContext.findPath("$.number")).thenReturn(List.of("123"));
        when(openRtbRequestContextContext.findPath("$.boolean")).thenReturn(List.of("true"));
        when(openRtbRequestContextContext.findPath("$.object")).thenReturn(List.of("{\"object\": \"value\"}"));

        // Act
        ModelFeature result = jsonExtractor.extract(openRtbRequestContextContext, featureConfiguration);

        // Assert
        assertNotNull(result);
        assertEquals(featureConfiguration, result.getConfiguration());
        assertEquals(Arrays.asList("123", "true", "{\"object\": \"value\"}"), result.getValues());
        verify(openRtbRequestContextContext, times(1)).findPath("$.number");
        verify(openRtbRequestContextContext, times(1)).findPath("$.boolean");
        verify(openRtbRequestContextContext, times(1)).findPath("$.object");
    }
}
