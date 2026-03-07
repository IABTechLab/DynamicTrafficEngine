// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import lombok.NonNull;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * A singleton factory class for creating and managing AWS service clients.
 * <p>
 * This class provides a centralized way to create and access AWS service clients,
 * specifically the S3Client. It uses lazy initialization and double-checked locking
 * to ensure thread-safe creation of the S3Client instance.
 * </p>
 */
public class AWSServiceClientFactory {

    private static final AWSServiceClientFactory INSTANCE = new AWSServiceClientFactory();
    private volatile S3Client s3Client;

    private AWSServiceClientFactory() {
    }

    public static AWSServiceClientFactory getInstance() {
        return INSTANCE;
    }

    public S3Client getS3Client(@NonNull AwsCredentialsProvider awsCredentialsProvider, @NonNull String region) {
        if (s3Client == null) {
            synchronized (this) {
                if (s3Client == null) {
                    s3Client = S3Client.builder()
                            .region(Region.of(region))
                            .credentialsProvider(awsCredentialsProvider)
                            .crossRegionAccessEnabled(true)
                            .build();
                }
            }
        }
        return s3Client;
    }
}
