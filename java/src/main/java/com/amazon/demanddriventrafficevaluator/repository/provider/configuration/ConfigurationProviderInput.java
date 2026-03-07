// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.provider.configuration;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ConfigurationProviderInput {
    private final String daoIdentifier;
    private final String sspIdentifier;
    private final String type;
    private final String key;
    private final String version;
}
