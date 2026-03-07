// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.localcache.builder;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class LocalCacheBuilderConfig {
    private String cacheName;
    private Integer maximumSize;
    private Integer expireAfterWriteSecs;
    private Integer expireAfterAccessSecs;
    private Integer refreshAfterWriteSecs;
    private String localCacheBuilderVersion;
    private Integer concurrencyLevel;
    private Boolean enableRecordStats;
    @Builder.Default
    private Boolean populateStatsPeriodically = false;
    @Builder.Default
    private Long populateStatsPeriodMillis = 60000L;
    @Builder.Default
    private Boolean populateByteStatsOnePod = false;
    @Builder.Default
    private Integer populateByteSizeSampleRate = 0;
    @Builder.Default
    private Boolean populateByteSizeStatsPeriodically = false;
    @Builder.Default
    private Integer populateByteSizeStatsPeriodSecs = 3600;
}
