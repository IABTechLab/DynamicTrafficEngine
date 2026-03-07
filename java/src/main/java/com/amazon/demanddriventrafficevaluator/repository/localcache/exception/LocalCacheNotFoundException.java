// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.localcache.exception;

public class LocalCacheNotFoundException extends Exception {

    public LocalCacheNotFoundException(String message) {
        super(message);
    }

    public LocalCacheNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public LocalCacheNotFoundException(Throwable cause) {
        super(cause);
    }
}
