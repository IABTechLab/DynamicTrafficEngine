// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@Data
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExperimentDefinition {
    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private String type;

    @JsonProperty("treatments")
    private List<TreatmentDefinition> treatmentDefinitions;

    @JsonProperty("salt")
    private String salt;

    @JsonProperty("startTimeUTC")
    private long startTimeUTC;

    @JsonProperty("endTimeUTC")
    private long endTimeUTC;

    @JsonProperty("allocationIdStart")
    private int allocationIdStart;

    @JsonProperty("allocationIdEnd")
    private int allocationIdEnd;

    @JsonProperty("hash")
    private boolean hashEnabled;
}
