// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.provider.model;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;
import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelDefinition;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelResult;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleBasedModelResultProviderTest {

    @Mock
    private Dao<String, Double> mockRuleBasedModelResultDao;

    @Mock
    private ModelResultProviderInput mockInput;

    @Mock
    private ModelDefinition mockModelDefinition;

    private RuleBasedModelResultProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RuleBasedModelResultProvider(mockRuleBasedModelResultDao);
    }

    @Test
    void testProvide_WithExistingModelResult_LowValue() {
        // Arrange
        List<ModelFeature> modelFeatures = Arrays.asList(
                createModelFeature("value1"),
                createModelFeature("value2")
        );
        when(mockInput.getModelFeatures()).thenReturn(modelFeatures);
        when(mockInput.getModelDefinition()).thenReturn(mockModelDefinition);
        when(mockModelDefinition.getIdentifier()).thenReturn("model1");
        when(mockModelDefinition.getType()).thenReturn(ModelValueType.LowValue);
        when(mockRuleBasedModelResultDao.get("model1", "value1|value2")).thenReturn(Optional.of(0.5));

        // Act
        ModelResult result = provider.provide(mockInput);

        // Assert
        assertEquals(Collections.singletonList("value1|value2"), result.getKeys());
        assertEquals(Collections.singletonList(0.5), result.getValues());
        assertEquals(0.5, result.getValue());
        verify(mockRuleBasedModelResultDao).get("model1", "value1|value2");
    }

    @Test
    void testProvide_WithNonExistingModelResult_LowValue() {
        // Arrange
        List<ModelFeature> modelFeatures = Collections.singletonList(
                createModelFeature("value1")
        );
        when(mockInput.getModelFeatures()).thenReturn(modelFeatures);
        when(mockInput.getModelDefinition()).thenReturn(mockModelDefinition);
        when(mockModelDefinition.getIdentifier()).thenReturn("model2");
        when(mockModelDefinition.getType()).thenReturn(ModelValueType.LowValue);
        when(mockRuleBasedModelResultDao.get("model2", "value1")).thenReturn(Optional.empty());

        // Act
        ModelResult result = provider.provide(mockInput);

        // Assert
        assertEquals(Collections.singletonList("value1"), result.getKeys());
        assertEquals(Collections.singletonList(1.0), result.getValues());
        assertEquals(1.0, result.getValue());
        verify(mockRuleBasedModelResultDao).get("model2", "value1");
    }

    @Test
    void testProvide_WithExistingModelResult_HighValue() {
        // Arrange
        List<ModelFeature> modelFeatures = Arrays.asList(
                createModelFeature("value1"),
                createModelFeature("value2")
        );
        when(mockInput.getModelFeatures()).thenReturn(modelFeatures);
        when(mockInput.getModelDefinition()).thenReturn(mockModelDefinition);
        when(mockModelDefinition.getIdentifier()).thenReturn("model1");
        when(mockModelDefinition.getType()).thenReturn(ModelValueType.HighValue);
        when(mockRuleBasedModelResultDao.get("model1", "value1|value2")).thenReturn(Optional.of(0.5));

        // Act
        ModelResult result = provider.provide(mockInput);

        // Assert
        assertEquals(Collections.singletonList("value1|value2"), result.getKeys());
        assertEquals(Collections.singletonList(0.5), result.getValues());
        assertEquals(0.5, result.getValue());
        verify(mockRuleBasedModelResultDao).get("model1", "value1|value2");
    }

    @Test
    void testProvide_WithNonExistingModelResult_HighValue() {
        // Arrange
        List<ModelFeature> modelFeatures = Collections.singletonList(
                createModelFeature("value1")
        );
        when(mockInput.getModelFeatures()).thenReturn(modelFeatures);
        when(mockInput.getModelDefinition()).thenReturn(mockModelDefinition);
        when(mockModelDefinition.getIdentifier()).thenReturn("model2");
        when(mockModelDefinition.getType()).thenReturn(ModelValueType.HighValue);
        when(mockRuleBasedModelResultDao.get("model2", "value1")).thenReturn(Optional.empty());

        // Act
        ModelResult result = provider.provide(mockInput);

        // Assert
        assertEquals(Collections.singletonList("value1"), result.getKeys());
        assertEquals(Collections.singletonList(0.0), result.getValues());
        assertEquals(0.0, result.getValue());
        verify(mockRuleBasedModelResultDao).get("model2", "value1");
    }

    @Test
    void testProvide_WithMultipleValuesPerFeature() {
        // Arrange
        List<ModelFeature> modelFeatures = Arrays.asList(
                createModelFeature("value1", "value2"),
                createModelFeature("value3", "value4")
        );
        when(mockInput.getModelFeatures()).thenReturn(modelFeatures);
        when(mockInput.getModelDefinition()).thenReturn(mockModelDefinition);
        when(mockModelDefinition.getIdentifier()).thenReturn("model3");
        when(mockModelDefinition.getType()).thenReturn(ModelValueType.LowValue);
        when(mockRuleBasedModelResultDao.get("model3", "value1|value3")).thenReturn(Optional.of(0.2));
        when(mockRuleBasedModelResultDao.get("model3", "value1|value4")).thenReturn(Optional.of(0.75));
        when(mockRuleBasedModelResultDao.get("model3", "value2|value3")).thenReturn(Optional.of(0.6));
        when(mockRuleBasedModelResultDao.get("model3", "value2|value4")).thenReturn(Optional.of(0.5));

        // Act
        ModelResult result = provider.provide(mockInput);

        // Assert
        assertEquals(Arrays.asList("value1|value3", "value1|value4", "value2|value3", "value2|value4"), result.getKeys());
        assertEquals(Arrays.asList(0.2, 0.75, 0.6, 0.5), result.getValues());
        assertEquals(0.2, result.getValue());
        verify(mockRuleBasedModelResultDao).get("model3", "value1|value3");
        verify(mockRuleBasedModelResultDao).get("model3", "value1|value4");
        verify(mockRuleBasedModelResultDao).get("model3", "value2|value3");
        verify(mockRuleBasedModelResultDao).get("model3", "value2|value4");
    }

    @Test
    void testProvide_WithMultipleValuesPerFeatureWithMiss() {
        // Arrange
        List<ModelFeature> modelFeatures = Arrays.asList(
                createModelFeature("value1", "value2"),
                createModelFeature("value3", "value4")
        );
        when(mockInput.getModelFeatures()).thenReturn(modelFeatures);
        when(mockInput.getModelDefinition()).thenReturn(mockModelDefinition);
        when(mockModelDefinition.getIdentifier()).thenReturn("model3");
        when(mockModelDefinition.getType()).thenReturn(ModelValueType.LowValue);
        when(mockRuleBasedModelResultDao.get("model3", "value1|value3")).thenReturn(Optional.empty());
        when(mockRuleBasedModelResultDao.get("model3", "value1|value4")).thenReturn(Optional.of(0.75));
        when(mockRuleBasedModelResultDao.get("model3", "value2|value3")).thenReturn(Optional.of(0.6));
        when(mockRuleBasedModelResultDao.get("model3", "value2|value4")).thenReturn(Optional.of(0.5));

        // Act
        ModelResult result = provider.provide(mockInput);

        // Assert
        assertEquals(Arrays.asList("value1|value3", "value1|value4", "value2|value3", "value2|value4"), result.getKeys());
        assertEquals(Arrays.asList(1.0, 0.75, 0.6, 0.5), result.getValues());
        assertEquals(0.75, result.getValue());
        verify(mockRuleBasedModelResultDao).get("model3", "value1|value3");
        verify(mockRuleBasedModelResultDao).get("model3", "value1|value4");
        verify(mockRuleBasedModelResultDao).get("model3", "value2|value3");
        verify(mockRuleBasedModelResultDao).get("model3", "value2|value4");
    }

    @Test
    void testProvide_WithEmptyFeatures() {
        // Arrange
        when(mockInput.getModelFeatures()).thenReturn(Collections.emptyList());
        when(mockInput.getModelDefinition()).thenReturn(mockModelDefinition);
        when(mockModelDefinition.getIdentifier()).thenReturn("model4");
        when(mockModelDefinition.getType()).thenReturn(ModelValueType.LowValue);

        // Act
        ModelResult result = provider.provide(mockInput);

        // Assert
        assertEquals(Collections.emptyList(), result.getKeys());
        assertEquals(Collections.emptyList(), result.getValues());
        assertEquals(1.0, result.getValue());
        verify(mockRuleBasedModelResultDao, never()).get(anyString(), anyString());
    }

    @Test
    void buildKeys_emptyFeaturesList_returnsEmptyList() {
        List<ModelFeature> emptyFeatures = Collections.emptyList();

        List<String> result = provider.buildKeys(emptyFeatures);

        assertTrue(result.isEmpty());
    }

    @Test
    void buildKeys_singleFeature_returnsFeatureValues() {
        ModelFeature feature = ModelFeature.builder()
                .values(Arrays.asList("A", "B"))
                .build();

        List<String> result = provider.buildKeys(Collections.singletonList(feature));

        assertEquals(Arrays.asList("A", "B"), result);
    }

    @Test
    void buildKeys_twoFeatures_returnsAllPermutations() {
        ModelFeature feature1 = ModelFeature.builder()
                .values(Arrays.asList("A", "B"))
                .build();
        ModelFeature feature2 = ModelFeature.builder()
                .values(Arrays.asList("1", "2"))
                .build();

        List<String> result = provider.buildKeys(Arrays.asList(feature1, feature2));

        List<String> expected = Arrays.asList("A|1", "A|2", "B|1", "B|2");
        assertEquals(expected, result);
    }

    @Test
    void buildKeys_threeFeatures_returnsAllPermutations() {
        ModelFeature feature1 = ModelFeature.builder()
                .values(Arrays.asList("A", "B"))
                .build();
        ModelFeature feature2 = ModelFeature.builder()
                .values(Arrays.asList("1", "2"))
                .build();
        ModelFeature feature3 = ModelFeature.builder()
                .values(Arrays.asList("X", "Y"))
                .build();

        List<String> result = provider.buildKeys(Arrays.asList(feature1, feature2, feature3));

        List<String> expected = Arrays.asList(
                "A|1|X", "A|1|Y", "A|2|X", "A|2|Y",
                "B|1|X", "B|1|Y", "B|2|X", "B|2|Y"
        );
        assertEquals(expected, result);
    }

    @Test
    void buildKeys_featureWithEmptyValues_returnsEmptyList() {
        ModelFeature feature1 = ModelFeature.builder()
                .values(Arrays.asList("A", "B"))
                .build();
        ModelFeature feature2 = ModelFeature.builder()
                .values(Collections.emptyList())
                .build();

        List<String> result = provider.buildKeys(Arrays.asList(feature1, feature2));

        assertTrue(result.isEmpty());
    }

    @Test
    void buildKeys_featureWithNullValues_returnsEmptyList() {
        ModelFeature feature1 = ModelFeature.builder()
                .values(Arrays.asList("A", "B"))
                .build();
        ModelFeature feature2 = ModelFeature.builder()
                .values(null)
                .build();

        List<String> result = provider.buildKeys(Arrays.asList(feature1, feature2));

        assertTrue(result.isEmpty());
    }

    @Test
    void buildKeys_nullFeatureList_returnsEmptyList() {
        List<String> result = provider.buildKeys(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void buildKeys_singleValueFeatures_returnsCorrectPermutation() {
        ModelFeature feature1 = ModelFeature.builder()
                .values(Collections.singletonList("A"))
                .build();
        ModelFeature feature2 = ModelFeature.builder()
                .values(Collections.singletonList("1"))
                .build();

        List<String> result = provider.buildKeys(Arrays.asList(feature1, feature2));

        assertEquals(Collections.singletonList("A|1"), result);
    }

    private ModelFeature createModelFeature(String... values) {
        ModelFeature feature = mock(ModelFeature.class);
        when(feature.getValues()).thenReturn(Arrays.asList(values));
        return feature;
    }
}
