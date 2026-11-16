// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.loader.model;

import com.amazon.demanddriventrafficevaluator.repository.dao.ConditionalGetResult;
import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelValueType;
import com.amazon.demanddriventrafficevaluator.repository.entity.S3PathMode;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BloomFilterModelLoaderTest {

    @Mock
    private Dao<String, String> mockFileIdentifierCacheDao;
    @Mock
    private Dao<String, BloomFilter<String>> mockBloomFilterDao;
    // CALLS_REAL_METHODS: the default Dao.getIfChanged delegates to the stubbed get().
    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private Dao<String, InputStream> mockFileDao;

    private BloomFilterModelLoader loader;

    @BeforeEach
    void setUp() {
        loader = new BloomFilterModelLoader(mockFileIdentifierCacheDao, mockBloomFilterDao, mockFileDao);
    }

    // ========================================================================
    // Property 1: DYNAMIC S3 path format
    // Validates: Requirements 1.7, 8.6
    // ========================================================================

    @Nested
    class DynamicS3PathFormatProperty {

        /**
         * **Validates: Requirements 1.7, 8.6**
         * <p>
         * Property 1: DYNAMIC S3 path format
         * For any vendor string and s3ObjectKey string, when the S3PathMode is DYNAMIC,
         * getS3ObjectKey() SHALL return a string matching the pattern
         * {vendor}/{YYYY-MM-DD}/{HH}/{s3ObjectKey}.
         */
        @ParameterizedTest
        @MethodSource("dynamicPathInputs")
        void dynamicPathShouldMatchVendorDateHourKeyPattern(String vendor, String s3ObjectKey) {
            // Arrange
            ModelResultLoaderInput input = new ModelResultLoaderInput(
                    "bucket", s3ObjectKey, vendor, "model-id", ModelValueType.LowValue, S3PathMode.DYNAMIC);

            Clock fixedClock = Clock.fixed(Instant.parse("2024-06-15T09:30:00Z"), ZoneId.of("UTC"));
            try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class)) {
                mockedInstant.when(Instant::now).thenReturn(fixedClock.instant());

                // Act
                String result = loader.getS3ObjectKey(input);

                // Assert
                String expected = vendor + "/2024-06-15/09/" + s3ObjectKey;
                assertEquals(expected, result);
                assertTrue(result.matches(".+/\\d{4}-\\d{2}-\\d{2}/\\d{2}/.+"),
                        "Path should match {vendor}/{YYYY-MM-DD}/{HH}/{s3ObjectKey} pattern: " + result);
            }
        }

        static Stream<Arguments> dynamicPathInputs() {
            return Stream.of(
                    Arguments.of("adsp", "model_v1.bf"),
                    Arguments.of("vendor-with-dashes", "path/to/model.bloom"),
                    Arguments.of("UPPERCASE_VENDOR", "simple_key"),
                    Arguments.of("v1", "a"),
                    Arguments.of("my.vendor", "models/inv/v2.bin")
            );
        }
    }

    // ========================================================================
    // Property 2: STATIC S3 path identity
    // Validates: Requirements 1.8, 8.7
    // ========================================================================

    @Nested
    class StaticS3PathIdentityProperty {

        /**
         * **Validates: Requirements 1.8, 8.7**
         * <p>
         * Property 2: STATIC S3 path format
         * For any s3ObjectKey string and vendor, when the S3PathMode is STATIC,
         * getS3ObjectKey() SHALL return {vendor}/models/{s3ObjectKey}.bloom.
         */
        @ParameterizedTest
        @MethodSource("staticPathInputs")
        void staticPathShouldReturnVendorModelsKeyBloom(String s3ObjectKey, String vendor, String expected) {
            // Arrange
            ModelResultLoaderInput input = new ModelResultLoaderInput(
                    "bucket", s3ObjectKey, vendor, "model-id", ModelValueType.LowValue, S3PathMode.STATIC);

            // Act
            String result = loader.getS3ObjectKey(input);

            // Assert
            assertEquals(expected, result,
                    "STATIC path should return {vendor}/models/{s3ObjectKey}.bloom");
        }

        static Stream<Arguments> staticPathInputs() {
            return Stream.of(
                    Arguments.of("adsp_rsp_v1", "test_ssp", "test_ssp/models/adsp_rsp_v1.bloom"),
                    Arguments.of("model_v1", "vendor-a", "vendor-a/models/model_v1.bloom"),
                    Arguments.of("adsp_inv_v1", "my_ssp", "my_ssp/models/adsp_inv_v1.bloom"),
                    Arguments.of("simple", "v", "v/models/simple.bloom"),
                    Arguments.of("a.b.c", "ssp", "ssp/models/a.b.c.bloom"),
                    Arguments.of("model-with-dashes", "ssp_id", "ssp_id/models/model-with-dashes.bloom")
            );
        }
    }

    // ========================================================================
    // Unit tests for BloomFilterModelLoader
    // Requirements: 1.2, 1.3, 1.4, 1.5
    // ========================================================================

    @Nested
    class LoadBehavior {

        @Test
        void testLoad_SuccessfulWithNewETag() throws Exception {
            // Arrange
            ModelResultLoaderInput input = new ModelResultLoaderInput(
                    "testBucket", "testKey", "testVendor", "testModel",
                    ModelValueType.LowValue, S3PathMode.STATIC);

            @SuppressWarnings("UnstableApiUsage")
            BloomFilter<String> originalFilter = BloomFilter.create(
                    Funnels.stringFunnel(StandardCharsets.UTF_8), 100, 0.01);
            originalFilter.put("test-element");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            originalFilter.writeTo(baos);
            byte[] serializedFilter = baos.toByteArray();

            InputStream mockInputStream = new ByteArrayInputStream(serializedFilter);
            InputStream mockResponseInputStream = new ResponseInputStream<>(
                    GetObjectResponse.builder().eTag("newETag").build(), mockInputStream);

            when(mockFileDao.get("testBucket", "testVendor/models/testKey.bloom")).thenReturn(Optional.of(mockResponseInputStream));
            when(mockFileIdentifierCacheDao.get(anyString(), anyString())).thenReturn(Optional.empty());

            // Act
            boolean result = loader.load(input);

            // Assert
            assertTrue(result);
            verify(mockBloomFilterDao).put(eq("testModel"), eq("testModel"), org.mockito.ArgumentMatchers.any());
            verify(mockFileIdentifierCacheDao).put("model-results-identifier", "testModel", "newETag");
        }

        @Test
        void testLoad_SkipsWhenETagMatches() {
            // Arrange
            ModelResultLoaderInput input = new ModelResultLoaderInput(
                    "testBucket", "testKey", "testVendor", "testModel",
                    ModelValueType.LowValue, S3PathMode.STATIC);

            @SuppressWarnings("unchecked")
            ResponseInputStream<GetObjectResponse> mockResponseInputStream = mock(ResponseInputStream.class);
            when(mockResponseInputStream.response()).thenReturn(
                    GetObjectResponse.builder().eTag("sameETag").build());
            when(mockFileDao.get(anyString(), anyString())).thenReturn(Optional.of(mockResponseInputStream));
            when(mockFileIdentifierCacheDao.get(anyString(), anyString())).thenReturn(Optional.of("sameETag"));

            // Act
            boolean result = loader.load(input);

            // Assert
            assertFalse(result);
            verify(mockBloomFilterDao, never()).put(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        }

        @Test
        void testLoad_NotModified_SkipsWithoutDownloading() {
            // Arrange
            ModelResultLoaderInput input = new ModelResultLoaderInput(
                    "testBucket", "testKey", "testVendor", "testModel",
                    ModelValueType.LowValue, S3PathMode.STATIC);
            when(mockFileIdentifierCacheDao.get("model-results-identifier", "testModel"))
                    .thenReturn(Optional.of("sameETag"));
            doReturn(ConditionalGetResult.notModified()).when(mockFileDao)
                    .getIfChanged("testBucket", "testVendor/models/testKey.bloom", "sameETag");

            // Act
            boolean result = loader.load(input);

            // Assert
            assertFalse(result);
            verify(mockFileDao, never()).get(anyString(), anyString());
            verify(mockBloomFilterDao, never()).put(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
            verify(mockFileIdentifierCacheDao, never()).put(anyString(), anyString(), anyString());
        }

        @Test
        void testLoad_FileNotFound_ReturnsFalse() {
            // Arrange
            ModelResultLoaderInput input = new ModelResultLoaderInput(
                    "testBucket", "testKey", "testVendor", "testModel",
                    ModelValueType.LowValue, S3PathMode.STATIC);

            when(mockFileDao.get(anyString(), anyString())).thenReturn(Optional.empty());

            // Act
            boolean result = loader.load(input);

            // Assert
            assertFalse(result);
            verify(mockBloomFilterDao, never()).put(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
            verify(mockFileIdentifierCacheDao, never()).put(anyString(), anyString(), anyString());
        }

        @Test
        void testLoad_DeserializationFailure_ReturnsFalse() {
            // Arrange
            ModelResultLoaderInput input = new ModelResultLoaderInput(
                    "testBucket", "testKey", "testVendor", "testModel",
                    ModelValueType.LowValue, S3PathMode.STATIC);

            // Provide invalid data that cannot be deserialized as a BloomFilter
            byte[] invalidData = "this is not a valid bloom filter".getBytes(StandardCharsets.UTF_8);
            InputStream mockInputStream = new ByteArrayInputStream(invalidData);
            InputStream mockResponseInputStream = new ResponseInputStream<>(
                    GetObjectResponse.builder().eTag("newETag").build(), mockInputStream);

            when(mockFileDao.get(anyString(), anyString())).thenReturn(Optional.of(mockResponseInputStream));
            when(mockFileIdentifierCacheDao.get(anyString(), anyString())).thenReturn(Optional.empty());

            // Act
            boolean result = loader.load(input);

            // Assert
            assertFalse(result);
            verify(mockBloomFilterDao, never()).put(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        }

        @Test
        void testLoad_DeserializationFailure_RetainsExistingState() {
            // Arrange
            ModelResultLoaderInput input = new ModelResultLoaderInput(
                    "testBucket", "testKey", "testVendor", "testModel",
                    ModelValueType.LowValue, S3PathMode.STATIC);

            byte[] invalidData = "corrupted bloom filter data".getBytes(StandardCharsets.UTF_8);
            InputStream mockInputStream = new ByteArrayInputStream(invalidData);
            InputStream mockResponseInputStream = new ResponseInputStream<>(
                    GetObjectResponse.builder().eTag("newETag").build(), mockInputStream);

            when(mockFileDao.get(anyString(), anyString())).thenReturn(Optional.of(mockResponseInputStream));
            when(mockFileIdentifierCacheDao.get(anyString(), anyString())).thenReturn(Optional.empty());

            // Act
            boolean result = loader.load(input);

            // Assert - existing state should not be modified
            assertFalse(result);
            verify(mockBloomFilterDao, never()).put(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
            verify(mockBloomFilterDao, never()).clear(anyString());
        }

        @Test
        void testLoad_DynamicPath_BuildsCorrectKey() throws Exception {
            // Arrange
            ModelResultLoaderInput input = new ModelResultLoaderInput(
                    "testBucket", "testKey", "testVendor", "testModel",
                    ModelValueType.LowValue, S3PathMode.DYNAMIC);

            @SuppressWarnings("UnstableApiUsage")
            BloomFilter<String> filter = BloomFilter.create(
                    Funnels.stringFunnel(StandardCharsets.UTF_8), 100, 0.01);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            filter.writeTo(baos);
            byte[] serializedFilter = baos.toByteArray();

            String expectedKey = "testVendor/2023-05-20/10/testKey";
            Clock fixedClock = Clock.fixed(Instant.parse("2023-05-20T10:15:30Z"), ZoneId.of("UTC"));

            try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class)) {
                mockedInstant.when(Instant::now).thenReturn(fixedClock.instant());

                InputStream mockInputStream = new ByteArrayInputStream(serializedFilter);
                InputStream mockResponseInputStream = new ResponseInputStream<>(
                        GetObjectResponse.builder().eTag("eTag").build(), mockInputStream);

                when(mockFileDao.get("testBucket", expectedKey)).thenReturn(Optional.of(mockResponseInputStream));
                when(mockFileIdentifierCacheDao.get(anyString(), anyString())).thenReturn(Optional.empty());

                // Act
                boolean result = loader.load(input);

                // Assert
                assertTrue(result);
                verify(mockFileDao).get("testBucket", expectedKey);
            }
        }
    }
}
