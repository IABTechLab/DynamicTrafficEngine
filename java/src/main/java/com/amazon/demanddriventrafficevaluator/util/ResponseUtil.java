// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.util;

import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.AggregatedModelEvaluationResult;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.EvaluationContext;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluationContext;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.ModelEvaluatorOutput;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.Signal;
import com.amazon.demanddriventrafficevaluator.evaluation.evaluator.Slot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;

/**
 * A utility class for building and managing response-related data structures.
 * <p>
 * This class provides static methods to construct various components of a response,
 * including slots, extensions, signals, and debug information. It uses Jackson's
 * ObjectMapper for JSON processing.
 * </p>
 */
@Log4j2
public class ResponseUtil {

    public static final String EXTENSION_KEYWORD_DECISION = "decision";
    public static final String EXTENSION_KEYWORD_LEARNING = "learning";
    private static final String EXTENSION_KEYWORD_AMAZONTEST = "amazontest";

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Builds a list of Slot objects based on the evaluation context.
     *
     * @param context The EvaluationContext containing the aggregated model evaluation result.
     * @return A List containing a single Slot object with filter decision and extension.
     */
    public static List<Slot> buildSlots(EvaluationContext context) {
        AggregatedModelEvaluationResult aggregatedModelEvaluationResult = context.getAggregatedModelEvaluationResult();
        return List.of(Slot.builder()
                .filterDecision(aggregatedModelEvaluationResult.getScoreWithTreatment())
                .ext(buildExtension(Map.of(EXTENSION_KEYWORD_DECISION, aggregatedModelEvaluationResult.getScore())))
                .build());
    }

    /**
     * Builds an extension string from a map of key-value pairs.
     *
     * @param extensionMapping A Map containing extension data to be serialized.
     * @return A JSON string representation of the extension data.
     */
    public static String buildExtension(Map<String, Object> extensionMapping) {
        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode dsp = rootNode.putObject(EXTENSION_KEYWORD_AMAZONTEST);
        for (Map.Entry<String, Object> entry : extensionMapping.entrySet()) {
            dsp.putPOJO(entry.getKey(), entry.getValue());
        }
        try {
            return mapper.writeValueAsString(rootNode);
        } catch (JsonProcessingException e) {
            log.error("Error while serializing extension", e);
            return "";
        }
    }

    /**
     * Builds a list of Signal objects from model evaluator outputs.
     *
     * @param modelEvaluatorOutputs A List of ModelEvaluatorOutput objects.
     * @return A List of Signal objects containing model evaluation information.
     */
    public static List<Signal> buildSignals(List<ModelEvaluatorOutput> modelEvaluatorOutputs) {
        List<Signal> signals = new ArrayList<>(modelEvaluatorOutputs.size());
        for (ModelEvaluatorOutput output : modelEvaluatorOutputs) {
            Signal signal = Signal.builder()
                    .name(output.getModelDefinition().getName())
                    .version(output.getModelDefinition().getVersion())
                    .status(output.getStatus().toString())
                    .build();
            signals.add(signal);
        }
        return signals;
    }

    /**
     * Retrieves debug information from the model evaluation context.
     *
     * @param context The ModelEvaluationContext containing debug information.
     * @return A String containing concatenated request-level and model-level debug information.
     */
    public static String getDebugInfo(ModelEvaluationContext context) {
        StringBuilder requestLevelDebugInfoBuilder = new StringBuilder("[RequestLevelDebugInfo]\n");
        for (String debugInfo : context.getEvaluationContext().getDebugInfo()) {
            requestLevelDebugInfoBuilder.append(debugInfo);
        }
        String requestLevelDebugInfo = requestLevelDebugInfoBuilder.toString();

        StringBuilder modelLevelDebugInfoBuilder = new StringBuilder("[ModelLevelDebugInfo]\n");
        for (String debugInfo : context.getDebugInfo()) {
            modelLevelDebugInfoBuilder.append(debugInfo);
        }
        String modelLevelDebugInfo = modelLevelDebugInfoBuilder.toString();

        return requestLevelDebugInfo + modelLevelDebugInfo;
    }
}
