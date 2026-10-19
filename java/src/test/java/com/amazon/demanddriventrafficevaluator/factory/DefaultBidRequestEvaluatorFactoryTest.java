// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluatorOnRuleBasedModel;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ConfigurableAggregator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.DelegatingModelEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluationResultsAggregator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluator;
import com.amazon.demanddriventrafficevaluator.task.TaskInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockConstruction;

@ExtendWith(MockitoExtension.class)
class DefaultBidRequestEvaluatorFactoryTest {

    @Mock
    private AwsCredentialsProvider mockCredentialsProvider;

    @Mock
    private ScheduledThreadPoolExecutor mockExecutor;

    private DefaultBidRequestEvaluatorFactory factory;

    @BeforeEach
    void setUp() {
        factory = new DefaultBidRequestEvaluatorFactory(
                "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket");
    }

    @Test
    void testConstructorWithFourParameters() {
        // Arrange & Act
        assertNotNull(factory);
        assertEquals("testSupplier", factory.sspIdentifier);
        assertNotNull(factory.defaultTaskInitializerFactory);
    }

    @Test
    void testConstructorWithFiveParameters() {
        // Arrange & Act
        factory = new DefaultBidRequestEvaluatorFactory(
                "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket", mockExecutor);

        // Assert
        assertNotNull(factory);
        assertEquals("testSupplier", factory.sspIdentifier);
        assertNotNull(factory.defaultTaskInitializerFactory);
    }

    @Test
    void testGetTaskInitializer() {
        // Act
        TaskInitializer initializer = factory.getTaskInitializer();

        // Assert
        assertNotNull(initializer);
    }

    @Test
    void testGetEvaluatorReturnsBidRequestEvaluatorOnRuleBasedModel() {
        // Act
        BidRequestEvaluator evaluator = factory.getEvaluator();

        // Assert
        assertNotNull(evaluator);
        assertTrue(evaluator instanceof BidRequestEvaluatorOnRuleBasedModel);
    }

    @Test
    void testProvideModelEvaluatorReturnsDelegatingModelEvaluator() {
        // Act
        ModelEvaluator evaluator = factory.provideModelEvaluator();

        // Assert
        assertNotNull(evaluator);
        assertTrue(evaluator instanceof DelegatingModelEvaluator);
    }

    @Test
    void testProvideModelEvaluationResultsAggregatorReturnsConfigurableAggregator() {
        // Act
        ModelEvaluationResultsAggregator aggregator = factory.provideModelEvaluationResultsAggregator();

        // Assert
        assertNotNull(aggregator);
        assertTrue(aggregator instanceof ConfigurableAggregator);
    }

    @Test
    void testProvideModelConfigurationProvider() {
        // Act & Assert
        assertNotNull(factory.provideModelConfigurationProvider());
    }

    @Test
    void testCreateFourParameters() {
        // Act
        BidRequestEvaluatorFactory result = BidRequestEvaluatorFactory.create(
                "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket");

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof DefaultBidRequestEvaluatorFactory);
    }

    @Test
    void testCreateFiveParameters() {
        // Act
        BidRequestEvaluatorFactory result = BidRequestEvaluatorFactory.create(
                "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket", mockExecutor);

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof DefaultBidRequestEvaluatorFactory);
    }

    @Test
    void testConstructorWithFiveParametersAllocatesNoExecutor() {
        // Build the cached S3 client first, so pools created by the AWS SDK are not counted
        AWSServiceClientFactory.getInstance().getS3Client(mockCredentialsProvider, "us-west-2");
        try (MockedConstruction<ScheduledThreadPoolExecutor> pools = mockConstruction(ScheduledThreadPoolExecutor.class)) {
            // Act
            factory = new DefaultBidRequestEvaluatorFactory(
                    "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket", mockExecutor);

            // Assert
            assertEquals(0, pools.constructed().size());
            assertSame(mockExecutor, getExecutor(factory));
            assertSame(mockExecutor, getExecutor(factory.defaultTaskInitializerFactory));
        }
    }

    @Test
    void testConstructorWithFourParametersSharesOneExecutor() {
        // Build the cached S3 client first, so pools created by the AWS SDK are not counted
        AWSServiceClientFactory.getInstance().getS3Client(mockCredentialsProvider, "us-west-2");
        try (MockedConstruction<ScheduledThreadPoolExecutor> pools = mockConstruction(ScheduledThreadPoolExecutor.class)) {
            // Act
            factory = new DefaultBidRequestEvaluatorFactory(
                    "testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket");

            // Assert
            assertEquals(1, pools.constructed().size());
            ScheduledThreadPoolExecutor pool = pools.constructed().get(0);
            assertSame(pool, getExecutor(factory));
            assertSame(pool, getExecutor(factory.defaultTaskInitializerFactory));
        }
    }

    // Helper method to read the private executor field using reflection
    private ScheduledThreadPoolExecutor getExecutor(Object target) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField("executor");
            field.setAccessible(true);
            return (ScheduledThreadPoolExecutor) field.get(target);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to read executor field", e);
        }
    }
}
