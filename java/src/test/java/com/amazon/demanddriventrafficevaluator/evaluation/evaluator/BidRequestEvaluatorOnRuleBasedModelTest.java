// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import com.amazon.demanddriventrafficevaluator.BaseTestCase;
import com.amazon.demanddriventrafficevaluator.evaluation.experiment.ExperimentContext;
import com.amazon.demanddriventrafficevaluator.evaluation.experiment.ExperimentManager;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ConfigurationProvider;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.utils.ImmutableMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluatorOnRuleBasedModel.DEFAULT_RESPONSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidRequestEvaluatorOnRuleBasedModelTest extends BaseTestCase {

    private static final String SSP_IDENTIFIER = "test-ssp";
    private static final String REQUEST_ID = "test-request-id";
    private static final String MODEL_IDENTIFIER = "test-model";
    private static final String TREATMENT_T = "T";
    private static final Map<String, String> EXPERIMENT_ARRANGEMENT = ImmutableMap.of(
            "test-experiment", TREATMENT_T
    );
    @Mock
    private ExperimentManager experimentManager;
    @Mock
    private ConfigurationProvider<ModelConfiguration> modelConfigurationProvider;
    @Mock
    private ModelEvaluator modelEvaluator;
    @Mock
    private ModelEvaluationResultsAggregator modelEvaluationResultsAggregator;
    @InjectMocks
    private BidRequestEvaluatorOnRuleBasedModel evaluator;
    private ExperimentConfiguration experimentConfiguration;
    private ModelConfiguration modelConfiguration;
    private ExperimentContext experimentContext;

    @BeforeEach
    void setUp() {
        evaluator = new BidRequestEvaluatorOnRuleBasedModel(
                SSP_IDENTIFIER,
                experimentManager,
                modelConfigurationProvider,
                modelEvaluator,
                modelEvaluationResultsAggregator
        );

        experimentConfiguration = readJsonResourceAsPojo(
                "/test/ExperimentConfiguration.json",
                ExperimentConfiguration.class
        );
        modelConfiguration = readJsonResourceAsPojo(
                "/test/ModelConfiguration.json",
                ModelConfiguration.class
        );
        experimentContext = new ExperimentContext(EXPERIMENT_ARRANGEMENT, experimentConfiguration);
    }

    @Test
    void testEvaluateSuccess() {
        // Prepare test data
        String openRtbRequest = "{\"id\":\"" + REQUEST_ID + "\"}";
        BidRequestEvaluatorInput input = BidRequestEvaluatorInput.builder()
                .openRtbRequest(openRtbRequest)
                .build();
        ModelEvaluatorOutput modelEvaluatorOutput = ModelEvaluatorOutput.builder()
                .build();
        AggregatedModelEvaluationResult aggregatedResult = AggregatedModelEvaluationResult.builder()
                .score(0.0)
                .scoreWithTreatment(1.0)
                .treatmentCodeInInt(1)
                .build();

        // Setup mocks
        ArgumentCaptor<EvaluationContext> contextCaptor = ArgumentCaptor.forClass(EvaluationContext.class);
        doAnswer(invocation -> {
            EvaluationContext context = invocation.getArgument(0);
            context.setExperimentContext(experimentContext);
            return null;
        }).when(experimentManager).setupExperimentContext(contextCaptor.capture());
        when(modelConfigurationProvider.provide()).thenReturn(modelConfiguration);
        when(modelEvaluator.evaluate(any(ModelEvaluatorInput.class)))
                .thenReturn(modelEvaluatorOutput);
        when(modelEvaluationResultsAggregator.aggregate(any(EvaluationContext.class)))
                .thenReturn(aggregatedResult);

        // Execute
        BidRequestEvaluatorOutput output = evaluator.evaluate(input);

        // Verify
        EvaluationContext capturedContext = contextCaptor.getValue();
        assertNotNull(capturedContext.getRequestId());
        assertEquals(REQUEST_ID, capturedContext.getRequestId());

        assertNotNull(output);
        assertNotNull(output.getResponse());
        assertEquals(1, output.getResponse().getSlots().size());
        Slot slot = output.getResponse().getSlots().get(0);
        assertEquals(1.0, slot.getFilterDecision());
        assertTrue(slot.getExt().contains("{\"decision\":0.0}"));
        assertTrue(output.getResponse().getExt().contains("{\"amazontest\":{\"learning\":1}}"));

        verify(experimentManager).setupExperimentContext(any(EvaluationContext.class));
        verify(modelConfigurationProvider).provide();
        verify(modelEvaluator).evaluate(any(ModelEvaluatorInput.class));
        verify(modelEvaluationResultsAggregator).aggregate(any(EvaluationContext.class));

        List<String> debugInfo = capturedContext.getDebugInfo();
        assertEquals(0, debugInfo.size());
    }

    @Test
    void testEvaluateSuccessWithMap() {
        // Prepare test data
        Map<String, List<String>> openRtbRequest = new HashMap<>();
        openRtbRequest.put("$.id", Collections.singletonList(REQUEST_ID));
        BidRequestEvaluatorInput input = BidRequestEvaluatorInput.builder()
                .openRtbRequestMap(openRtbRequest)
                .build();
        ModelEvaluatorOutput modelEvaluatorOutput = ModelEvaluatorOutput.builder()
                .build();
        AggregatedModelEvaluationResult aggregatedResult = AggregatedModelEvaluationResult.builder()
                .score(0.0)
                .scoreWithTreatment(1.0)
                .treatmentCodeInInt(1)
                .build();

        // Setup mocks
        ArgumentCaptor<EvaluationContext> contextCaptor = ArgumentCaptor.forClass(EvaluationContext.class);
        doAnswer(invocation -> {
            EvaluationContext context = invocation.getArgument(0);
            context.setExperimentContext(experimentContext);
            return null;
        }).when(experimentManager).setupExperimentContext(contextCaptor.capture());
        when(modelConfigurationProvider.provide()).thenReturn(modelConfiguration);
        when(modelEvaluator.evaluate(any(ModelEvaluatorInput.class)))
                .thenReturn(modelEvaluatorOutput);
        when(modelEvaluationResultsAggregator.aggregate(any(EvaluationContext.class)))
                .thenReturn(aggregatedResult);

        // Execute
        BidRequestEvaluatorOutput output = evaluator.evaluate(input);

        // Verify
        EvaluationContext capturedContext = contextCaptor.getValue();
        assertNotNull(capturedContext.getRequestId());
        assertEquals(REQUEST_ID, capturedContext.getRequestId());

        assertNotNull(output);
        assertNotNull(output.getResponse());
        assertEquals(1, output.getResponse().getSlots().size());
        Slot slot = output.getResponse().getSlots().get(0);
        assertEquals(1.0, slot.getFilterDecision());
        assertTrue(slot.getExt().contains("{\"decision\":0.0}"));
        assertTrue(output.getResponse().getExt().contains("{\"amazontest\":{\"learning\":1}}"));

        verify(experimentManager).setupExperimentContext(any(EvaluationContext.class));
        verify(modelConfigurationProvider).provide();
        verify(modelEvaluator).evaluate(any(ModelEvaluatorInput.class));
        verify(modelEvaluationResultsAggregator).aggregate(any(EvaluationContext.class));

        List<String> debugInfo = capturedContext.getDebugInfo();
        assertEquals(0, debugInfo.size());
    }

    @Test
    void testEvaluateSuccessWithTwoModels() {
        experimentConfiguration = readJsonResourceAsPojo(
                "/test/ExperimentConfigurationTwoModels.json",
                ExperimentConfiguration.class
        );
        modelConfiguration = readJsonResourceAsPojo(
                "/test/ModelConfigurationTwoModels.json",
                ModelConfiguration.class
        );
        experimentContext = new ExperimentContext(EXPERIMENT_ARRANGEMENT, experimentConfiguration);

        // Prepare test data
        String openRtbRequest = "{\"id\":\"" + REQUEST_ID + "\"}";
        BidRequestEvaluatorInput input = BidRequestEvaluatorInput.builder()
                .openRtbRequest(openRtbRequest)
                .build();
        ModelEvaluatorOutput modelEvaluatorOutput = ModelEvaluatorOutput.builder()
                .build();
        AggregatedModelEvaluationResult aggregatedResult = AggregatedModelEvaluationResult.builder()
                .score(0.0)
                .scoreWithTreatment(1.0)
                .treatmentCodeInInt(1)
                .build();

        // Setup mocks
        ArgumentCaptor<EvaluationContext> contextCaptor = ArgumentCaptor.forClass(EvaluationContext.class);
        doAnswer(invocation -> {
            EvaluationContext context = invocation.getArgument(0);
            context.setExperimentContext(experimentContext);
            return null;
        }).when(experimentManager).setupExperimentContext(contextCaptor.capture());
        when(modelConfigurationProvider.provide()).thenReturn(modelConfiguration);
        when(modelEvaluator.evaluate(any(ModelEvaluatorInput.class)))
                .thenReturn(modelEvaluatorOutput);
        when(modelEvaluationResultsAggregator.aggregate(any(EvaluationContext.class)))
                .thenReturn(aggregatedResult);

        // Execute
        BidRequestEvaluatorOutput output = evaluator.evaluate(input);

        // Verify
        EvaluationContext capturedContext = contextCaptor.getValue();
        assertNotNull(capturedContext.getRequestId());
        assertEquals(REQUEST_ID, capturedContext.getRequestId());

        assertNotNull(output);
        assertNotNull(output.getResponse());
        assertEquals(1, output.getResponse().getSlots().size());
        Slot slot = output.getResponse().getSlots().get(0);
        assertEquals(1.0, slot.getFilterDecision());
        assertTrue(slot.getExt().contains("{\"decision\":0.0}"));
        assertTrue(output.getResponse().getExt().contains("{\"amazontest\":{\"learning\":1}}"));

        verify(experimentManager).setupExperimentContext(any(EvaluationContext.class));
        verify(modelConfigurationProvider).provide();
        verify(modelEvaluator, times(2)).evaluate(any(ModelEvaluatorInput.class));
        verify(modelEvaluationResultsAggregator).aggregate(any(EvaluationContext.class));

        List<String> debugInfo = capturedContext.getDebugInfo();
        assertEquals(0, debugInfo.size());
    }

    @Test
    void testEvaluateSuccessWithMapTwoModels() {
        experimentConfiguration = readJsonResourceAsPojo(
                "/test/ExperimentConfigurationTwoModels.json",
                ExperimentConfiguration.class
        );
        modelConfiguration = readJsonResourceAsPojo(
                "/test/ModelConfigurationTwoModels.json",
                ModelConfiguration.class
        );
        experimentContext = new ExperimentContext(EXPERIMENT_ARRANGEMENT, experimentConfiguration);

        // Prepare test data
        Map<String, List<String>> openRtbRequest = new HashMap<>();
        openRtbRequest.put("$.id", Collections.singletonList(REQUEST_ID));
        BidRequestEvaluatorInput input = BidRequestEvaluatorInput.builder()
                .openRtbRequestMap(openRtbRequest)
                .build();
        ModelEvaluatorOutput modelEvaluatorOutput = ModelEvaluatorOutput.builder()
                .build();
        AggregatedModelEvaluationResult aggregatedResult = AggregatedModelEvaluationResult.builder()
                .score(0.0)
                .scoreWithTreatment(1.0)
                .treatmentCodeInInt(1)
                .build();

        // Setup mocks
        ArgumentCaptor<EvaluationContext> contextCaptor = ArgumentCaptor.forClass(EvaluationContext.class);
        doAnswer(invocation -> {
            EvaluationContext context = invocation.getArgument(0);
            context.setExperimentContext(experimentContext);
            return null;
        }).when(experimentManager).setupExperimentContext(contextCaptor.capture());
        when(modelConfigurationProvider.provide()).thenReturn(modelConfiguration);
        when(modelEvaluator.evaluate(any(ModelEvaluatorInput.class)))
                .thenReturn(modelEvaluatorOutput);
        when(modelEvaluationResultsAggregator.aggregate(any(EvaluationContext.class)))
                .thenReturn(aggregatedResult);

        // Execute
        BidRequestEvaluatorOutput output = evaluator.evaluate(input);

        // Verify
        EvaluationContext capturedContext = contextCaptor.getValue();
        assertNotNull(capturedContext.getRequestId());
        assertEquals(REQUEST_ID, capturedContext.getRequestId());

        assertNotNull(output);
        assertNotNull(output.getResponse());
        assertEquals(1, output.getResponse().getSlots().size());
        Slot slot = output.getResponse().getSlots().get(0);
        assertEquals(1.0, slot.getFilterDecision());
        assertTrue(slot.getExt().contains("{\"decision\":0.0}"));
        assertTrue(output.getResponse().getExt().contains("{\"amazontest\":{\"learning\":1}}"));

        verify(experimentManager).setupExperimentContext(any(EvaluationContext.class));
        verify(modelConfigurationProvider).provide();
        verify(modelEvaluator, times(2)).evaluate(any(ModelEvaluatorInput.class));
        verify(modelEvaluationResultsAggregator).aggregate(any(EvaluationContext.class));

        List<String> debugInfo = capturedContext.getDebugInfo();
        assertEquals(0, debugInfo.size());
    }

    @Test
    void testEvaluateWithMissingRequestId() {
        // Prepare test data with missing ID
        String openRtbRequest = "{\"test\": \"value\"}";
        BidRequestEvaluatorInput input = BidRequestEvaluatorInput.builder()
                .openRtbRequest(openRtbRequest)
                .build();

        // Setup mocks
        ArgumentCaptor<EvaluationContext> contextCaptor = ArgumentCaptor.forClass(EvaluationContext.class);
        doAnswer(invocation -> {
            EvaluationContext context = invocation.getArgument(0);
            context.setExperimentContext(experimentContext);
            return null;
        }).when(experimentManager).setupExperimentContext(contextCaptor.capture());


        // Execute
        BidRequestEvaluatorOutput output = evaluator.evaluate(input);

        EvaluationContext capturedContext = contextCaptor.getValue();
        assertNotNull(capturedContext.getRequestId());
        assertNotNull(output);
        verify(experimentManager).setupExperimentContext(any(EvaluationContext.class));
        List<String> debugInfo = capturedContext.getDebugInfo();
        assertEquals(3, debugInfo.size());
        assertTrue(debugInfo.get(0).contains("[Debug] Could not find id from OpenRtbRequest and use self generated UUID instead."));
        assertEquals("""
                [Error] Error while loading model configuration.
                Cannot invoke "com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration.getModelDefinitionByIdentifier()" because "modelConfiguration" is null                                                 
                """, debugInfo.get(1));
        assertEquals("""
                [Error] Error while evaluating bid request.
                Error while loading model configuration
                """, debugInfo.get(2));

        // Prepare test data with null ID
        openRtbRequest = "{\"id\": null}";
        input = BidRequestEvaluatorInput.builder()
                .openRtbRequest(openRtbRequest)
                .build();

        // Execute
        output = evaluator.evaluate(input);

        capturedContext = contextCaptor.getValue();
        assertNotNull(capturedContext.getRequestId());
        assertNotNull(output);
        verify(experimentManager, times(2)).setupExperimentContext(any(EvaluationContext.class));
        debugInfo = capturedContext.getDebugInfo();
        assertEquals(3, debugInfo.size());
        assertTrue(debugInfo.get(0).contains("[Debug] Could not find id from OpenRtbRequest and use self generated UUID instead."));
        assertEquals("""
                [Error] Error while loading model configuration.
                Cannot invoke "com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration.getModelDefinitionByIdentifier()" because "modelConfiguration" is null                                                 
                """, debugInfo.get(1));
        assertEquals("""
                [Error] Error while evaluating bid request.
                Error while loading model configuration
                """, debugInfo.get(2));
    }

    @Test
    void testEvaluateMapWithMissingRequestId() {
        // Prepare test data with missing ID
        Map<String, List<String>> openRtbRequest = new HashMap<>();
        openRtbRequest.put("test", Collections.singletonList("value"));
        BidRequestEvaluatorInput input = BidRequestEvaluatorInput.builder()
                .openRtbRequestMap(openRtbRequest)
                .build();

        // Setup mocks
        ArgumentCaptor<EvaluationContext> contextCaptor = ArgumentCaptor.forClass(EvaluationContext.class);
        doAnswer(invocation -> {
            EvaluationContext context = invocation.getArgument(0);
            context.setExperimentContext(experimentContext);
            return null;
        }).when(experimentManager).setupExperimentContext(contextCaptor.capture());

        // Execute
        BidRequestEvaluatorOutput output = evaluator.evaluate(input);

        EvaluationContext capturedContext = contextCaptor.getValue();
        assertNotNull(capturedContext.getRequestId());
        assertNotNull(output);
        verify(experimentManager).setupExperimentContext(any(EvaluationContext.class));
        List<String> debugInfo = capturedContext.getDebugInfo();
        assertEquals(3, debugInfo.size());
        assertTrue(debugInfo.get(0).contains("[Debug] Could not find id from OpenRtbRequest and use self generated UUID instead."));
        assertEquals("""
                [Error] Error while loading model configuration.
                Cannot invoke "com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration.getModelDefinitionByIdentifier()" because "modelConfiguration" is null                                                 
                """, debugInfo.get(1));
        assertEquals("""
                [Error] Error while evaluating bid request.
                Error while loading model configuration
                """, debugInfo.get(2));

        // Prepare test data with null ID
        openRtbRequest = new HashMap<>();
        openRtbRequest.put("id", null);
        input = BidRequestEvaluatorInput.builder()
                .openRtbRequestMap(openRtbRequest)
                .build();

        // Execute
        output = evaluator.evaluate(input);

        capturedContext = contextCaptor.getValue();
        assertNotNull(capturedContext.getRequestId());
        assertNotNull(output);
        verify(experimentManager, times(2)).setupExperimentContext(any(EvaluationContext.class));
        debugInfo = capturedContext.getDebugInfo();
        assertEquals(3, debugInfo.size());
        assertTrue(debugInfo.get(0).contains("[Debug] Could not find id from OpenRtbRequest and use self generated UUID instead."));
        assertEquals("""
                [Error] Error while loading model configuration.
                Cannot invoke "com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration.getModelDefinitionByIdentifier()" because "modelConfiguration" is null                                                 
                """, debugInfo.get(1));
        assertEquals("""
                [Error] Error while evaluating bid request.
                Error while loading model configuration
                """, debugInfo.get(2));
    }

    @Test
    void testEvaluateWithMissingRequest() {
        BidRequestEvaluatorInput input = BidRequestEvaluatorInput.builder()
                .openRtbRequest(null)
                .openRtbRequestMap(null)
                .build();

        // Execute
        BidRequestEvaluatorOutput output = evaluator.evaluate(input);

        // Verify default response
        assertNotNull(output);
        assertEquals(DEFAULT_RESPONSE, output.getResponse());

        input = BidRequestEvaluatorInput.builder()
                .openRtbRequest("")
                .openRtbRequestMap(null)
                .build();

        // Execute
        output = evaluator.evaluate(input);

        // Verify default response
        assertNotNull(output);
        assertEquals(DEFAULT_RESPONSE, output.getResponse());

        input = BidRequestEvaluatorInput.builder()
                .openRtbRequest("{}")
                .openRtbRequestMap(new HashMap<>())
                .build();

        // Execute
        output = evaluator.evaluate(input);

        // Verify default response
        assertNotNull(output);
        assertEquals(DEFAULT_RESPONSE, output.getResponse());
    }

    @Test
    void testEvaluateWithModelConfigurationError() {
        // Prepare test data
        String openRtbRequest = "{\"id\":\"" + REQUEST_ID + "\"}";
        BidRequestEvaluatorInput input = BidRequestEvaluatorInput.builder()
                .openRtbRequest(openRtbRequest)
                .build();

        // Setup mocks
        ArgumentCaptor<EvaluationContext> contextCaptor = ArgumentCaptor.forClass(EvaluationContext.class);
        doAnswer(invocation -> {
            EvaluationContext context = invocation.getArgument(0);
            context.setExperimentContext(experimentContext);
            return null;
        }).when(experimentManager).setupExperimentContext(contextCaptor.capture());

        when(modelConfigurationProvider.provide())
                .thenThrow(new RuntimeException("Configuration error"));

        // Execute
        BidRequestEvaluatorOutput output = evaluator.evaluate(input);

        // Verify default response
        assertNotNull(output);
        assertEquals(DEFAULT_RESPONSE, output.getResponse());
        EvaluationContext capturedContext = contextCaptor.getValue();
        List<String> debugInfo = capturedContext.getDebugInfo();
        assertEquals(2, debugInfo.size());
        assertEquals("""
                [Error] Error while loading model configuration.
                Configuration error
                """, debugInfo.get(0));
        assertEquals("""
                [Error] Error while evaluating bid request.
                Error while loading model configuration
                """, debugInfo.get(1));
    }

    @Test
    void testEvaluateWithMissingModelDefinition() {
        // Prepare test data
        String openRtbRequest = "{\"id\":\"" + REQUEST_ID + "\"}";
        BidRequestEvaluatorInput input = BidRequestEvaluatorInput.builder()
                .openRtbRequest(openRtbRequest)
                .build();
        modelConfiguration.setModelDefinitionByIdentifier(new HashMap<>());

        // Setup mocks
        ArgumentCaptor<EvaluationContext> contextCaptor = ArgumentCaptor.forClass(EvaluationContext.class);
        doAnswer(invocation -> {
            EvaluationContext context = invocation.getArgument(0);
            context.setExperimentContext(experimentContext);
            return null;
        }).when(experimentManager).setupExperimentContext(contextCaptor.capture());

        when(modelConfigurationProvider.provide()).thenReturn(modelConfiguration);

        // Execute
        BidRequestEvaluatorOutput output = evaluator.evaluate(input);

        // Verify default response
        assertNotNull(output);
        assertEquals(DEFAULT_RESPONSE, output.getResponse());
        EvaluationContext capturedContext = contextCaptor.getValue();
        List<String> debugInfo = capturedContext.getDebugInfo();
        assertEquals(2, debugInfo.size());
        assertEquals("""
                [Error] Error while finding the definition of model adsp_low-value_v2 registered in the experiment.
                """, debugInfo.get(0));
        assertEquals("""
                [Error] Error while evaluating bid request.
                Error while finding the definition of model adsp_low-value_v2 registered in the experiment.
                """, debugInfo.get(1));
    }

    @Override
    protected Class<?> getResourceClass() {
        return BidRequestEvaluatorOnRuleBasedModelTest.class;
    }
}
