// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.provider.model;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;
import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import java.util.List;
import lombok.extern.log4j.Log4j2;

/**
 * A provider class for retrieving rule-based model results.
 * <p>
 * This class implements the ModelResultProvider interface and is responsible for
 * providing model results based on input features. It uses a DAO to retrieve
 * results from a data store, typically a cache.
 * </p>
 */
@Log4j2
public class RuleBasedModelResultProvider implements ModelResultProvider {

    private static final String KEY_DELIMITER = "|";

    private final Dao<String, Double> ruleBasedModelResultDao;

    public RuleBasedModelResultProvider(Dao<String, Double> ruleBasedModelResultDao) {
        this.ruleBasedModelResultDao = ruleBasedModelResultDao;
    }

    /**
     * Provides a ModelResult based on the input features and model definition.
     * <p>
     * This method builds keys from the input features, retrieves the corresponding
     * values from the DAO, and constructs a ModelResult. If no values are found for
     * the keys, a default value based on the ModelType is used (low-value has default
     * value of 1.0, high-value has default value of 0.0). If multiple results are found,
     * the first value found in the DAO is used.
     * </p>
     *
     * @param input The ModelResultProviderInput containing model features and definition.
     * @return A ModelResult containing the keys, retrieved (or default) values, and overall value.
     */
    @Override
    public ModelResult provide(ModelResultProviderInput input) {
        List<String> keys = buildKeys(input.getModelFeatures());
        log.debug("In RuleBasedModelResultProvider keys: {}", keys);
        String cacheIdentifier = input.getModelDefinition().getIdentifier();
        double defaultValue = input.getModelDefinition().getType().getDefaultValue();

        List<Double> values = new ArrayList<>(keys.size());
        double value = defaultValue;
        boolean cacheHit = false;
        for (String key: keys) {
            Optional<Double> keyValue = ruleBasedModelResultDao.get(cacheIdentifier, key);
            if (keyValue.isPresent() && !cacheHit) {
                cacheHit = true;
                value = keyValue.get();
            }
            values.add(keyValue.orElse(defaultValue));
        }
        log.debug("In RuleBasedModelResultProvider values: {}", values);
        return ModelResult.builder()
                .keys(keys)
                .values(values)
                .value(value)
                .build();
    }

    /**
     * Builds every key permutation from the input features' values, with "|" delimiter.
     * <p>
     * If you have:
     * ModelFeature1 values: ["A", "B"]
     * ModelFeature2 values: ["1", "2"]
     * ModelFeature3 values: ["X", "Y"]
     * The result will be:
     * ["A|1|X", "A|1|Y", "A|2|X", "A|2|Y", "B|1|X", "B|1|Y", "B|2|X", "B|2|Y"]
     * </p>
     * <p>
     * If the input is null or empty, or if any feature has no values, an empty list is returned.
     * </p>
     * <p>
     * Note that the number of tuples generated scales multiplicatively, and using too many features with multiple values
     * will explode the number of lookup tuples exponentially. This will cause increased CPU and latency for each request's evaluation.
     * </p>
     * @param modelFeatures the list of model features derived from the OpenRTB request based on the model definition
     * @return list of all tuple permutations of the input features' values
     */
    List<String> buildKeys(List<ModelFeature> modelFeatures) {
        if (modelFeatures == null || modelFeatures.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // Start with the first feature's values
            List<String> result = new ArrayList<>(modelFeatures.get(0).getValues());

            // For each subsequent feature, create permutations with existing results
            for (int i = 1; i < modelFeatures.size(); i++) {
                result = addFeatureToPermutations(modelFeatures, i, result);
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to build key tuples", e);
            return Collections.emptyList();
        }
    }

    private List<String> addFeatureToPermutations(List<ModelFeature> modelFeatures, int i, List<String> result) {
        List<String> currentFeatureValues = modelFeatures.get(i).getValues();
        List<String> newPermutations = new ArrayList<>(result.size() * currentFeatureValues.size());

        // Create permutations by combining each existing result with each new value
        for (String existingKey : result) {
            for (String newValue : currentFeatureValues) {
                newPermutations.add(existingKey + KEY_DELIMITER + newValue);
            }
        }
        return newPermutations;
    }
}
