// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import com.amazon.demanddriventrafficevaluator.BaseTestCase;
import com.amazon.demanddriventrafficevaluator.evaluation.experiment.ExperimentContext;
import com.amazon.demanddriventrafficevaluator.evaluation.experiment.ExperimentManager;
import com.amazon.demanddriventrafficevaluator.repository.entity.ExperimentConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelResult;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ConfigurationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.utils.ImmutableMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluatorOnRuleBasedModel.DEFAULT_RESPONSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for the opt-in {@link BidRequestEvaluatorOutput#getModelEvaluations()} summary.
 */
@ExtendWith(MockitoExtension.class)
class BidRequestEvaluatorModelEvaluationsTest extends BaseTestCase {

    private static final String OPEN_RTB_REQUEST = "{\"id\":\"test-request-id\"}";

    @Mock
    private ExperimentManager experimentManager;
    @Mock
    private ConfigurationProvider<ModelConfiguration> modelConfigurationProvider;
    @Mock
    private ModelEvaluator modelEvaluator;
    @Mock
    private ModelEvaluationResultsAggregator modelEvaluationResultsAggregator;

    private BidRequestEvaluatorOnRuleBasedModel evaluator;
    private ModelConfiguration modelConfiguration;
    private ExperimentContext experimentContext;

    @BeforeEach
    void setUp() {
        evaluator = new BidRequestEvaluatorOnRuleBasedModel(
                "test-ssp", experimentManager, modelConfigurationProvider, modelEvaluator,
                modelEvaluationResultsAggregator);
        ExperimentConfiguration experimentConfiguration = readJsonResourceAsPojo(
                "/test/ExperimentConfigurationTwoModels.json", ExperimentConfiguration.class);
        modelConfiguration = readJsonResourceAsPojo(
                "/test/ModelConfigurationTwoModels.json", ModelConfiguration.class);
        experimentContext = new ExperimentContext(ImmutableMap.of("test-experiment", "T"), experimentConfiguration);
    }

    private void stubPipeline(ModelEvaluatorOutput first, ModelEvaluatorOutput... rest) {
        doAnswer(invocation -> {
            EvaluationContext context = invocation.getArgument(0);
            context.setExperimentContext(experimentContext);
            return null;
        }).when(experimentManager).setupExperimentContext(any(EvaluationContext.class));
        when(modelConfigurationProvider.provide()).thenReturn(modelConfiguration);
        when(modelEvaluator.evaluate(any(ModelEvaluatorInput.class))).thenReturn(first, rest);
        when(modelEvaluationResultsAggregator.aggregate(any(EvaluationContext.class)))
                .thenReturn(AggregatedModelEvaluationResult.builder()
                        .score(0.0).scoreWithTreatment(1.0).treatmentCodeInInt(1).build());
    }

    private ModelEvaluatorOutput output(String identifier, ModelEvaluationStatus status, ModelResult result) {
        return ModelEvaluatorOutput.builder()
                .status(status)
                .modelDefinition(modelConfiguration.getModelDefinitionByIdentifier().get(identifier))
                .modelResult(result)
                .build();
    }

    private List<String> modelsInEvaluationOrder() {
        return experimentContext.getModelIdentifiers();
    }

    @Test
    void includeDetails_defaultsToFalse() {
        assertFalse(BidRequestEvaluatorInput.builder().openRtbRequest(OPEN_RTB_REQUEST).build().isIncludeDetails());
        assertTrue(BidRequestEvaluatorInput.builder().includeDetails(true).build().isIncludeDetails());
    }

    @Test
    void includeDetailsOff_mappingNotReached_sharedEmptyList() {
        // A mock output records every getter call; the mapping would read status, definition
        // and result, while the mocked aggregator and response building read none of them.
        ModelEvaluatorOutput first = mock(ModelEvaluatorOutput.class);
        ModelEvaluatorOutput second = mock(ModelEvaluatorOutput.class);
        stubPipeline(first, second);

        BidRequestEvaluatorOutput result = evaluator.evaluate(
                BidRequestEvaluatorInput.builder().openRtbRequest(OPEN_RTB_REQUEST).build());

        verifyNoInteractions(first, second);
        assertSame(List.of(), result.getModelEvaluations());
        assertEquals(1.0, result.getResponse().getSlots().get(0).getFilterDecision());
    }

    @Test
    void includeDetails_summarizesEveryModelInEvaluationOrder() {
        List<String> order = modelsInEvaluationOrder();
        assertEquals(2, order.size());
        stubPipeline(
                output(order.get(0), ModelEvaluationStatus.SUCCESS, ModelResult.builder()
                        .value(0.0)
                        .values(new ArrayList<>(List.of(0.0)))
                        .keys(new ArrayList<>(List.of("k0")))
                        .build()),
                output(order.get(1), ModelEvaluationStatus.ERROR, null));

        BidRequestEvaluatorOutput result = evaluator.evaluate(BidRequestEvaluatorInput.builder()
                .openRtbRequest(OPEN_RTB_REQUEST)
                .includeDetails(true)
                .build());

        List<ModelEvaluation> evaluations = result.getModelEvaluations();
        assertEquals(2, evaluations.size());
        assertEquals(order.get(0), evaluations.get(0).getIdentifier());
        assertEquals(ModelEvaluationStatus.SUCCESS, evaluations.get(0).getStatus());
        assertEquals(Double.valueOf(0.0), evaluations.get(0).getValue());
        assertEquals(List.of("k0"), evaluations.get(0).getKeys());
        assertEquals(order.get(1), evaluations.get(1).getIdentifier());
        assertEquals(ModelEvaluationStatus.ERROR, evaluations.get(1).getStatus());
        assertNull(evaluations.get(1).getValue());
        assertThrows(UnsupportedOperationException.class, () -> evaluations.remove(0));
        // The response is the same as without includeDetails.
        assertEquals(1.0, result.getResponse().getSlots().get(0).getFilterDecision());
        assertEquals(1, result.getResponse().getLearning());
    }

    @Test
    void includeDetails_timeoutFromCustomEvaluatorIsSummarized() {
        // No built-in evaluator returns TIMEOUT; a custom ModelEvaluator may.
        List<String> order = modelsInEvaluationOrder();
        stubPipeline(
                output(order.get(0), ModelEvaluationStatus.TIMEOUT, null),
                output(order.get(1), ModelEvaluationStatus.SUCCESS,
                        ModelResult.builder().value(1.0).build()));

        List<ModelEvaluation> evaluations = evaluator.evaluate(BidRequestEvaluatorInput.builder()
                .openRtbRequest(OPEN_RTB_REQUEST)
                .includeDetails(true)
                .build()).getModelEvaluations();

        assertEquals(ModelEvaluationStatus.TIMEOUT, evaluations.get(0).getStatus());
        assertNull(evaluations.get(0).getValue());
        assertEquals(Double.valueOf(1.0), evaluations.get(1).getValue());
        assertTrue(evaluations.get(1).getValues().isEmpty());
    }

    @Test
    void includeDetails_nullOutputFromCustomEvaluatorIsSkipped() {
        List<String> order = modelsInEvaluationOrder();
        stubPipeline(null, output(order.get(1), ModelEvaluationStatus.SUCCESS,
                ModelResult.builder().value(1.0).build()));

        List<ModelEvaluation> evaluations = evaluator.evaluate(BidRequestEvaluatorInput.builder()
                .openRtbRequest(OPEN_RTB_REQUEST)
                .includeDetails(true)
                .build()).getModelEvaluations();

        assertEquals(1, evaluations.size());
        assertEquals(order.get(1), evaluations.get(0).getIdentifier());
    }

    @Test
    void includeDetails_mappingFailureSkipsOnlyThatOutputAndKeepsResponse() {
        ModelEvaluatorOutput broken = mock(ModelEvaluatorOutput.class);
        when(broken.getStatus()).thenThrow(new IllegalStateException("boom"));
        String good = modelsInEvaluationOrder().get(1);
        stubPipeline(broken, output(good, ModelEvaluationStatus.SUCCESS, ModelResult.builder().value(1.0).build()));

        BidRequestEvaluatorOutput result = evaluator.evaluate(BidRequestEvaluatorInput.builder()
                .openRtbRequest(OPEN_RTB_REQUEST)
                .includeDetails(true)
                .build());

        assertEquals(1, result.getModelEvaluations().size());
        assertEquals(good, result.getModelEvaluations().get(0).getIdentifier());
        // The whole Response is the one the stubbed aggregation yields, as without includeDetails.
        assertEquals(Response.builder()
                .slots(List.of(Slot.builder().filterDecision(1.0).decision(0.0).build()))
                .learning(1)
                .build(), result.getResponse());
    }

    @Test
    void includeDetails_invalidInputFallbackReturnsEmptyList() {
        BidRequestEvaluatorOutput result = evaluator.evaluate(BidRequestEvaluatorInput.builder()
                .openRtbRequest("{}")
                .includeDetails(true)
                .build());

        assertEquals(DEFAULT_RESPONSE, result.getResponse());
        assertSame(List.of(), result.getModelEvaluations());
    }

    @Test
    void includeDetails_exceptionFallbackReturnsEmptyList() {
        doAnswer(invocation -> {
            EvaluationContext context = invocation.getArgument(0);
            context.setExperimentContext(experimentContext);
            return null;
        }).when(experimentManager).setupExperimentContext(any(EvaluationContext.class));
        when(modelConfigurationProvider.provide()).thenThrow(new RuntimeException("Configuration error"));

        BidRequestEvaluatorOutput result = evaluator.evaluate(BidRequestEvaluatorInput.builder()
                .openRtbRequest(OPEN_RTB_REQUEST)
                .includeDetails(true)
                .build());

        assertEquals(DEFAULT_RESPONSE, result.getResponse());
        assertSame(List.of(), result.getModelEvaluations());
    }

    @Test
    void outputBuiltWithoutField_returnsEmptyList() {
        BidRequestEvaluatorOutput result = BidRequestEvaluatorOutput.builder().response(DEFAULT_RESPONSE).build();

        assertSame(List.of(), result.getModelEvaluations());
        assertTrue(BidRequestEvaluatorOutput.builder().response(DEFAULT_RESPONSE).modelEvaluations(null).build()
                .getModelEvaluations().isEmpty());
    }

    @Test
    void outputBuilder_copiesList() {
        List<ModelEvaluation> evaluations = new ArrayList<>(List.of(
                ModelEvaluation.builder().identifier("a").build()));
        BidRequestEvaluatorOutput result = BidRequestEvaluatorOutput.builder()
                .response(DEFAULT_RESPONSE)
                .modelEvaluations(evaluations)
                .build();
        evaluations.clear();

        assertEquals(1, result.getModelEvaluations().size());
        assertThrows(UnsupportedOperationException.class, () -> result.getModelEvaluations().clear());

        BidRequestEvaluatorOutput withNull = BidRequestEvaluatorOutput.builder()
                .response(DEFAULT_RESPONSE)
                .modelEvaluations(Arrays.asList(ModelEvaluation.builder().identifier("a").build(), null))
                .build();
        assertEquals(1, withNull.getModelEvaluations().size());
    }

    @Override
    protected Class<?> getResourceClass() {
        return BidRequestEvaluatorModelEvaluationsTest.class;
    }
}
