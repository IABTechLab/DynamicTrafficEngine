// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.vertx;

import com.amazon.demanddriventrafficevaluator.factory.AWSServiceClientFactory;
import io.vertx.core.Context;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * S3 fake serving the core test fixtures. It records the thread of every call, so tests can check that no
 * S3 call runs on an event loop. Installed into the JVM-wide {@link AWSServiceClientFactory} singleton.
 */
final class FakeS3Client implements S3Client {

    static final String SSP = "vertx-test-ssp";
    static final String BUCKET = "vertx-test-bucket";

    private final List<String> callThreads = new CopyOnWriteArrayList<>();
    private final List<String> eventLoopCalls = new CopyOnWriteArrayList<>();
    private final String eTag = UUID.randomUUID().toString();
    private volatile boolean failModelResult;
    private volatile boolean failModelConfiguration;
    private volatile CountDownLatch modelResultGate;
    private volatile boolean gateInterrupted;
    private final CountDownLatch modelResultRequested = new CountDownLatch(1);

    static FakeS3Client install() throws ReflectiveOperationException {
        FakeS3Client fake = new FakeS3Client();
        setS3Client(fake);
        return fake;
    }

    static void uninstall() throws ReflectiveOperationException {
        setS3Client(null);
    }

    private static void setS3Client(S3Client client) throws ReflectiveOperationException {
        Field field = AWSServiceClientFactory.class.getDeclaredField("s3Client");
        field.setAccessible(true);
        field.set(AWSServiceClientFactory.getInstance(), client);
    }

    /** Thread names of all calls so far, in call order. */
    List<String> callThreads() {
        return List.copyOf(callThreads);
    }

    /** Calls that ran on an event-loop thread; must stay empty. */
    List<String> eventLoopCalls() {
        return List.copyOf(eventLoopCalls);
    }

    /** Makes reading the rule-based model result throw; the periodic model load then logs and retries next period. */
    void failModelResult() {
        failModelResult = true;
    }

    /** Makes reading the model configuration throw, which fails init(). */
    void failModelConfiguration() {
        failModelConfiguration = true;
    }

    /** Lets the model configuration load again after {@link #failModelConfiguration()}. */
    void restoreModelConfiguration() {
        failModelConfiguration = false;
    }

    /** Holds every read of the rule-based model result until {@link #releaseModelResult()}. */
    void holdModelResult() {
        modelResultGate = new CountDownLatch(1);
    }

    void releaseModelResult() {
        modelResultGate.countDown();
    }

    /** True once a held read of the model result was interrupted. */
    boolean gateInterrupted() {
        return gateInterrupted;
    }

    /** Waits until the rule-based model result has been requested at least once. */
    boolean awaitModelResultRequested(long timeout, TimeUnit unit) throws InterruptedException {
        return modelResultRequested.await(timeout, unit);
    }

    @Override
    public String serviceName() {
        return "s3";
    }

    @Override
    public void close() {
    }

    @Override
    public HeadObjectResponse headObject(HeadObjectRequest request) {
        record(request.key());
        return HeadObjectResponse.builder().contentLength(1024L).build();
    }

    @Override
    public ResponseInputStream<GetObjectResponse> getObject(GetObjectRequest request) {
        record(request.key());
        String key = request.key();
        InputStream stream;
        if ((SSP + "/configuration/model/config.json").equals(key)) {
            stream = failModelConfiguration ? new FailingInputStream() : resource("/test/ModelConfiguration.json");
        } else if ((SSP + "/configuration/experiment/config.json").equals(key)) {
            stream = resource("/test/ExperimentConfiguration.json");
        } else if (key.startsWith(SSP + "/") && key.endsWith("/adsp_low-value_v2.csv")) {
            modelResultRequested.countDown();
            awaitGate();
            stream = failModelResult ? new FailingInputStream() : resource("/test/ModelResult.csv");
        } else {
            throw NoSuchKeyException.builder().message("unknown key: " + key).build();
        }
        // A fresh eTag while failing, so a retry does not skip the read as an unchanged file.
        String tag = failModelResult || failModelConfiguration ? UUID.randomUUID().toString() : eTag;
        return new ResponseInputStream<>(GetObjectResponse.builder().eTag(tag).build(), stream);
    }

    private void awaitGate() {
        CountDownLatch gate = modelResultGate;
        if (gate == null) {
            return;
        }
        try {
            if (!gate.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("model result gate not released (test)");
            }
        } catch (InterruptedException e) {
            gateInterrupted = true;
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting for the model result gate (test)", e);
        }
    }

    private void record(String key) {
        String thread = Thread.currentThread().getName();
        callThreads.add(thread);
        if (Context.isOnEventLoopThread()) {
            eventLoopCalls.add(thread + " " + key);
        }
    }

    private static InputStream resource(String path) {
        return FakeS3Client.class.getResourceAsStream(path);
    }

    private static final class FailingInputStream extends InputStream {
        @Override
        public int read() {
            throw new IllegalStateException("model result read failed (test)");
        }
    }
}
