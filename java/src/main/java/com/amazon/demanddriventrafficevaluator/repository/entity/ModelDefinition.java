// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.entity;

import com.amazon.demanddriventrafficevaluator.modelfeature.FeatureExtractorType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelDefinition {

    /**
     * Unique identifier for the model <dsp>_<name>_<version>
     */
    @JsonProperty("identifier")
    private String identifier;

    @JsonProperty("name")
    private String name;

    @JsonProperty("dsp")
    private String dsp;

    @JsonProperty("version")
    private String version;

    @JsonProperty("modelType")
    private ModelValueType type = ModelValueType.LowValue;

    @JsonProperty("featureExtractorType")
    private FeatureExtractorType featureExtractorType;

    @JsonProperty("features")
    private List<FeatureConfiguration> features;
}
