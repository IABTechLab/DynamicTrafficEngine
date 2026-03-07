// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.loader;

import lombok.Getter;

@Getter
public class LoaderInput {
    private final String s3Bucket;
    private final String s3ObjectKey;
    private final String type;
    private final String vendor;

    public LoaderInput(String s3Bucket, String s3ObjectKey, String type, String vendor) {
        this.s3Bucket = s3Bucket;
        this.s3ObjectKey = s3ObjectKey;
        this.type = type;
        this.vendor = vendor;
    }
}
