// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class TreatmentDefinition {
    @JsonProperty("treatmentCode")
    private String treatmentCode;

    @JsonProperty("weight")
    private int weight;

    @JsonProperty("idStart")
    private int idStart;

    @JsonProperty("idEnd")
    private int idEnd;
}
