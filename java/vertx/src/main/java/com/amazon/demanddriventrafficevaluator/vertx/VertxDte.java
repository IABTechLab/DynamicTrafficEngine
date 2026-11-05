// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.vertx;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluator;
import com.amazon.demanddriventrafficevaluator.factory.BidRequestEvaluatorFactory;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DTE on Vert.x: the factory's scheduled tasks (the feature registry setup, the model loads and every refresh) run on
 * a Vert.x worker pool through {@link VertxScheduledExecutorService}, and the evaluator is called inline on the event
 * loop. {@code init()} blocks one thread of that pool while it loads the configurations on threads of its own.
 * <pre>{@code
 * VertxDte.create(vertx, options).onSuccess(dte -> {
 *     // configurations loaded, model loads scheduled; report dte.isReady() from a readiness probe.
 *     // on the event loop, per request:
 *     dte.evaluator().evaluate(input);
 * });
 * }</pre>
 */
public final class VertxDte {

    private final VertxScheduledExecutorService executor;
    private final BidRequestEvaluatorFactory factory;
    private final BidRequestEvaluator evaluator;
    private Future<Void> closeFuture;

    private VertxDte(VertxScheduledExecutorService executor, BidRequestEvaluatorFactory factory) {
        this.executor = executor;
        this.factory = factory;
        this.evaluator = factory.getEvaluator();
    }

    /**
     * Creates the factory and runs {@code getTaskInitializer().init()} on the worker pool. Safe to call on an event
     * loop. When the returned future succeeds, the model and experiment configurations are loaded and the periodic
     * refresh is scheduled; the model results are not loaded yet. Each model loads first after a random delay of up
     * to one refresh period (300 s by default), and until then evaluations return that model's no-match value:
     * a {@code LowValue} model forwards (fail open) and a {@code HighValue} model scores the request low value.
     * Use {@link #isReady()} to know when every model has had its first load.
     * <p>
     * The worker pool and the timers belong to the returned instance, not to the calling context: they are created
     * off any Vert.x context, so undeploying the verticle that called this method does not stop the refreshes. Only
     * {@link #close()} releases them, so whoever receives the instance must close it, even if the calling verticle
     * is undeployed before the future completes. The returned future's handlers run on the calling context.
     * </p>
     * <p>
     * The future fails if the factory cannot be built or {@code init()} throws, for example because a configuration
     * cannot be loaded or both stages together do not complete within {@code task.overall.execution-timeout}; the
     * factory is closed and every timer and the worker pool are released. To retry, call this method again: it
     * builds a new factory and starts over. The S3 client and the loaded data are JVM-wide in DTE: a retry
     * with other credentials or another region in the same JVM still uses the first S3 client.
     * </p>
     */
    public static Future<VertxDte> create(Vertx vertx, VertxDteOptions options) {
        // Read the options once, here: the caller may change or reuse the object while the worker runs.
        String supplierName;
        String region;
        String bucket;
        AwsCredentialsProvider credentialsProvider;
        String poolName;
        int poolSize;
        long maxExecuteTime;
        TimeUnit maxExecuteTimeUnit;
        Context caller;
        try {
            Objects.requireNonNull(vertx, "vertx");
            supplierName = Objects.requireNonNull(options.getSupplierName(), "supplierName");
            bucket = Objects.requireNonNull(options.getBucket(), "bucket");
            credentialsProvider = Objects.requireNonNull(options.getCredentialsProvider(), "credentialsProvider");
            region = Objects.requireNonNull(options.getRegion(), "region");
            poolName = Objects.requireNonNull(options.getWorkerPoolName(), "workerPoolName");
            poolSize = options.getWorkerPoolSize();
            maxExecuteTime = options.getMaxWorkerExecuteTime();
            maxExecuteTimeUnit = Objects.requireNonNull(options.getMaxWorkerExecuteTimeUnit(), "maxWorkerExecuteTimeUnit");
            if (poolSize < 1) {
                throw new IllegalArgumentException("workerPoolSize must be at least 1: " + poolSize);
            }
            caller = vertx.getOrCreateContext();
        } catch (RuntimeException e) {
            return Future.failedFuture(e);
        }
        Promise<VertxDte> result = Promise.promise();
        // Vert.x ties a shared worker executor, and timers, to the context that creates them, and releases them when
        // that context's verticle is undeployed. Start from a thread with no Vert.x context, so that only close()
        // releases them.
        Thread starter = new Thread(() -> {
            Future<VertxDte> started;
            try {
                started = start(vertx, poolName, poolSize, maxExecuteTime, maxExecuteTimeUnit, supplierName,
                        credentialsProvider, region, bucket);
            } catch (Throwable e) {
                // for example a linkage error from an unsupported Vert.x version: fail the future, never leave it pending
                started = Future.failedFuture(e);
            }
            started.onComplete(done -> caller.runOnContext(ignored -> {
                if (done.succeeded()) {
                    result.complete(done.result());
                } else {
                    result.fail(done.cause());
                }
            }));
        }, "dte-vertx-create");
        starter.setDaemon(true);
        starter.start();
        return result.future();
    }

    // Runs on a thread with no Vert.x context; see create().
    private static Future<VertxDte> start(Vertx vertx, String poolName, int poolSize, long maxExecuteTime,
                                          TimeUnit maxExecuteTimeUnit, String supplierName,
                                          AwsCredentialsProvider credentialsProvider, String region, String bucket) {
        VertxScheduledExecutorService executor;
        try {
            executor = new VertxScheduledExecutorService(vertx, poolName, poolSize, maxExecuteTime, maxExecuteTimeUnit);
        } catch (RuntimeException e) {
            return Future.failedFuture(e);
        }
        AtomicReference<BidRequestEvaluatorFactory> created = new AtomicReference<>();
        Future<VertxDte> initialized;
        try {
            // Unordered: an ordered run would hold its context's ordered blocking queue for all of init().
            initialized = executor.worker().executeBlocking(() -> {
                // Building the factory also builds the S3 client, so it runs on the worker too.
                BidRequestEvaluatorFactory factory = BidRequestEvaluatorFactory.create(supplierName,
                        credentialsProvider, region, bucket, executor);
                created.set(factory);
                factory.getTaskInitializer().init();
                return new VertxDte(executor, factory);
            }, false);
        } catch (RuntimeException e) {
            // for example a Vert.x that is closing: release the worker created above
            initialized = Future.failedFuture(e);
        }
        return initialized.recover(failure -> release(executor, created.get())
                .transform(ignored -> Future.failedFuture(failure)));
    }

    /**
     * Returns the evaluator. Call {@code evaluate} inline on the event loop; it does no I/O.
     */
    public BidRequestEvaluator evaluator() {
        return evaluator;
    }

    /**
     * Delegates to {@link BidRequestEvaluatorFactory#isReady()}: the model-readiness signal. It turns true once the
     * feature registries are populated and every model-result and bloom-filter loading task has finished its first
     * run, whatever the outcome (a missing file or an S3 error still counts), which can take up to one refresh period
     * (300 s by default) plus the model load time after {@link #create} succeeds. Once true it stays true until {@link #close()}. Until it
     * first returns true, a call can list the feature providers on the classpath, which reads jar files: poll it from
     * a readiness probe rather than per request, and from {@code executeBlocking} rather than on an event loop.
     */
    public boolean isReady() {
        return factory.isReady();
    }

    // Tests only.
    VertxScheduledExecutorService executor() {
        return executor;
    }

    /**
     * Cancels DTE's timers, closes the factory and then this instance's use of the shared worker pool. Never
     * closes {@link Vertx} and never blocks. A load still running is allowed to finish, for up to 60 s, before the
     * pool is released, because Vert.x interrupts the pool's threads when no other user of the pool name is left.
     * The returned future completes once the pool is released. Idempotent: every call returns the same future.
     */
    public synchronized Future<Void> close() {
        if (closeFuture == null) {
            closeFuture = release(executor, factory);
        }
        return closeFuture;
    }

    private static Future<Void> release(VertxScheduledExecutorService executor, BidRequestEvaluatorFactory factory) {
        Future<Void> shutdown = executor.shutdownAndClose();
        if (factory != null) {
            factory.close();
        }
        return shutdown;
    }
}
