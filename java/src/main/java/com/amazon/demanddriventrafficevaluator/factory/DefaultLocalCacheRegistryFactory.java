// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import com.amazon.demanddriventrafficevaluator.repository.localcache.builder.GuavaLocalCacheBuilderImpl;
import com.amazon.demanddriventrafficevaluator.repository.localcache.builder.LocalCacheBuilder;
import com.amazon.demanddriventrafficevaluator.repository.localcache.removalListener.GuavaLocalCacheRemovalListenerOnLog;
import com.google.common.cache.RemovalListener;
import software.amazon.awssdk.utils.ImmutableMap;

import java.util.Map;

import static com.amazon.demanddriventrafficevaluator.repository.localcache.LocalCacheRegistryImpl.DEFAULT_GUAVA_LOCAL_CACHE_BUILDER_VERSION;

public class DefaultLocalCacheRegistryFactory extends LocalCacheRegistryFactory {

    private static final LocalCacheRegistryFactory INSTANCE = new DefaultLocalCacheRegistryFactory();

    public static LocalCacheRegistryFactory getInstance() {
        return INSTANCE;
    }

    @Override
    Map<String, LocalCacheBuilder> getLocalCacheBuilderMap() {
        RemovalListener removalListener = new GuavaLocalCacheRemovalListenerOnLog<>();
        LocalCacheBuilder guavaLocalCacheBuilder = new GuavaLocalCacheBuilderImpl(removalListener);
        return ImmutableMap.of(DEFAULT_GUAVA_LOCAL_CACHE_BUILDER_VERSION, guavaLocalCacheBuilder);
    }
}
