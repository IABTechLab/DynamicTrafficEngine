// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import com.amazon.demanddriventrafficevaluator.evaluation.experiment.ExperimentContext;
import com.amazon.demanddriventrafficevaluator.evaluation.experiment.ExperimentManager;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelConfiguration;
import com.amazon.demanddriventrafficevaluator.repository.entity.ModelDefinition;
import com.amazon.demanddriventrafficevaluator.repository.provider.configuration.ConfigurationProvider;
import com.amazon.demanddriventrafficevaluator.util.ResponseUtil;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import static com.amazon.demanddriventrafficevaluator.util.ResponseUtil.EXTENSION_KEYWORD_DECISION;
import static com.amazon.demanddriventrafficevaluator.util.ResponseUtil.EXTENSION_KEYWORD_LEARNING;

/**
 * This class implements the BidRequestEvaluator interface to evaluate bid requests
 * based on rule-based models.
 * <p>
 * It processes incoming bid requests, applies model evaluations, and generates
 * appropriate responses. The class utilizes various components such as experiment
 * management, model configuration, and result aggregation to do the evaluation.
 * </p>
 */
@Log4j2
public class BidRequestEvaluatorOnRuleBasedModel implements BidRequestEvaluator {

    private static final double DEFAULT_FILTER_RECOMMENDATION = 1.0;
    private static final int DEFAULT_LEARNING = 1;
    private static final String EMPTY_JSON_STRING = "{}";
    static final Configuration DOCUMENT_CONFIGURATION = Configuration.builder().build().addOptions(Option.ALWAYS_RETURN_LIST);

    static final Response DEFAULT_RESPONSE = Response.builder()
            .slots(List.of(Slot.builder()
                    .filterDecision(DEFAULT_FILTER_RECOMMENDATION)
                    .ext(ResponseUtil.buildExtension(Map.of(EXTENSION_KEYWORD_DECISION, DEFAULT_FILTER_RECOMMENDATION)))
                    .build()))
            .ext(ResponseUtil.buildExtension(Map.of(EXTENSION_KEYWORD_LEARNING, DEFAULT_LEARNING)))
            .build();

    private final String sspIdentifier;
    private final ExperimentManager experimentManager;
    private final ConfigurationProvider<ModelConfiguration> modelConfigurationProvider;
    private final ModelEvaluator modelEvaluator;
    private final ModelEvaluationResultsAggregator modelEvaluationResultsAggregator;

    public BidRequestEvaluatorOnRuleBasedModel(
            String sspIdentifier,
            ExperimentManager experimentManager,
            ConfigurationProvider<ModelConfiguration> modelConfigurationProvider,
            ModelEvaluator modelEvaluator,
            ModelEvaluationResultsAggregator modelEvaluationResultsAggregator
    ) {
        this.sspIdentifier = sspIdentifier;
        this.experimentManager = experimentManager;
        this.modelConfigurationProvider = modelConfigurationProvider;
        this.modelEvaluator = modelEvaluator;
        this.modelEvaluationResultsAggregator = modelEvaluationResultsAggregator;
    }

    /**
     * Evaluates a bid request and produces an evaluation output.
     * <p>
     * This method processes the input bid request by setting up the evaluation context,
     * applying model definitions, evaluating models, aggregating results, and building
     * a response. If an error occurs during evaluation, a default response is returned.
     * </p>
     *
     * @param input The BidRequestEvaluatorInput containing the bid request to evaluate.
     * @return A BidRequestEvaluatorOutput containing the evaluation response.
     */
    @Override
    public BidRequestEvaluatorOutput evaluate(BidRequestEvaluatorInput input) {
        EvaluationContext evaluationContext = new EvaluationContext();
        try {
            boolean validInput = setupEvaluationContext(input, evaluationContext);
            if (!validInput) {
                return BidRequestEvaluatorOutput.builder()
                        .response(DEFAULT_RESPONSE)
                        .build();
            }

            setupRequestId(evaluationContext);
            experimentManager.setupExperimentContext(evaluationContext);
            List<ModelDefinition> modelDefinitions = getModelDefinitions(evaluationContext);
            log.debug("modelDefinitions: {}", modelDefinitions);

            List<ModelEvaluatorOutput> modelEvaluatorOutputs = new ArrayList<>(modelDefinitions.size());
            for (ModelDefinition modelDefinition: modelDefinitions) {
                modelEvaluatorOutputs.add(modelEvaluator.evaluate(
                        ModelEvaluatorInput.builder()
                            .context(new ModelEvaluationContext(evaluationContext))
                            .modelDefinition(modelDefinition)
                            .build()));
            }

            log.debug("modelEvaluatorOutputs: {}", modelEvaluatorOutputs);
            evaluationContext.setModelEvaluatorOutputs(modelEvaluatorOutputs);
            AggregatedModelEvaluationResult aggregatedModelEvaluationResult = modelEvaluationResultsAggregator.aggregate(
                    evaluationContext);
            log.debug("aggregatedModelEvaluationResult: {}", aggregatedModelEvaluationResult);
            evaluationContext.setAggregatedModelEvaluationResult(aggregatedModelEvaluationResult);
            return BidRequestEvaluatorOutput.builder()
                    .response(buildResponse(evaluationContext))
                    .build();
        } catch (Exception e) {
            evaluationContext.addError("Error while evaluating bid request.\n" + e.getMessage());
            log.error("Error while evaluating bid request", e);
            return BidRequestEvaluatorOutput.builder()
                    .response(DEFAULT_RESPONSE)
                    .build();
        }
    }

    private boolean setupEvaluationContext(BidRequestEvaluatorInput input, EvaluationContext evaluationContext) {
        boolean validInput = false;
        String rawOpenRtbRequest = input.getOpenRtbRequest();
        Map<String, List<String>> openRtbRequestMap = input.getOpenRtbRequestMap();

        if (rawOpenRtbRequest != null && !rawOpenRtbRequest.isEmpty() && !rawOpenRtbRequest.equals(EMPTY_JSON_STRING)) {
            log.debug("Using json string openRTB input");
            DocumentContext openRtbRequestContext = JsonPath.parse(input.getOpenRtbRequest(), DOCUMENT_CONFIGURATION);
            OpenRtbRequestContextJsonDocument openRtbRequestContextJsonDocument = new OpenRtbRequestContextJsonDocument();
            openRtbRequestContextJsonDocument.setOpenRtbRequestContext(openRtbRequestContext);
            evaluationContext.setOpenRtbRequestContext(openRtbRequestContextJsonDocument);
            validInput = true;
        } else if (openRtbRequestMap != null && !openRtbRequestMap.isEmpty()) {
            log.debug("Using map openRTB input");
            OpenRtbRequestContextMap openRtbRequestContextMap = new OpenRtbRequestContextMap();
            openRtbRequestContextMap.setOpenRtbRequestContext(openRtbRequestMap);
            evaluationContext.setOpenRtbRequestContext(openRtbRequestContextMap);
            validInput = true;
        } else {
            evaluationContext.addError("No valid OpenRTB input was provided.");
            log.error("No valid OpenRTB input was provided.");
        }

        return validInput;
    }

    private void setupRequestId(EvaluationContext context) {
        try {
            String id = context.getOpenRtbRequestContext().findPath("$.id").get(0);
            if (StringUtils.isEmpty(id) || id.equals("null")) {
                throw new PathNotFoundException();
            }
            context.setRequestId(id);
        } catch (PathNotFoundException e) {
            String randomId = UUID.randomUUID().toString();
            context.addDebug("Could not find id from OpenRtbRequest and use self generated UUID instead. Generated id: " + randomId);
            context.setRequestId(randomId);
        }
    }

    private List<ModelDefinition> getModelDefinitions(EvaluationContext context) {
        ExperimentContext experimentContext = context.getExperimentContext();
        List<String> modelsInExperiment = experimentContext.getModelIdentifiers();
        Map<String, ModelDefinition> modelDefinitionByIdentifier;
        try {
            ModelConfiguration modelConfiguration = modelConfigurationProvider.provide();
            modelDefinitionByIdentifier = modelConfiguration.getModelDefinitionByIdentifier();
        } catch (Exception e) {
            context.addError("Error while loading model configuration.\n" + e.getMessage());
            throw new IllegalStateException("Error while loading model configuration", e);
        }
        List<ModelDefinition> modelDefinitions = new ArrayList<>(modelsInExperiment.size());
        for (String modelIdentifier : modelsInExperiment) {
            if (!modelDefinitionByIdentifier.containsKey(modelIdentifier)) {
                context.addError("Error while finding the definition of model " + modelIdentifier + " registered in the experiment.");
                throw new IllegalStateException("Error while finding the definition of model " + modelIdentifier + " registered in the experiment.");
            }
            modelDefinitions.add(modelDefinitionByIdentifier.get(modelIdentifier));
        }
        return modelDefinitions;
    }

    private Response buildResponse(EvaluationContext context) {
        AggregatedModelEvaluationResult aggregatedModelEvaluationResult = context.getAggregatedModelEvaluationResult();
        return Response.builder()
                .slots(ResponseUtil.buildSlots(context))
                .ext(ResponseUtil.buildExtension(Map.of(EXTENSION_KEYWORD_LEARNING, aggregatedModelEvaluationResult.getTreatmentCodeInInt())))
                .build();
    }
}
