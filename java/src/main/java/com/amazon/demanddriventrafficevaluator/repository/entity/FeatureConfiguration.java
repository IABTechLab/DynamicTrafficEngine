// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.entity;

import com.amazon.demanddriventrafficevaluator.modelfeature.FeatureTransformerName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class FeatureConfiguration {

    @JsonProperty("name")
    private String name;

    @JsonProperty("fields")
    private List<String> fields;

    @JsonProperty("transformation")
    private List<FeatureTransformerName> transformations;

    @JsonProperty("mapping")
    private Map<String, String> mapping;

    @JsonProperty("mappingDefaultValue")
    private String mappingDefaultValue;
}
