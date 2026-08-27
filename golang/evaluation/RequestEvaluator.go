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

	"github.com/google/uuid"
	"github.com/ohler55/ojg/jp"
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
	configurableAggregator    *ConfigurableAggregator
}

// Input to RequestEvaluator. At least one of OpenRtbRequest and OpenRtbRequestMap must be present.
//
// If both are present, OpenRtbRequestMap is used to reduce JSON parsing.
type BidRequestEvaluatorInput struct {
	// Raw OpenRTB request, in JSON format.
	OpenRtbRequest string

	// Abridged OpenRTB request, as a Map of string -> []string. The keys are the path of the field,
	// in dot notation described in JsonPath, and the values are string slices of the field values.
	OpenRtbRequestMap map[string][]string
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

func NewRequestEvaluator(sspIdentifier string, trafficAllocator interfaces.TrafficAllocatorInterface, modelEvaluator interfaces.ModelEvaluator, modelConfigurationHandler interfaces.ModelConfigurationHandlerInterface, configurableAggregator *ConfigurableAggregator) *RequestEvaluator {
	return &RequestEvaluator{
		sspIdentifier:             sspIdentifier,
		trafficAllocator:          trafficAllocator,
		modelEvaluator:            modelEvaluator,
		modelConfigurationHandler: modelConfigurationHandler,
		configurableAggregator:    configurableAggregator,
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
	var requestFieldValueMap map[string][]string
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
	// Check if a configurable aggregation schema is defined for the experiment
	experimentDef, expErr := trafficAllocationContext.GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter)
	if expErr != nil {
		Logger.Error().Msgf("fail to get experiment definition due to %+v\n return the default response", expErr)
		return &BidRequestEvaluatorOutput{
			Response: DefaultResponse,
		}
	}
	var aggregatedModelEvaluationResult *interfaces.AggregatedModelEvaluationResult
	if experimentDef.AggregationSchema != nil && b.configurableAggregator != nil {
		aggregatedModelEvaluationResult, err = b.configurableAggregator.Aggregate(experimentDef.AggregationSchema, modelEvaluatorOutputs, context)
	} else {
		aggregatedModelEvaluationResult, err = b.aggregateModelEvaluationResultsOnMax(context)
	}
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

func (b *RequestEvaluator) setupOpenRtbRequestID(context *interfaces.Context, requestFieldValueMap map[string][]string) {
	values, exists := requestFieldValueMap["$.id"]
	var requestID string
	if !exists || len(values) == 0 {
		Logger.Debug().Msgf("Could not find id from OpenRtbRequest and use self generated UUID instead.")
		context.AddDebug("Could not find id from OpenRtbRequest and use self generated UUID instead.")
		requestID = "unknown"
	} else {
		requestID = values[0]
	}
	context.OpenRtbRequestId = requestID
}

func (b *RequestEvaluator) addMissingEntriesToMap(openRtbRequestMap map[string][]string) (map[string][]string, error) {
	uniqueFeatureFields, err := b.modelConfigurationHandler.GetAllUniqueFeatureFields()
	if err != nil {
		return nil, fmt.Errorf("fail to augment openRtbRequestMap due to %v", err)
	}
	Logger.Debug().Msgf("uniqueFeatureFields: %v", uniqueFeatureFields)
	var fieldValueMap = make(map[string][]string)
	for _, field := range uniqueFeatureFields {
		value, exists := openRtbRequestMap[field]
		if exists && value != nil {
			fieldValueMap[field] = value
		} else {
			// A field the SSP did not provide is treated the same as a field that the
			// string parser could not resolve: a definite (normal) path yields a single
			// empty string [""], an indefinite path yields an empty slice []. This keeps
			// the map-input and string-input paths consistent for downstream transformers
			// such as Exists.
			fieldValueMap[field] = emptyValueForField(field)
			Logger.Debug().Msgf("field [%v] is not found", field)
		}
	}
	return fieldValueMap, nil
}

// emptyValueForField returns the placeholder value used when a field cannot be resolved.
// It mirrors extractField's empty-match behavior so the string-input and map-input code
// paths agree: a definite (normal) JSONPath yields [""] (matching the Java findPath
// contract), while an indefinite path (wildcard, filter, union, slice, descent) yields [].
// A field that is not a valid JSONPath expression is treated as indefinite ([]).
func emptyValueForField(field string) []string {
	expr, err := jp.ParseString(field)
	if err != nil {
		return []string{}
	}
	if expr.Normal() {
		return []string{""}
	}
	return []string{}
}

// Extract values of all unique fields of all model features.
//
// Values are extracted using JSONPath expressions (via github.com/ohler55/ojg/jp),
// mirroring the Java implementation which relies on Jayway JsonPath configured with
// ALWAYS_RETURN_LIST. Each configured field is a JSONPath expression evaluated against the
// parsed OpenRTB request. A field maps to:
//   - a single-element slice for a scalar match (e.g. "$.site.publisher.id"),
//   - a multi-element slice for a wildcard/filter match (e.g. "$.imp[0].pmp.deals[*].id"),
//   - a single-element slice containing "null" when the field exists but is JSON null,
//   - an empty slice when the field does not exist or the path fails to resolve.
func (b *RequestEvaluator) parse(openRtbRequest string, externalFields []string) (map[string][]string, error) {
	uniqueFeatureFields, err := b.modelConfigurationHandler.GetAllUniqueFeatureFields()
	if err != nil {
		return nil, fmt.Errorf("fail to extract openRtbRequest due to %v", err)
	}
	uniqueFeatureFields = append(uniqueFeatureFields, externalFields...)
	Logger.Debug().Msgf("uniqueFeatureFields: %v", uniqueFeatureFields)

	// Parse the request once into a generic document. UseNumber preserves the exact
	// textual representation of numeric values (e.g. "970", "6.33") instead of coercing
	// them through float64 formatting.
	document, err := parseJSONDocument(openRtbRequest)
	if err != nil {
		return nil, fmt.Errorf("fail to parse openRtbRequest as JSON due to %v", err)
	}

	fieldValueMap := make(map[string][]string, len(uniqueFeatureFields))
	for _, field := range uniqueFeatureFields {
		fieldValueMap[field] = b.extractField(document, field)
	}

	Logger.Debug().Msgf("fieldValueMap: %v", fieldValueMap)
	return fieldValueMap, nil
}

// parseJSONDocument decodes a raw JSON string into a generic document suitable for
// JSONPath evaluation, preserving numbers as json.Number to retain their exact form.
func parseJSONDocument(rawJSON string) (interface{}, error) {
	decoder := json.NewDecoder(strings.NewReader(rawJSON))
	decoder.UseNumber()
	var document interface{}
	if err := decoder.Decode(&document); err != nil {
		return nil, err
	}
	return document, nil
}

// extractField compiles a single JSONPath expression and evaluates it against the parsed
// document, flattening the matches into a slice of strings.
//
// The empty-match behavior mirrors the Java implementation (Jayway JsonPath with
// ALWAYS_RETURN_LIST as consumed by OpenRtbRequestContextJsonDocument.findPath):
//   - A definite (normal) path — only object keys and array indices, e.g. "$.app" or
//     "$.site.publisher.id" — that matches nothing is treated like Jayway's
//     PathNotFoundException and yields a single empty string [""]. This keeps downstream
//     transformers such as Exists deterministic: a missing field becomes "0" rather than
//     dropping out of the feature entirely.
//   - An indefinite path — containing a wildcard, filter, union, slice, or descent, e.g.
//     "$.imp[0].pmp.deals[*].id" — that matches nothing yields an empty slice [], since a
//     wildcard over zero elements legitimately produces no values.
//
// An invalid expression yields an empty slice.
func (b *RequestEvaluator) extractField(document interface{}, field string) []string {
	expr, err := jp.ParseString(field)
	if err != nil {
		Logger.Debug().Msgf("field [%v] is not a valid JSONPath expression: %v", field, err)
		return []string{}
	}
	// jp.Get always returns a slice of matches (equivalent to Jayway ALWAYS_RETURN_LIST).
	matches := expr.Get(document)
	if len(matches) == 0 {
		// No match: a definite path yields [""] (Java findPath contract), an indefinite
		// path yields []. This is the same rule as emptyValueForField, applied here with
		// the already-parsed expr to avoid re-parsing.
		if expr.Normal() {
			return []string{""}
		}
		return []string{}
	}
	values := make([]string, 0, len(matches))
	for _, item := range matches {
		values = append(values, jsonValueToString(item))
	}
	return values
}

// jsonValueToString renders a scalar JSON value as a string. A nil (JSON null) becomes
// "null"; all other values use their natural string representation (json.Number preserves
// the original numeric text).
func jsonValueToString(value interface{}) string {
	if value == nil {
		return "null"
	}
	return fmt.Sprintf("%v", value)
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
