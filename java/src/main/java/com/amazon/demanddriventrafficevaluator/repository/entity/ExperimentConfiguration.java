// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ExperimentConfiguration extends Configuration {

    @JsonProperty("experimentDefinitionByName")
    private Map<String, ExperimentDefinition> experimentDefinitionByName;

    @JsonProperty("modelToExperiment")
    private Map<String, String> modelToExperiment;
}
