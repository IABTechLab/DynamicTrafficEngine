// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.localcache.exception;

public class LocalCacheRegistrationException extends Exception {

    public LocalCacheRegistrationException(String message) {
        super(message);
    }

    public LocalCacheRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }

    public LocalCacheRegistrationException(Throwable cause) {
        super(cause);
    }
}
