// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.localcache.removalListener;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import lombok.extern.log4j.Log4j2;

/**
 * A removal listener for Guava caches that logs removal events.
 * <p>
 * This class implements Guava's RemovalListener interface and provides logging
 * functionality for cache entry removal events. It logs the cause of removal
 * for each cache entry that is removed.
 * </p>
 *
 * @param <K> The type of keys in the cache
 * @param <V> The type of values in the cache
 */
@Log4j2
public class GuavaLocalCacheRemovalListenerOnLog<K, V> implements RemovalListener<K, V> {

    public GuavaLocalCacheRemovalListenerOnLog() {
    }

    @Override
    public void onRemoval(RemovalNotification<K, V> removalNotification) {
        log.debug("localCacheCount.{}", this.removalCauseToString(removalNotification.getCause()));
    }

    /**
     * Converts a RemovalCause enum to a string representation.
     * <p>
     * This method is marked as @VisibleForTesting to indicate that it's
     * primarily used for testing purposes but may also be used internally.
     * </p>
     *
     * @param removalCause The RemovalCause enum to convert
     * @return A string representation of the removal cause
     */
    @VisibleForTesting
    protected String removalCauseToString(RemovalCause removalCause) {
        return switch (removalCause) {
            case COLLECTED -> "collected";
            case EXPIRED -> "expired";
            case SIZE -> "size";
            case REPLACED -> "replaced";
            case EXPLICIT -> "explicit";
        };
    }
}
