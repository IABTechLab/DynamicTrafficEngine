// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluator;
import com.amazon.demanddriventrafficevaluator.task.TaskInitializer;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * An abstract factory for creating BidRequestEvaluator instances and associated components.
 * <p>
 * This class provides static factory methods to create specific implementations of
 * BidRequestEvaluatorFactory based on the provided parameters. It also defines abstract
 * methods that concrete implementations must provide to access the TaskInitializer
 * and BidRequestEvaluator.
 * </p>
 */
public abstract class BidRequestEvaluatorFactory {

    public static BidRequestEvaluatorFactory create(String supplierName, AwsCredentialsProvider credentialsProvider, String region, String bucket) {
        return new BidRequestEvaluatorOnRuleBasedModelFactory(supplierName, credentialsProvider, region, bucket);
    }

    public static BidRequestEvaluatorFactory create(String supplierName, AwsCredentialsProvider credentialsProvider, String region, String bucket, ScheduledThreadPoolExecutor executor) {
        return new BidRequestEvaluatorOnRuleBasedModelFactory(supplierName, credentialsProvider, region, bucket, executor);
    }

    public abstract TaskInitializer getTaskInitializer();

    public abstract BidRequestEvaluator getEvaluator();

}
