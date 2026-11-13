// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.loader;

import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao;
import com.amazon.demanddriventrafficevaluator.repository.dao.S3ObjectDao;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelValueType;
import com.amazon.demanddriventrafficevaluator.repository.entity.S3PathMode;
import com.amazon.demanddriventrafficevaluator.repository.loader.configuration.ConfigurationLoaderInput;
import com.amazon.demanddriventrafficevaluator.repository.loader.configuration.DefaultConfigurationLoader;
import com.amazon.demanddriventrafficevaluator.repository.loader.model.BloomFilterModelLoader;
import com.amazon.demanddriventrafficevaluator.repository.loader.model.ModelResultLoaderInput;
import com.amazon.demanddriventrafficevaluator.repository.loader.model.RuleBasedModelResultLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives each S3-backed loader through the real {@link S3ObjectDao} with a stubbed {@link S3Client}
 * to check that a refresh of an unchanged file transfers no object body (issue #27).
 */
@ExtendWith(MockitoExtension.class)
class UnchangedS3FileRefreshTest {

    private static final String BUCKET = "test-bucket";
    private static final String VENDOR = "test-ssp";
    private static final String MODEL_IDENTIFIER = "test-model";

    @Mock
    private S3Client s3Client;

    private InMemoryDao<String> fileIdentifierCacheDao;
    private S3ObjectDao fileDao;

    @BeforeEach
    void setUp() {
        fileIdentifierCacheDao = new InMemoryDao<>();
        fileDao = new S3ObjectDao(s3Client);
    }

    @Test
    void bloomFilterRefreshWithUnchangedETagDownloadsNothing() {
        byte[] body = serializedBloomFilter("element");
        stubS3Object("etag-1", body);
        InMemoryDao<BloomFilter<String>> bloomFilterDao = new InMemoryDao<>();
        BloomFilterModelLoader loader = new BloomFilterModelLoader(fileIdentifierCacheDao, bloomFilterDao, fileDao);
        ModelResultLoaderInput input = new ModelResultLoaderInput(
                BUCKET, "test-model", VENDOR, MODEL_IDENTIFIER, ModelValueType.LowValue, S3PathMode.STATIC);

        assertTrue(loader.load(input));
        BloomFilter<String> loaded = bloomFilterDao.get(MODEL_IDENTIFIER, MODEL_IDENTIFIER).orElseThrow();

        assertFalse(loader.load(input));

        verify(s3Client, times(1)).getObject(ArgumentMatchers.<GetObjectRequest>any());
        assertSame(loaded, bloomFilterDao.get(MODEL_IDENTIFIER, MODEL_IDENTIFIER).orElseThrow());
    }

    @Test
    void ruleBasedModelResultRefreshWithUnchangedETagDownloadsNothing() {
        stubS3Object("etag-1", "key-a\nkey-b\n".getBytes(StandardCharsets.UTF_8));
        InMemoryDao<Double> modelResultsCacheDao = new InMemoryDao<>();
        RuleBasedModelResultLoader loader =
                new RuleBasedModelResultLoader(fileIdentifierCacheDao, modelResultsCacheDao, fileDao);
        ModelResultLoaderInput input = new ModelResultLoaderInput(
                BUCKET, "test-model.csv", VENDOR, MODEL_IDENTIFIER, ModelValueType.LowValue);

        assertTrue(loader.load(input));
        assertFalse(loader.load(input));

        verify(s3Client, times(1)).getObject(ArgumentMatchers.<GetObjectRequest>any());
        assertEquals(2, modelResultsCacheDao.size(MODEL_IDENTIFIER));
    }

    @Test
    void configurationRefreshWithUnchangedETagDownloadsNothing() throws IOException {
        stubS3Object("etag-1", readResource("/test/ModelConfiguration.json"));
        @SuppressWarnings("unchecked")
        LocalCacheDao<String, ModelConfiguration> configurationCacheDao = org.mockito.Mockito.mock(LocalCacheDao.class);
        DefaultConfigurationLoader<ModelConfiguration> loader = new DefaultConfigurationLoader<>(
                fileIdentifierCacheDao, fileDao, configurationCacheDao,
                LocalCacheDao.CACHE_KEY_MODEL_CONFIGURATION, ModelConfiguration.class, new ObjectMapper());
        ConfigurationLoaderInput input = new ConfigurationLoaderInput(BUCKET, "config.json", VENDOR, "model");

        assertTrue(loader.load(input));
        assertFalse(loader.load(input));

        verify(s3Client, times(1)).getObject(ArgumentMatchers.<GetObjectRequest>any());
        verify(configurationCacheDao, times(1)).put(anyString(), eq(LocalCacheDao.CACHE_KEY_MODEL_CONFIGURATION), any());
    }

    @Test
    void refreshWithChangedETagDownloadsAndReloads() {
        byte[] body = serializedBloomFilter("element");
        stubS3Object("etag-1", body);
        InMemoryDao<BloomFilter<String>> bloomFilterDao = new InMemoryDao<>();
        BloomFilterModelLoader loader = new BloomFilterModelLoader(fileIdentifierCacheDao, bloomFilterDao, fileDao);
        ModelResultLoaderInput input = new ModelResultLoaderInput(
                BUCKET, "test-model", VENDOR, MODEL_IDENTIFIER, ModelValueType.LowValue, S3PathMode.STATIC);
        assertTrue(loader.load(input));

        stubS3Object("etag-2", body);

        assertTrue(loader.load(input));
        verify(s3Client, times(2)).getObject(ArgumentMatchers.<GetObjectRequest>any());
    }

    private void stubS3Object(String eTag, byte[] body) {
        when(s3Client.headObject(ArgumentMatchers.<HeadObjectRequest>any()))
                .thenReturn(HeadObjectResponse.builder().eTag(eTag).contentLength((long) body.length).build());
        when(s3Client.getObject(ArgumentMatchers.<GetObjectRequest>any()))
                .thenAnswer(invocation -> new ResponseInputStream<>(
                        GetObjectResponse.builder().eTag(eTag).contentLength((long) body.length).build(),
                        new ByteArrayInputStream(body)));
    }

    @SuppressWarnings("UnstableApiUsage")
    private static byte[] serializedBloomFilter(String element) {
        BloomFilter<String> bloomFilter = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), 100, 0.01);
        bloomFilter.put(element);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            bloomFilter.writeTo(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static byte[] readResource(String path) throws IOException {
        try (InputStream in = UnchangedS3FileRefreshTest.class.getResourceAsStream(path)) {
            return in.readAllBytes();
        }
    }

    // A private map per test, so cached ETags do not leak between tests through the shared cache registry.
    private static final class InMemoryDao<R> implements Dao<String, R> {
        private final Map<String, Map<String, R>> records = new HashMap<>();

        @Override
        public Optional<R> get(String identifier, String key) {
            return Optional.ofNullable(records.getOrDefault(identifier, Map.of()).get(key));
        }

        @Override
        public void put(String identifier, String key, R value) {
            records.computeIfAbsent(identifier, ignored -> new HashMap<>()).put(key, value);
        }

        @Override
        public void clear(String identifier) {
            records.remove(identifier);
        }

        int size(String identifier) {
            return records.getOrDefault(identifier, Map.of()).size();
        }
    }
}
