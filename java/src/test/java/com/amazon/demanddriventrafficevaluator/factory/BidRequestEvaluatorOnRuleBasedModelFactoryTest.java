// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.factory;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluatorOnRuleBasedModel;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluationResultsAggregator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluationResultsMaxAggregator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluator;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.RuleBasedModelEvaluator;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ModelConfigurationProvider;
import com.amazon.demanddriventrafficevaluator.task.TaskInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class BidRequestEvaluatorOnRuleBasedModelFactoryTest {

    @Mock
    private AwsCredentialsProvider mockCredentialsProvider;

    @Mock
    private ScheduledThreadPoolExecutor mockExecutor;

    @Mock
    private TaskInitializer mockTaskInitializer;

    private BidRequestEvaluatorOnRuleBasedModelFactory factory;

    @BeforeEach
    void setUp() {
        factory = new BidRequestEvaluatorOnRuleBasedModelFactory("testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket");
    }

    @Test
    void testConstructorWithFourParameters() {
        assertNotNull(factory);
        assertEquals("testSupplier", factory.sspIdentifier);
        assertNotNull(factory.defaultTaskInitializerFactory);
    }

    @Test
    void testConstructorWithFiveParameters() {
        factory = new BidRequestEvaluatorOnRuleBasedModelFactory("testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket", mockExecutor);
        assertNotNull(factory);
        assertEquals("testSupplier", factory.sspIdentifier);
        assertNotNull(factory.defaultTaskInitializerFactory);
    }

    @Test
    void testGetTaskInitializer() {
        TaskInitializer initializer = factory.getTaskInitializer();
        assertNotNull(initializer);
    }

    @Test
    void testGetEvaluator() {
        BidRequestEvaluator evaluator = factory.getEvaluator();
        assertNotNull(evaluator);
        assertTrue(evaluator instanceof BidRequestEvaluatorOnRuleBasedModel);
    }

    @Test
    void testProvideModelConfigurationProvider() {
        ModelConfigurationProvider provider = factory.provideModelConfigurationProvider();
        assertNotNull(provider);
    }

    @Test
    void testProvideModelEvaluator() {
        ModelEvaluator evaluator = factory.provideModelEvaluator();
        assertNotNull(evaluator);
        assertTrue(evaluator instanceof RuleBasedModelEvaluator);
    }

    @Test
    void testProvideModelEvaluationResultsAggregator() {
        ModelEvaluationResultsAggregator aggregator = factory.provideModelEvaluationResultsAggregator();
        assertNotNull(aggregator);
        assertTrue(aggregator instanceof ModelEvaluationResultsMaxAggregator);
    }

    @Test
    void testCreateWithFourParameters() {
        BidRequestEvaluatorFactory result = BidRequestEvaluatorFactory.create("testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket");
        assertNotNull(result);
        assertTrue(result instanceof BidRequestEvaluatorOnRuleBasedModelFactory);
    }

    @Test
    void testCreateWithFiveParameters() {
        BidRequestEvaluatorFactory result = BidRequestEvaluatorFactory.create("testSupplier", mockCredentialsProvider, "us-west-2", "test-bucket", mockExecutor);
        assertNotNull(result);
        assertTrue(result instanceof BidRequestEvaluatorOnRuleBasedModelFactory);
    }
}
