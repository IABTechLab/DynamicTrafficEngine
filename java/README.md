Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License").
You may not use this file except in compliance with the License.
You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

# Demand Driven Traffic Evaluator
 
This project is a Java library for evaluating demand-driven traffic based on rule-based models and experiments.
 
## Building the Package
 
To build the package, no need to have Gradle installed on your system. Follow these steps:
 
1. Get the repository:
  1. The repository is provided as the zip file through the S3 bucket, please downloaded and unzip it.
 
2. Navigate to the project directory:
 
   ```
   cd DemandDrivenTrafficEvaluator
   ```
 
3. Build the package using Gradle:
 
   ```
   ./gradlew clean build
   ```
 
Please look for the `Fat JAR created at: <the location to the Fat JAR>` in the console, and get the FAT JAR by following the provided location.

## Vert.x Module (optional)

The `vertx` subproject builds a separate jar, `DemandDrivenTrafficEvaluator-<version>-vertx.jar`. It runs DTE's
scheduled tasks on a Vert.x worker pool instead of DTE's own executor. Add `io.vertx:vertx-core` 4.5.x or 5.x
yourself; the core jar has no Vert.x dependency, and applications that do not use this jar are not affected.

### Initialization

```java
VertxDteOptions options = new VertxDteOptions()
        .setSupplierName(supplierName)
        .setBucket(bucket)
        .setCredentialsProvider(credentialsProvider)
        .setRegion(region);

// in a verticle: keep the future, and complete start() once DTE is created
private Future<VertxDte> dte;

public void start(Promise<Void> startPromise) {
    dte = VertxDte.create(vertx, options);
    dte.<Void>mapEmpty().onComplete(startPromise);
}

// per request, on the event loop, once started (evaluate() does no I/O):
dte.result().evaluator().evaluate(input);

// close it in stop(), also if create() is still running when the verticle is undeployed
public void stop(Promise<Void> stopPromise) {
    dte.transform(created -> created.succeeded() ? created.result().close() : Future.<Void>succeededFuture())
            .onComplete(stopPromise);
}
```

- `create` can be called on an event loop, including from a verticle's `start()`. It loads the configurations
  on the worker pool and completes its future once they are loaded and the refresh is scheduled.
- `isReady()` turns true once every model has had its first load, and false again after `close()`. Report it from
  a readiness probe.
- `close()` stops the refreshes and releases the worker pool. It never closes `Vertx`.

### Details and failure modes

- **Model loading.** The future does not wait for the model results. Each model loads first after a random delay of
  up to one refresh period (5 minutes by default). Until then a `LowValue` model forwards (fail open) and a
  `HighValue` model scores the request low value.
- **Readiness.** `isReady()` counts a failed first load (a missing file, an S3 error) as done, and once true it stays
  true until `close()`. Until it first returns true, a call can list the feature providers on the classpath, so poll it off the
  event loop (from `executeBlocking`).
- **Failed create.** The future fails if a configuration cannot be loaded or `init()` does not finish within
  `task.overall.execution-timeout` (one budget for both stages). The timers and the pool are then released. To
  retry, call `create` again; it starts over with a new factory. The S3 client and the loaded data are JVM-wide, so a
  retry in the same JVM keeps the first credentials and region.
- **Lifetime.** The worker pool and the timers belong to the `VertxDte` instance, not to the calling context:
  undeploying the verticle that called `create` does not stop the refreshes. Only `close()` releases them, so whoever
  receives the instance must close it, even if its verticle is undeployed before `create` completes.
- **Close.** `close()` never blocks. It lets a load in progress finish, for at most 60 s, before it releases the pool,
  so a model is never cut off half way.

To run the module's tests against Vert.x 5: `./gradlew :vertx:test -PvertxVersion=5.x.y`.
