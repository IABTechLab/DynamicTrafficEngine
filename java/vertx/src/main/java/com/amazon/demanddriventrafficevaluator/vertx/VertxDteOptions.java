// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.vertx;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.util.concurrent.TimeUnit;

/**
 * Options for {@link VertxDte#create}. supplierName, bucket, credentialsProvider and region are required
 * and mean the same as for {@code BidRequestEvaluatorFactory.create(...)}.
 */
public class VertxDteOptions {

    /**
     * Default name of the shared worker pool that runs DTE's loads. Instances using the same name share one pool,
     * sized by the first of them.
     */
    public static final String DEFAULT_WORKER_POOL_NAME = "dte-loader";
    /**
     * Default size of that pool: one thread for {@code init()}, which blocks while the configurations load, and one
     * for the scheduled tasks (the feature registry setup, the first model loads and the refreshes), so they do not
     * wait for {@code init()} to return.
     */
    public static final int DEFAULT_WORKER_POOL_SIZE = 2;
    /**
     * Default worker blocked-thread warning threshold, in minutes: above the 10-minute default of
     * {@code task.overall.execution-timeout}, which bounds both stages of {@code init()} together, the longest run on
     * the pool (a failing {@code init()} can wait up to 60 s more for an attempt in progress). Model loads are not part of
     * {@code init()}; each runs as a separate scheduled task. Raise it if you raise that timeout.
     */
    public static final long DEFAULT_MAX_WORKER_EXECUTE_TIME_MINUTES = 15L;

    private String supplierName;
    private String bucket;
    private AwsCredentialsProvider credentialsProvider;
    private String region;
    private String workerPoolName = DEFAULT_WORKER_POOL_NAME;
    private int workerPoolSize = DEFAULT_WORKER_POOL_SIZE;
    private long maxWorkerExecuteTime = DEFAULT_MAX_WORKER_EXECUTE_TIME_MINUTES;
    private TimeUnit maxWorkerExecuteTimeUnit = TimeUnit.MINUTES;

    public String getSupplierName() {
        return supplierName;
    }

    public VertxDteOptions setSupplierName(String supplierName) {
        this.supplierName = supplierName;
        return this;
    }

    public String getBucket() {
        return bucket;
    }

    public VertxDteOptions setBucket(String bucket) {
        this.bucket = bucket;
        return this;
    }

    public AwsCredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }

    public VertxDteOptions setCredentialsProvider(AwsCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
        return this;
    }

    public String getRegion() {
        return region;
    }

    public VertxDteOptions setRegion(String region) {
        this.region = region;
        return this;
    }

    public String getWorkerPoolName() {
        return workerPoolName;
    }

    public VertxDteOptions setWorkerPoolName(String workerPoolName) {
        this.workerPoolName = workerPoolName;
        return this;
    }

    public int getWorkerPoolSize() {
        return workerPoolSize;
    }

    public VertxDteOptions setWorkerPoolSize(int workerPoolSize) {
        this.workerPoolSize = workerPoolSize;
        return this;
    }

    public long getMaxWorkerExecuteTime() {
        return maxWorkerExecuteTime;
    }

    /**
     * Sets after how long a worker run triggers Vert.x's blocked-thread warning. Always pass the unit too.
     */
    public VertxDteOptions setMaxWorkerExecuteTime(long maxWorkerExecuteTime, TimeUnit unit) {
        this.maxWorkerExecuteTime = maxWorkerExecuteTime;
        this.maxWorkerExecuteTimeUnit = unit;
        return this;
    }

    public TimeUnit getMaxWorkerExecuteTimeUnit() {
        return maxWorkerExecuteTimeUnit;
    }
}
