// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.localcache.exception;

public class LocalCacheCreationException extends Exception {

    public LocalCacheCreationException(String message) {
        super(message);
    }

    public LocalCacheCreationException(String message, Throwable cause) {
        super(message, cause);
    }

    public LocalCacheCreationException(Throwable cause) {
        super(cause);
    }
}
