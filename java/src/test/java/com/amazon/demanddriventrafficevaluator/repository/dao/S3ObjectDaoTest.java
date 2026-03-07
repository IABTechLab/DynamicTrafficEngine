// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ObjectDaoTest {

    @Mock
    private S3Client mockS3Client;

    @Mock
    private InputStream mockInputStream;

    @Mock
    private ResponseInputStream<GetObjectResponse> mockResponseResponseInputStream;

    private S3ObjectDao s3ObjectDao;

    @BeforeEach
    void setUp() {
        s3ObjectDao = new S3ObjectDao(mockS3Client);
    }

    @Test
    void testGet_Successful() {
        // Arrange
        String bucketName = "testBucket";
        String key = "testKey";
        GetObjectRequest expectedRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        when(mockS3Client.getObject(any(GetObjectRequest.class))).thenReturn(mockResponseResponseInputStream);

        // Act
        Optional<InputStream> result = s3ObjectDao.get(bucketName, key);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(mockResponseResponseInputStream, result.get());
        verify(mockS3Client).getObject(eq(expectedRequest));
    }

    @Test
    void testGet_WhenS3ExceptionThrown() {
        // Arrange
        String bucketName = "testBucket";
        String key = "testKey";
        when(mockS3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("S3 Error").build());

        // Act
        Optional<InputStream> result = s3ObjectDao.get(bucketName, key);

        // Assert
        assertFalse(result.isPresent());
        verify(mockS3Client).getObject(any(GetObjectRequest.class));
    }

    @Test
    void testGet_WhenGenericExceptionThrown() {
        // Arrange
        String bucketName = "testBucket";
        String key = "testKey";
        when(mockS3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        // Act
        Optional<InputStream> result = s3ObjectDao.get(bucketName, key);

        // Assert
        assertFalse(result.isPresent());
        verify(mockS3Client).getObject(any(GetObjectRequest.class));
    }

    @Test
    void testPut_ThrowsUnsupportedOperationException() {
        // Arrange
        String bucketName = "testBucket";
        String key = "testKey";
        InputStream inputStream = mock(InputStream.class);

        // Act & Assert
        assertThrows(UnsupportedOperationException.class,
                () -> s3ObjectDao.put(bucketName, key, inputStream));
    }
}
