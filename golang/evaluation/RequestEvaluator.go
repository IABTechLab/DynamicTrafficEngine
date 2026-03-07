// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package evaluation

import (
	"encoding/json"
	"fmt"
	"math"
	"runtime/debug"
	"slices"
	"strings"

	"github.com/buger/jsonparser"
	"github.com/google/uuid"
	"github.com/rs/zerolog"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/modelfeature"
	"golang.a2z.com/demanddriventrafficevaluator/util"
)

var Logger zerolog.Logger

func init() {
	Logger = util.GetLogger()
	util.WithComponent("evaluation")
}

const (
	ExtensionKeywordDecision   = "decision"
	ExtensionKeywordLearning   = "learning"
	ExtensionKeywordAmazonTest = "amazontest"
)

var (
	DefaultFilterRecommendation = float32(1.0)
	DefaultLearning             = 0
	DefaultResponse             = Response{
		Slots: []Slot{{
			FilterDecision: DefaultFilterRecommendation,
			Ext:            buildExtension(map[string]any{ExtensionKeywordDecision: DefaultFilterRecommendation}),
		}},
		Ext: buildExtension(map[string]any{ExtensionKeywordLearning: DefaultLearning}),
	}
)

// RequestEvaluator provides a filter recommendation for OpenRTB requests based on model evaluation(s).
type RequestEvaluator struct {
	sspIdentifier             string
	trafficAllocator          interfaces.TrafficAllocatorInterface
	modelEvaluator            interfaces.ModelEvaluator
	modelConfigurationHandler interfaces.ModelConfigurationHandlerInterface
}

// Input to RequestEvaluator. At least one of OpenRtbRequest and OpenRtbRequestMap must be present.
//
// If both are present, OpenRtbRequestMap is used to reduce JSON parsing.
type BidRequestEvaluatorInput struct {
	// Raw OpenRTB request, in JSON format.
	OpenRtbRequest string

	// Abridged OpenRTB request, as a Map of string -> string. The keys are the path of the field,
	// in dot notation described in JsonPath, and the values are the the string value of the field.
	OpenRtbRequestMap map[string]string
}

// Output of Request Evaluator. Provides overall filter recommendation, as well as extensions to be
// populated in the OpenRTB request forwarded downstream.
type BidRequestEvaluatorOutput struct {
	// Filter recommendation for Amazon Ads.
	Response Response
}

type Response struct {
	//  Evaluation of signals for each slot (imp object) of the incoming bid request
	Slots []Slot

	// An SSP is expected to add this JSON blob to the ext field in the root level object of the OpenRTB request
	// that they forward to Amazon Ads. This field contains information on whether the evaluator internally
	// assigned the request to treatment (learning=0) or control (learning=1).
	//
	// Example: "amazontest": {"learning": 1}
	Ext string
}

type Slot struct {
	// Recommended filter decision for the slot based on Amazon Ads signal(s). This is a value ranging from 0.0 to 1.0,
	// where 0.0 indicates no probability of getting response from Amazon Ads, and 1.0 indicates highest probability to get a response from Amazon Ads.
	FilterDecision float32

	// An SSP is expected to add this json blob to the ext field in the imp object of the oRTB request that they forward to Amazon Ads.
	// This field contains information about the decision taken by the evaluator internally.
	//
	// Example: "amazontest": {"decision": 0.0}
	Ext string
}

func NewRequestEvaluator(sspIdentifier string, trafficAllocator interfaces.TrafficAllocatorInterface, modelEvaluator interfaces.ModelEvaluator, modelConfigurationHandler interfaces.ModelConfigurationHandlerInterface) *RequestEvaluator {
	return &RequestEvaluator{
		sspIdentifier:             sspIdentifier,
		trafficAllocator:          trafficAllocator,
		modelEvaluator:            modelEvaluator,
		modelConfigurationHandler: modelConfigurationHandler,
	}
}

// For a given OpenRTB request, returns an overall filter recommendation for each impression object in the request,
// as well as a learning value for performance and model training.
func (b *RequestEvaluator) Evaluate(requestInput *BidRequestEvaluatorInput) (output *BidRequestEvaluatorOutput) {
	requestId := uuid.New().String()
	context := interfaces.NewContext()
	context.RequestId = requestId

	// Check if requestInput is null, return default response
	if requestInput == nil {
		Logger.Info().Msg("requestInput is null, returning default response")
		return &BidRequestEvaluatorOutput{
			Response: DefaultResponse,
		}
	}

	openRtbRequest := requestInput.OpenRtbRequest
	context.OpenRtbRequest = openRtbRequest

	Logger.Debug().Msgf("Evaluating request: %v", openRtbRequest)
	defer func() {
		if r := recover(); r != nil {
			debug.PrintStack()
			Logger.Debug().Msgf("Error while evaluating the request: %v", r)
			output = &BidRequestEvaluatorOutput{
				Response: DefaultResponse,
			}
		}
	}()

	trafficAllocationContext := b.trafficAllocator.GetTrafficAllocationContext()
	Logger.Debug().Msgf("trafficAllocationContext: %+v", trafficAllocationContext)
	context.TrafficAllocationContext = trafficAllocationContext

	externalFields := []string{"$.id"}
	var requestFieldValueMap map[string]string
	var err error
	if openRtbRequest != "" {
		Logger.Debug().Msgf("Using raw OpenRtbRequest string")
		requestFieldValueMap, err = b.parse(openRtbRequest, externalFields)
		if err != nil {
			Logger.Error().Msgf("fail to parse openRtbRequest due to %+v", err)
			context.AddError(fmt.Sprintf("fail to parse openRtbRequest due to %+v\n return the default response", err))
			return &BidRequestEvaluatorOutput{
				Response: DefaultResponse,
			}
		}
	} else if len(requestInput.OpenRtbRequestMap) > 0 {
		// SSP has already extracted the necessary fields for DTE evaluation into map, add missing fields
		Logger.Debug().Msgf("Using OpenRtbRequest Map")
		requestFieldValueMap, err = b.addMissingEntriesToMap(requestInput.OpenRtbRequestMap)
		if err != nil {
			Logger.Error().Msgf("fail to augment openRtbRequestMap due to %+v", err)
			context.AddError(fmt.Sprintf("fail to augment openRtbRequestMap due to %+v\n return the default response", err))
			return &BidRequestEvaluatorOutput{
				Response: DefaultResponse,
			}
		}
	} else {
		Logger.Info().Msgf("No valid openRtbRequest string or map was provided, returning default response")
		return &BidRequestEvaluatorOutput{
			Response: DefaultResponse,
		}
	}

	b.setupOpenRtbRequestID(context, requestFieldValueMap)
	modelDefinitions, err := b.getModelDefinitions(context)
	if err != nil {
		Logger.Error().Msgf("fail to get model definitions due to %+v\n return the default response", err)
		return &BidRequestEvaluatorOutput{
			Response: DefaultResponse,
		}
	}
	var modelEvaluatorOutputs []interfaces.ModelEvaluatorOutput
	for _, modelDefinition := range modelDefinitions {
		modelEvaluatorOutput, err := b.modelEvaluator.Evaluate(interfaces.ModelEvaluatorInput{
			Context:              context,
			OpenRtbRequest:       openRtbRequest,
			ModelDefinition:      &modelDefinition,
			FeatureFieldValueMap: requestFieldValueMap,
		})
		if err == nil {
			modelEvaluatorOutputs = append(modelEvaluatorOutputs, *modelEvaluatorOutput)
		} else {
			Logger.Error().Msgf("Error while evaluating the model [%+v]: %+v", modelDefinition.Identifier, err)
		}
	}
	Logger.Debug().Msgf("modelEvaluatorOutputs: %+v", modelEvaluatorOutputs)
	if len(modelEvaluatorOutputs) == 0 {
		Logger.Error().Msgf("no model evaluator outputs\n return the default response")
		return &BidRequestEvaluatorOutput{
			Response: DefaultResponse,
		}
	}
	context.ModelEvaluatorOutputs = modelEvaluatorOutputs
	aggregatedModelEvaluationResult, err := b.aggregateModelEvaluationResultsOnMax(context)
	if err != nil {
		Logger.Error().Msgf("fail to aggregate model evaluation results due to %+v\n return the default response", err)
		return &BidRequestEvaluatorOutput{
			Response: DefaultResponse,
		}
	}
	context.AggregatedModelEvaluationResult = aggregatedModelEvaluationResult
	output = &BidRequestEvaluatorOutput{
		Response: b.buildResponse(context),
	}
	return output
}

func (b *RequestEvaluator) setupOpenRtbRequestID(context *interfaces.Context, requestFieldValueMap map[string]string) {
	requestID, exists := requestFieldValueMap["$.id"]
	if !exists {
		Logger.Debug().Msgf("Could not find id from OpenRtbRequest and use self generated UUID instead.")
		context.AddDebug("Could not find id from OpenRtbRequest and use self generated UUID instead.")
		requestID = "unknown"
	}
	context.OpenRtbRequestId = requestID
}

func (b *RequestEvaluator) addMissingEntriesToMap(openRtbRequestMap map[string]string) (map[string]string, error) {
	uniqueFeatureFields, err := b.modelConfigurationHandler.GetAllUniqueFeatureFields()
	if err != nil {
		return nil, fmt.Errorf("fail to augment openRtbRequestMap due to %v", err)
	}
	Logger.Debug().Msgf("uniqueFeatureFields: %v", uniqueFeatureFields)
	var fieldValueMap = make(map[string]string)
	for _, field := range uniqueFeatureFields {
		value, exists := openRtbRequestMap[field]
		if exists {
			fieldValueMap[field] = value
		} else {
			fieldValueMap[field] = ""
			Logger.Debug().Msgf("field [%v] is not found", field)
		}
	}
	return fieldValueMap, nil
}

// Extract values of all unique fields of all model features.
func (b *RequestEvaluator) parse(openRtbRequest string, externalFields []string) (map[string]string, error) {
	uniqueFeatureFields, err := b.modelConfigurationHandler.GetAllUniqueFeatureFields()
	if err != nil {
		return nil, fmt.Errorf("fail to extract openRtbRequest due to %v", err)
	}
	uniqueFeatureFields = append(uniqueFeatureFields, externalFields...)
	Logger.Debug().Msgf("uniqueFeatureFields: %v", uniqueFeatureFields)
	var fieldValueMap = make(map[string]string)
	paths := convertFieldsToPaths(uniqueFeatureFields)
	Logger.Debug().Msgf("paths: %v", paths)
	jsonparser.EachKey([]byte(openRtbRequest), func(idx int, value []byte, vt jsonparser.ValueType, err error) {
		var str string
		switch vt {
		case jsonparser.String:
			str = string(value)
		default:
			str = string(value)
		}
		fieldValueMap[convertPathsToField(paths[idx])] = str
	}, paths...)
	Logger.Debug().Msgf("fieldValueMap: %v", fieldValueMap)
	// Required as JSON Parser forEach doesn't call the iterator function for non-existing keys in JSON
	for _, field := range uniqueFeatureFields {
		_, exists := fieldValueMap[field]
		if !exists {
			fieldValueMap[field] = ""
			Logger.Debug().Msgf("field [%v] is not found", field)
		}
	}
	return fieldValueMap, nil
}

func (b *RequestEvaluator) getModelDefinitions(context *interfaces.Context) ([]interfaces.ModelDefinition, error) {
	modelConfiguration, err := b.modelConfigurationHandler.Provide()
	if err != nil {
		context.AddError(fmt.Sprintf("error while providing model configuration: %v", err))
		return nil, fmt.Errorf("error while providing model configuration: %v", err)
	}
	modelDefinitionByIdentifier := modelConfiguration.ModelDefinitionByIdentifier

	trafficAllocationContext := context.TrafficAllocationContext
	modelsInExperiment := trafficAllocationContext.GetModelIdentifiers()

	var modelDefinitions []interfaces.ModelDefinition
	for _, model := range modelsInExperiment {
		modelDefinition, exist := modelDefinitionByIdentifier[model]
		if !exist {
			return nil, fmt.Errorf("error while finding the definition of model [%s] registered in the experiment", model)
		}
		modelDefinitions = append(modelDefinitions, modelDefinition)
	}
	return modelDefinitions, nil
}

func (b *RequestEvaluator) aggregateModelEvaluationResultsOnMax(context *interfaces.Context) (*interfaces.AggregatedModelEvaluationResult, error) {
	modelEvaluatorOutputs := context.ModelEvaluatorOutputs
	trafficAllocationContext := context.TrafficAllocationContext
	experimentDef, err := trafficAllocationContext.GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter)
	if err != nil {
		return nil, fmt.Errorf("error while aggregating model evaluation results on Max due to [%+v]", err)
	}
	experimentName := experimentDef.Name
	modelsByExperiment := trafficAllocationContext.GetModelsByExperiment()
	modelsInExperiment, exists := modelsByExperiment[experimentName]
	if !exists {
		return nil, fmt.Errorf("error while aggregating model evaluation results on Max since no models in the experiment [%s]", experimentName)
	}
	var maxScore float32 = -math.MaxFloat32

	for _, output := range modelEvaluatorOutputs {
		if output.Status == interfaces.ModelEvaluationStatusSuccess && slices.Contains(modelsInExperiment, output.ModelDefinition.Identifier) {
			if output.ModelResult.Value > maxScore {
				maxScore = output.ModelResult.Value
			}
		}
	}
	if maxScore == -math.MaxFloat32 {
		return nil, fmt.Errorf("no models have been evaluated for the experiment [%s]", experimentName)
	}

	treatmentCodeInInt := trafficAllocationContext.GetTreatmentCodeInInt(experimentName)
	aggregatedScoreWithTreatment := float32(math.Max(float64(maxScore), float64(treatmentCodeInInt)))
	treatmentCode := trafficAllocationContext.GetTreatmentCode(experimentName)
	return &interfaces.AggregatedModelEvaluationResult{
		ExperimentName:     "DemandDrivenTrafficEvaluatorSoftFilter",
		ExperimentType:     "soft-filter",
		TreatmentCode:      treatmentCode,
		TreatmentCodeInInt: treatmentCodeInInt,
		Score:              maxScore,
		ScoreWithTreatment: aggregatedScoreWithTreatment,
		AggregationType:    "max",
	}, nil
}

func (b *RequestEvaluator) buildResponse(context *interfaces.Context) Response {
	aggregatedModelEvaluationResult := context.AggregatedModelEvaluationResult
	slots := buildSlots(context)
	extension := buildExtension(map[string]any{ExtensionKeywordLearning: aggregatedModelEvaluationResult.TreatmentCodeInInt})
	return Response{
		Slots: slots,
		Ext:   extension,
	}
}

func convertFieldsToPaths(fields []string) [][]string {
	// Remove "$." prefix if present and add delimiter "." around "[]"
	var paths [][]string
	for _, field := range fields {
		field = strings.TrimPrefix(field, "$.")
		paths = append(paths, strings.Split(strings.ReplaceAll(field, "[", ".["), "."))
	}
	return paths
}

func convertPathsToField(paths []string) string {
	return "$." + strings.ReplaceAll(strings.Join(paths, "."), ".[", "[")
}

func buildSlots(context *interfaces.Context) []Slot {
	aggregatedModelEvaluationResult := context.AggregatedModelEvaluationResult
	return []Slot{
		{
			FilterDecision: aggregatedModelEvaluationResult.ScoreWithTreatment,
			Ext:            buildExtension(map[string]interface{}{ExtensionKeywordDecision: aggregatedModelEvaluationResult.Score}),
		},
	}
}

func buildExtension(extensionMapping map[string]any) string {
	rootNode := make(map[string]any)
	dsp := make(map[string]any)
	rootNode[ExtensionKeywordAmazonTest] = dsp

	for key, value := range extensionMapping {
		dsp[key] = value
	}

	jsonBytes, _ := json.Marshal(rootNode)
	return string(jsonBytes)
}
