// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.loader.model;

import com.amazon.demanddriventrafficevaluator.repository.entity.ModelValueType;
import com.amazon.demanddriventrafficevaluator.repository.loader.LoaderInput;
import lombok.Getter;

@Getter
public class ModelResultLoaderInput extends LoaderInput {

    private final String modelIdentifier;
    private final ModelValueType modelType;

    public ModelResultLoaderInput(String s3Bucket, String s3ObjectKey, String vendor, String modelIdentifier, ModelValueType modelType) {
        super(s3Bucket, s3ObjectKey, "model-result", vendor);
        this.modelIdentifier = modelIdentifier;
        this.modelType = modelType;
    }
}
