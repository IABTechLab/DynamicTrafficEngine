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
        return new DefaultBidRequestEvaluatorFactory(supplierName, credentialsProvider, region, bucket);
    }

    public static BidRequestEvaluatorFactory create(String supplierName, AwsCredentialsProvider credentialsProvider, String region, String bucket, ScheduledThreadPoolExecutor executor) {
        return new DefaultBidRequestEvaluatorFactory(supplierName, credentialsProvider, region, bucket, executor);
    }

    public abstract TaskInitializer getTaskInitializer();

    public abstract BidRequestEvaluator getEvaluator();

    /**
     * Tells whether the evaluator has had its first chance to load the data it needs to make recommendations.
     * <p>
     * True once all of the following hold:
     * </p>
     * <ul>
     *   <li>{@code init()} has completed normally on a {@link TaskInitializer} obtained from this factory, so the
     *       model and experiment configurations are loaded;</li>
     *   <li>every feature extractor and transformer is registered, which happens within about one second of
     *       {@code init()} starting, longer if the executor has no free thread;</li>
     *   <li>every model-result and bloom-filter loading task created for that {@link TaskInitializer} has finished
     *       its first periodic run.</li>
     * </ul>
     * <p>
     * The first periodic run starts after a random delay of up to one refresh period (300 s by default; the
     * longest of the configured model-result periods), so this can take up to one refresh period plus the model
     * load time after {@code init()} returns, longer if the executor has no free thread. Once true it stays true;
     * later refresh failures do not reset it. It stays false if {@code init()} throws, and while a feature
     * extractor or transformer provider has not been registered, for example because it failed to load.
     * </p>
     * <p>
     * A first run counts as finished whatever its outcome: a model file that is missing, an S3 error or a loader
     * exception does not block readiness.
     * </p>
     * <p>
     * Until a model's results are loaded, evaluations still run: that model returns its no-match value, which
     * forwards for a {@code LowValue} model (1.0) and marks the request low value for a {@code HighValue} model
     * (0.0), and the aggregation combines it with the other models as usual. With only {@code LowValue} models,
     * evaluations therefore fail open (forward) until the models load.
     * </p>
     * <p>
     * This default implementation throws; {@link DefaultBidRequestEvaluatorFactory} overrides it.
     * </p>
     *
     * @return true when the conditions above hold.
     * @throws UnsupportedOperationException if the factory does not track readiness.
     */
    public boolean isReady() {
        throw new UnsupportedOperationException("isReady is not implemented by " + getClass().getName());
    }

}
