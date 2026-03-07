// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AWSServiceClientFactoryTest {

    @Mock
    private AwsCredentialsProvider mockCredentialsProvider;

    @Mock
    private S3Client mockS3Client;

    @Mock
    private S3ClientBuilder mockS3ClientBuilder;

    private AWSServiceClientFactory factory;

    @BeforeEach
    void setUp() {
        factory = AWSServiceClientFactory.getInstance();
        // Reset the s3Client field before each test
        setS3ClientToNull(factory);
    }

    @Test
    void testGetInstance() {
        AWSServiceClientFactory instance1 = AWSServiceClientFactory.getInstance();
        AWSServiceClientFactory instance2 = AWSServiceClientFactory.getInstance();
        assertSame(instance1, instance2, "getInstance should always return the same instance");
    }

    @Test
    void testGetS3Client_FirstCall() {
        // Arrange
        try (MockedStatic<S3Client> mockedS3Client = mockStatic(S3Client.class)) {
            String region = "us-west-2";
            mockedS3Client.when(S3Client::builder).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.region(Region.of(region))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.credentialsProvider(any())).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.crossRegionAccessEnabled(anyBoolean())).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.build()).thenReturn(mockS3Client);

            // Act
            S3Client result = factory.getS3Client(mockCredentialsProvider, region);

            // Assert
            assertNotNull(result);
            verify(mockS3ClientBuilder).region(Region.of(region));
            verify(mockS3ClientBuilder).credentialsProvider(mockCredentialsProvider);
            verify(mockS3ClientBuilder).crossRegionAccessEnabled(true);
            verify(mockS3ClientBuilder).build();
        }
    }

    @Test
    void testGetS3Client_SubsequentCalls() {
        // Arrange
        try (MockedStatic<S3Client> mockedS3Client = mockStatic(S3Client.class)) {
            String region = "us-west-2";
            mockedS3Client.when(S3Client::builder).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.region(Region.of(region))).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.credentialsProvider(any())).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.crossRegionAccessEnabled(anyBoolean())).thenReturn(mockS3ClientBuilder);
            when(mockS3ClientBuilder.build()).thenReturn(mockS3Client);
            // Act
            S3Client result1 = factory.getS3Client(mockCredentialsProvider, region);
            S3Client result2 = factory.getS3Client(mockCredentialsProvider, region);

            // Assert
            assertSame(result1, result2, "Subsequent calls should return the same S3Client instance");
            verify(mockS3ClientBuilder, times(1)).build(); // S3Client should only be built once
        }
    }

    @Test
    void testGetS3Client_NullCredentialsProvider() {
        assertThrows(NullPointerException.class, () -> factory.getS3Client(null, "us-west-2"));
    }

    @Test
    void testGetS3Client_NullRegion() {
        assertThrows(NullPointerException.class, () -> factory.getS3Client(mockCredentialsProvider, null));
    }

    @Test
    void testGetS3Client_ConcurrentAccess() throws InterruptedException {
        // Arrange
        String region = "us-west-2";

        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        S3Client[] results = new S3Client[threadCount];

        // Act
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> results[index] = factory.getS3Client(mockCredentialsProvider, region));
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        for (int i = 1; i < threadCount; i++) {
            assertSame(results[0], results[i], "All threads should get the same S3Client instance");
        }
    }

    // Helper method to reset s3Client field using reflection
    private void setS3ClientToNull(AWSServiceClientFactory factory) {
        try {
            java.lang.reflect.Field field = AWSServiceClientFactory.class.getDeclaredField("s3Client");
            field.setAccessible(true);
            field.set(factory, null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to reset s3Client field", e);
        }
    }
}
