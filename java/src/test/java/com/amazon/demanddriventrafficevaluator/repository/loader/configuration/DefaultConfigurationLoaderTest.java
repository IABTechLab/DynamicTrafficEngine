// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.loader.configuration;

import com.amazon.demanddriventrafficevaluator.repository.dao.Dao;
import com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static com.amazon.demanddriventrafficevaluator.repository.dao.LocalCacheDao.CACHE_IDENTIFIER_CONFIGURATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultConfigurationLoaderTest {

    private static final String CONFIG_CACHE_KEY = "testConfigKey";
    @Mock
    private Dao<String, String> mockFileIdentifierCacheDao;
    @Mock
    private Dao<String, InputStream> mockFileDao;
    @Mock
    private LocalCacheDao<String, ModelConfiguration> mockConfigurationCacheDao;
    @Mock
    private ObjectMapper mockMapper;
    @Mock
    private ModelConfiguration mockModelConfiguration;
    @Mock
    private ResponseInputStream<GetObjectResponse> mockResponseInputStream;
    private DefaultConfigurationLoader<ModelConfiguration> loader;

    @BeforeEach
    void setUp() {
        loader = new DefaultConfigurationLoader<>(
                mockFileIdentifierCacheDao,
                mockFileDao,
                mockConfigurationCacheDao,
                CONFIG_CACHE_KEY,
                ModelConfiguration.class,
                mockMapper
        );
    }

    @Test
    void testLoadNoPreviousETag_Successful() throws IOException {
        // Arrange
        ConfigurationLoaderInput input = new ConfigurationLoaderInput("testBucket", "testKey", "testVendor", "testType");
        String s3ObjectKey = "testVendor/configuration/testType/config.json";

        when(mockFileDao.get("testBucket", s3ObjectKey)).thenReturn(Optional.of(mockResponseInputStream));
        when(mockResponseInputStream.response()).thenReturn(GetObjectResponse.builder().eTag("eTag").build());
        when(mockMapper.readValue(mockResponseInputStream, ModelConfiguration.class)).thenReturn(mockModelConfiguration);
        when(mockFileIdentifierCacheDao.get(anyString(), anyString())).thenReturn(Optional.empty());

        // Act
        boolean result = loader.load(input);

        // Assert
        assertTrue(result);
        verify(mockConfigurationCacheDao).put(CACHE_IDENTIFIER_CONFIGURATION, CONFIG_CACHE_KEY, mockModelConfiguration);
        verify(mockFileIdentifierCacheDao).put("model-results-identifier", "testConfigKey", "eTag");
    }

    @Test
    void testLoad_Successful() throws IOException {
        // Arrange
        ConfigurationLoaderInput input = new ConfigurationLoaderInput("testBucket", "testKey", "testVendor", "testType");
        String s3ObjectKey = "testVendor/configuration/testType/config.json";

        when(mockFileDao.get("testBucket", s3ObjectKey)).thenReturn(Optional.of(mockResponseInputStream));
        when(mockResponseInputStream.response()).thenReturn(GetObjectResponse.builder().eTag("eTag").build());
        when(mockMapper.readValue(mockResponseInputStream, ModelConfiguration.class)).thenReturn(mockModelConfiguration);
        when(mockFileIdentifierCacheDao.get(anyString(), anyString())).thenReturn(Optional.of("anotherETag"));

        // Act
        boolean result = loader.load(input);

        // Assert
        assertTrue(result);
        verify(mockConfigurationCacheDao).put(CACHE_IDENTIFIER_CONFIGURATION, CONFIG_CACHE_KEY, mockModelConfiguration);
        verify(mockFileIdentifierCacheDao).put("model-results-identifier", "testConfigKey", "eTag");
    }

    @Test
    void testLoad_FileNotFound() {
        // Arrange
        ConfigurationLoaderInput input = new ConfigurationLoaderInput("testBucket", "testKey", "testVendor", "testType");
        String s3ObjectKey = "testVendor/configuration/testType/config.json";

        when(mockFileDao.get("testBucket", s3ObjectKey)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> loader.load(input));
    }

    @Test
    void testLoad_DeserializationError() throws IOException {
        // Arrange
        ConfigurationLoaderInput input = new ConfigurationLoaderInput("testBucket", "testKey", "testVendor", "testType");
        String s3ObjectKey = "testVendor/configuration/testType/config.json";

        when(mockFileDao.get("testBucket", s3ObjectKey)).thenReturn(Optional.of(mockResponseInputStream));
        when(mockMapper.readValue(mockResponseInputStream, ModelConfiguration.class)).thenThrow(new IOException("Deserialization error"));
        when(mockResponseInputStream.response()).thenReturn(GetObjectResponse.builder().eTag("eTag").build());
        when(mockFileIdentifierCacheDao.get(anyString(), anyString())).thenReturn(Optional.empty());

        // Act & Assert
        boolean result = loader.load(input);
        assertFalse(result);
        verify(mockConfigurationCacheDao, times(0)).put(CACHE_IDENTIFIER_CONFIGURATION, CONFIG_CACHE_KEY, mockModelConfiguration);
        verify(mockFileIdentifierCacheDao).put("model-results-identifier", "testConfigKey", "eTag");
    }

    @Test
    void testLoad_NoRefreshNeeded() throws IOException {
        // Arrange
        ConfigurationLoaderInput input = new ConfigurationLoaderInput("testBucket", "testKey", "testVendor", "testType");
        String s3ObjectKey = "testVendor/configuration/testType/config.json";

        when(mockResponseInputStream.response()).thenReturn(GetObjectResponse.builder().eTag("eTag").build());
        when(mockFileDao.get("testBucket", s3ObjectKey)).thenReturn(Optional.of(mockResponseInputStream));
        when(mockFileIdentifierCacheDao.get(anyString(), anyString())).thenReturn(Optional.of("eTag"));

        // Act
        boolean result = loader.load(input);

        // Assert
        assertFalse(result);
        verify(mockMapper, never()).readValue(any(InputStream.class), eq(ModelConfiguration.class));
        verify(mockConfigurationCacheDao, never()).put(anyString(), anyString(), any());
        verify(mockFileIdentifierCacheDao, never()).put(anyString(), anyString(), anyString());
    }

    @Test
    void testLoad_RefreshError() throws IOException {
        // Arrange
        ConfigurationLoaderInput input = new ConfigurationLoaderInput("testBucket", "testKey", "testVendor", "testType");
        String s3ObjectKey = "testVendor/configuration/testType/config.json";
        InputStream mockInputStream = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));

        when(mockFileDao.get("testBucket", s3ObjectKey)).thenReturn(Optional.of(mockInputStream));

        // Act
        boolean result = loader.load(input);

        // Assert
        assertFalse(result);
        verify(mockMapper, never()).readValue(any(InputStream.class), eq(ModelConfiguration.class));
        verify(mockConfigurationCacheDao, never()).put(anyString(), anyString(), any());
        verify(mockFileIdentifierCacheDao, never()).put(anyString(), anyString(), anyString());
    }

    @Test
    void testGetS3ObjectKey() {
        // Arrange
        ConfigurationLoaderInput input = new ConfigurationLoaderInput("testBucket", "testKey", "testVendor", "testType");

        // Act
        String result = loader.getS3ObjectKey(input);

        // Assert
        assertEquals("testVendor/configuration/testType/config.json", result);
    }
}
