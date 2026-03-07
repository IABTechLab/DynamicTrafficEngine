// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.loader.configuration;

import com.amazon.demanddriventrafficevaluator.repository.loader.LoaderInput;
import lombok.Getter;

@Getter
public class ConfigurationLoaderInput extends LoaderInput {
    private final String configurationType;

    public ConfigurationLoaderInput(String s3Bucket, String s3ObjectKey, String vendor, String configurationType) {
        super(s3Bucket, s3ObjectKey, "configuration", vendor);
        this.configurationType = configurationType;
    }
}
