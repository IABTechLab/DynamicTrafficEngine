// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.loader.model;

import com.amazon.demanddriventrafficevaluator.factory.DefaultLocalCacheRegistryFactory;
import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyDouble;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleBasedModelResultLoaderTest {

    @Mock
    private Dao<String, String> mockFileIdentifierCacheDao;
    @Spy
    private Dao<String, Double> spyModelResultsCacheDao = new LocalCacheDao<>(DefaultLocalCacheRegistryFactory.getInstance().getDefaultLocalCacheRegistrySingleton());
    @Mock
    private Dao<String, InputStream> mockFileDao;

    private RuleBasedModelResultLoader loader;

    @BeforeEach
    void setUp() {
        loader = new RuleBasedModelResultLoader(mockFileIdentifierCacheDao, spyModelResultsCacheDao, mockFileDao);
    }

    @Test
    void testLoad_Successful() {
        // Arrange
        ModelResultLoaderInput input = new ModelResultLoaderInput("testBucket", "testKey", "testVendor", "testModel", ModelValueType.LowValue);
        String fileKey = "testVendor/2023-05-20/10/testKey"; // Assuming current date and time
        String modelResult1 = "result1";
        String modelResult2 = "result2";
        InputStream mockInputStream = new ByteArrayInputStream((modelResult1 + "\n" + modelResult2).getBytes(StandardCharsets.UTF_8));

        Clock fixedClock = Clock.fixed(Instant.parse("2023-05-20T10:15:30Z"), ZoneId.of("UTC"));
        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class)) {
            mockedInstant.when(Instant::now).thenReturn(fixedClock.instant());
            InputStream mockResponseInputStream = new ResponseInputStream<>(GetObjectResponse.builder().eTag("eTag").build(), mockInputStream);
            when(mockFileDao.get("testBucket", fileKey)).thenReturn(Optional.of(mockResponseInputStream));
            when(mockFileIdentifierCacheDao.get(anyString(), anyString())).thenReturn(Optional.empty());

            // Act
            boolean result = loader.load(input);

            // Assert
            assertTrue(result);
            verify(spyModelResultsCacheDao).clear("testModel");
            verify(spyModelResultsCacheDao).put("testModel", modelResult1, 0.0);
            verify(spyModelResultsCacheDao).put("testModel", modelResult2, 0.0);
            assertEquals(2, loader.putItemCounter);
            assertEquals(modelResult1.length() + modelResult2.length(), loader.putItemTotalSize);
            assertTrue(spyModelResultsCacheDao.get("testModel", modelResult1).isPresent());
            assertTrue(spyModelResultsCacheDao.get("testModel", modelResult2).isPresent());
            verify(mockFileIdentifierCacheDao).put("model-results-identifier", "testModel", "eTag");
        }
    }

    @Test
    void testLoadHighValue_Successful() {
        // Arrange
        ModelResultLoaderInput input = new ModelResultLoaderInput("testBucket", "testKey", "testVendor", "testModel", ModelValueType.HighValue);
        String fileKey = "testVendor/2023-05-20/10/testKey"; // Assuming current date and time
        String modelResult1 = "result1";
        String modelResult2 = "result2";
        InputStream mockInputStream = new ByteArrayInputStream((modelResult1 + "\n" + modelResult2).getBytes(StandardCharsets.UTF_8));

        Clock fixedClock = Clock.fixed(Instant.parse("2023-05-20T10:15:30Z"), ZoneId.of("UTC"));
        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class)) {
            mockedInstant.when(Instant::now).thenReturn(fixedClock.instant());
            InputStream mockResponseInputStream = new ResponseInputStream<>(GetObjectResponse.builder().eTag("eTag").build(), mockInputStream);
            when(mockFileDao.get("testBucket", fileKey)).thenReturn(Optional.of(mockResponseInputStream));
            when(mockFileIdentifierCacheDao.get(anyString(), anyString())).thenReturn(Optional.empty());

            // Act
            boolean result = loader.load(input);

            // Assert
            assertTrue(result);
            verify(spyModelResultsCacheDao).clear("testModel");
            verify(spyModelResultsCacheDao).put("testModel", modelResult1, 1.0);
            verify(spyModelResultsCacheDao).put("testModel", modelResult2, 1.0);
            assertEquals(2, loader.putItemCounter);
            assertEquals(modelResult1.length() + modelResult2.length(), loader.putItemTotalSize);
            assertTrue(spyModelResultsCacheDao.get("testModel", modelResult1).isPresent());
            assertTrue(spyModelResultsCacheDao.get("testModel", modelResult2).isPresent());
            verify(mockFileIdentifierCacheDao).put("model-results-identifier", "testModel", "eTag");
        }
    }

    @Test
    void testLoad_SuccessfulWithRefresh() {
        // Arrange
        ModelResultLoaderInput input = new ModelResultLoaderInput("testBucket", "testKey", "testVendor", "testModel", ModelValueType.LowValue);
        String fileKey = "testVendor/2023-05-20/10/testKey"; // Assuming current date and time
        String modelResult1 = "result1";
        String modelResult2 = "result2";
        String modelResult3 = "result3";
        InputStream mockInputStream = new ByteArrayInputStream((modelResult1 + "\n" + modelResult2 + "\n" + modelResult3).getBytes(StandardCharsets.UTF_8));

        ModelResultLoaderInput secondInput = new ModelResultLoaderInput("testBucket", "testKey", "testVendor", "testModel", ModelValueType.LowValue);
        String secondFileKey = "testVendor/2023-05-20/10/testKey"; // Assuming current date and time
        InputStream secondMockInputStream = new ByteArrayInputStream((modelResult1 + "\n" + modelResult2).getBytes(StandardCharsets.UTF_8));

        Clock fixedClock = Clock.fixed(Instant.parse("2023-05-20T10:15:30Z"), ZoneId.of("UTC"));
        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class)) {
            // Setup
            mockedInstant.when(Instant::now).thenReturn(fixedClock.instant());
            InputStream mockResponseInputStream = new ResponseInputStream<>(GetObjectResponse.builder().eTag("eTag").build(), mockInputStream);
            when(mockFileDao.get("testBucket", fileKey)).thenReturn(Optional.of(mockResponseInputStream));
            when(mockFileIdentifierCacheDao.get(anyString(), anyString())).thenReturn(Optional.empty());

            // Act
            boolean result = loader.load(input);

            // Assert
            assertTrue(result);
            verify(spyModelResultsCacheDao).clear("testModel");
            verify(spyModelResultsCacheDao).put("testModel", modelResult1, 0.0);
            verify(spyModelResultsCacheDao).put("testModel", modelResult2, 0.0);
            verify(spyModelResultsCacheDao).put("testModel", modelResult3, 0.0);
            assertEquals(3, loader.putItemCounter);
            assertEquals(modelResult1.length() + modelResult2.length() + modelResult3.length(), loader.putItemTotalSize);
            assertTrue(spyModelResultsCacheDao.get("testModel", modelResult1).isPresent());
            assertTrue(spyModelResultsCacheDao.get("testModel", modelResult2).isPresent());
            assertTrue(spyModelResultsCacheDao.get("testModel", modelResult3).isPresent());
            verify(mockFileIdentifierCacheDao).put("model-results-identifier", "testModel", "eTag");

            // Setup again
            InputStream secondMockResponseInputStream = new ResponseInputStream<>(GetObjectResponse.builder().eTag("eTag2").build(), secondMockInputStream);
            when(mockFileDao.get("testBucket", secondFileKey)).thenReturn(Optional.of(secondMockResponseInputStream));
            when(mockFileIdentifierCacheDao.get(anyString(), anyString())).thenReturn(Optional.of("eTag"));

            // Act again
            boolean secondResult = loader.load(secondInput);

            // Assert again
            assertTrue(secondResult);
            verify(spyModelResultsCacheDao, times(2)).clear("testModel");
            verify(spyModelResultsCacheDao, times(2)).put("testModel", modelResult1, 0.0);
            verify(spyModelResultsCacheDao, times(2)).put("testModel", modelResult2, 0.0);
            assertEquals(2, loader.putItemCounter);
            assertEquals(modelResult1.length() + modelResult2.length(), loader.putItemTotalSize);
            assertTrue(spyModelResultsCacheDao.get("testModel", modelResult1).isPresent());
            assertTrue(spyModelResultsCacheDao.get("testModel", modelResult2).isPresent());
            assertTrue(spyModelResultsCacheDao.get("testModel", modelResult3).isEmpty());
            verify(mockFileIdentifierCacheDao).put("model-results-identifier", "testModel", "eTag2");
        }
    }

    @Test
    void testLoadHighValue_SuccessfulWithRefresh() {
        // Arrange
        ModelResultLoaderInput input = new ModelResultLoaderInput("testBucket", "testKey", "testVendor", "testModel", ModelValueType.HighValue);
        String fileKey = "testVendor/2023-05-20/10/testKey"; // Assuming current date and time
        String modelResult1 = "result1";
        String modelResult2 = "result2";
        String modelResult3 = "result3";
        InputStream mockInputStream = new ByteArrayInputStream((modelResult1 + "\n" + modelResult2 + "\n" + modelResult3).getBytes(StandardCharsets.UTF_8));

        ModelResultLoaderInput secondInput = new ModelResultLoaderInput("testBucket", "testKey", "testVendor", "testModel", ModelValueType.HighValue);
        String secondFileKey = "testVendor/2023-05-20/10/testKey"; // Assuming current date and time
        InputStream secondMockInputStream = new ByteArrayInputStream((modelResult1 + "\n" + modelResult2).getBytes(StandardCharsets.UTF_8));

        Clock fixedClock = Clock.fixed(Instant.parse("2023-05-20T10:15:30Z"), ZoneId.of("UTC"));
        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class)) {
            // Setup
            mockedInstant.when(Instant::now).thenReturn(fixedClock.instant());
            InputStream mockResponseInputStream = new ResponseInputStream<>(GetObjectResponse.builder().eTag("eTag").build(), mockInputStream);
            when(mockFileDao.get("testBucket", fileKey)).thenReturn(Optional.of(mockResponseInputStream));
            when(mockFileIdentifierCacheDao.get(anyString(), anyString())).thenReturn(Optional.empty());

            // Act
            boolean result = loader.load(input);

            // Assert
            assertTrue(result);
            verify(spyModelResultsCacheDao).clear("testModel");
            verify(spyModelResultsCacheDao).put("testModel", modelResult1, 1.0);
            verify(spyModelResultsCacheDao).put("testModel", modelResult2, 1.0);
            verify(spyModelResultsCacheDao).put("testModel", modelResult3, 1.0);
            assertEquals(3, loader.putItemCounter);
            assertEquals(modelResult1.length() + modelResult2.length() + modelResult3.length(), loader.putItemTotalSize);
            assertTrue(spyModelResultsCacheDao.get("testModel", modelResult1).isPresent());
            assertTrue(spyModelResultsCacheDao.get("testModel", modelResult2).isPresent());
            assertTrue(spyModelResultsCacheDao.get("testModel", modelResult3).isPresent());
            verify(mockFileIdentifierCacheDao).put("model-results-identifier", "testModel", "eTag");

            // Setup again
            InputStream secondMockResponseInputStream = new ResponseInputStream<>(GetObjectResponse.builder().eTag("eTag2").build(), secondMockInputStream);
            when(mockFileDao.get("testBucket", secondFileKey)).thenReturn(Optional.of(secondMockResponseInputStream));
            when(mockFileIdentifierCacheDao.get(anyString(), anyString())).thenReturn(Optional.of("eTag"));

            // Act again
            boolean secondResult = loader.load(secondInput);

            // Assert again
            assertTrue(secondResult);
            verify(spyModelResultsCacheDao, times(2)).clear("testModel");
            verify(spyModelResultsCacheDao, times(2)).put("testModel", modelResult1, 1.0);
            verify(spyModelResultsCacheDao, times(2)).put("testModel", modelResult2, 1.0);
            assertEquals(2, loader.putItemCounter);
            assertEquals(modelResult1.length() + modelResult2.length(), loader.putItemTotalSize);
            assertTrue(spyModelResultsCacheDao.get("testModel", modelResult1).isPresent());
            assertTrue(spyModelResultsCacheDao.get("testModel", modelResult2).isPresent());
            assertTrue(spyModelResultsCacheDao.get("testModel", modelResult3).isEmpty());
            verify(mockFileIdentifierCacheDao).put("model-results-identifier", "testModel", "eTag2");
        }
    }

    @Test
    void testLoad_FileNotFound() {
        // Arrange
        ModelResultLoaderInput input = new ModelResultLoaderInput("testBucket", "testKey", "testVendor", "testModel", ModelValueType.LowValue);
        when(mockFileDao.get(anyString(), anyString())).thenReturn(Optional.empty());

        // Act
        boolean result = loader.load(input);

        // Assert
        assertFalse(result);
        verify(spyModelResultsCacheDao, never()).put(anyString(), anyString(), anyDouble());
        verify(spyModelResultsCacheDao, never()).clear(anyString());
        verify(mockFileIdentifierCacheDao, never()).put(anyString(), anyString(), anyString());
    }

    @Test
    void testLoad_NoRefreshNeeded() {
        // Arrange
        ModelResultLoaderInput input = new ModelResultLoaderInput("testBucket", "testKey", "testVendor", "testModel", ModelValueType.LowValue);
        ResponseInputStream<GetObjectResponse> mockResponseInputStream = mock(ResponseInputStream.class);

        when(mockResponseInputStream.response()).thenReturn(GetObjectResponse.builder().eTag("ETag").build());
        when(mockFileDao.get(anyString(), anyString())).thenReturn(Optional.of(mockResponseInputStream));
        when(mockFileIdentifierCacheDao.get(anyString(), anyString())).thenReturn(Optional.of("ETag"));

        // Act
        boolean result = loader.load(input);

        // Assert
        assertFalse(result);
        verify(spyModelResultsCacheDao, never()).put(anyString(), anyString(), anyDouble());
        verify(spyModelResultsCacheDao, never()).clear(anyString());
        verify(mockFileIdentifierCacheDao, never()).put(anyString(), anyString(), anyString());
    }

    @Test
    void testLoad_ExceptionThrown() throws IOException {
        // Arrange
        ModelResultLoaderInput input = new ModelResultLoaderInput("testBucket", "testKey", "testVendor", "testModel", ModelValueType.LowValue);

        InputStream mockInputStream = mock(InputStream.class);
        doThrow(new IOException("Test exception")).when(mockInputStream).read(any(byte[].class));
        InputStream mockResponseInputStream = new ResponseInputStream<>(GetObjectResponse.builder().eTag("eTag").build(), mockInputStream);
        when(mockFileDao.get(anyString(), anyString())).thenReturn(Optional.of(mockResponseInputStream));
        when(mockFileIdentifierCacheDao.get(anyString(), anyString())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> loader.load(input));
    }

    @Test
    void testGetS3ObjectKey() {
        // Arrange
        ModelResultLoaderInput input = new ModelResultLoaderInput("testBucket", "testKey", "testVendor", "testModel", ModelValueType.LowValue);
        Clock fixedClock = Clock.fixed(Instant.parse("2023-05-20T10:15:30Z"), ZoneId.of("UTC"));
        try (MockedStatic<Instant> mockedInstant = mockStatic(Instant.class)) {
            mockedInstant.when(Instant::now).thenReturn(fixedClock.instant());

            // Act
            String result = loader.getS3ObjectKey(input);

            // Assert
            assertEquals("testVendor/2023-05-20/10/testKey", result);
        }
    }

    @Test
    void testGetBufferedReader() throws IOException {
        // Arrange
        InputStream inputStream = new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8));

        // Act
        BufferedReader reader = loader.getBufferedReader(inputStream);

        // Assert
        assertNotNull(reader);
        assertEquals("test", reader.readLine());
    }
}
