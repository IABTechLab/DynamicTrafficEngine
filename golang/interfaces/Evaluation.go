// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package interfaces

// An interface for evaluating bid request using specific models.
type ModelEvaluator interface {
	// Evaluates an OpenRTB request using the specified model.
	//
	// This method applies the model defined in the input to evaluate the given OpenRTB request and produce an filter recommendation.
	Evaluate(input ModelEvaluatorInput) (*ModelEvaluatorOutput, error)
}

// An interface that handles all the relevant components needed to evaluate relevant models against OpenRTB requests.
type ModelResultHandlerInterface interface {
	// Loads in all models' outputs that are available to a given SSP and stores it for evaluation.
	//
	// This function can either load from an S3 file, or from a locally downloaded file.
	//
	// The data itself will only be loaded into the local cache if the hash/etag of the file has changed.
	Load(sspIdentifier string) error

	// Returns a given model's filter recommendation based on the features of an OpenRTB request.
	//
	// If not in the cache, it is assumed that the request is high value (1.0).
	Provide(modelIdentifier string, features []ModelFeature, defaultValue float32) (*ModelResult, error)

	// Creates the path of the model output file. It returns a path in the form of *sspIdentifier*/*YYYY*-*MM*-*DD*/*hh*/*modelIdentifer*.csv
	BuildModelResultFileName(sspIdentifier string, modelIdentifier string) string

	// Generates the string tuple, given the individual feature values. Features are delimited by "|".
	BuildKey(modelFeatures []ModelFeature) string
}

// Context contains metadata regarding the evaluation of a given OpenRTB request.
type Context struct {
	// Unique ID.
	RequestId string

	// JSON OpenRTB request.
	OpenRtbRequest string

	// OpenRTB request ID, or randomly generated ID if not present.
	OpenRtbRequestId string

	// Context used to allocate request to a given treatment.
	TrafficAllocationContext TrafficAllocationContextInterface

	// Individual models' recommendation responses.
	ModelEvaluatorOutputs []ModelEvaluatorOutput

	// Overall filter recommendation response, based on the models' recommendations.

	AggregatedModelEvaluationResult *AggregatedModelEvaluationResult

	// Errors accumulated during the evaluation.
	Errors []string

	// Debug messages accumulated during the evaluation.
	DebugMessages []string
}

func (c *Context) AddError(error string) {
	c.Errors = append(c.Errors, error)
}

func (c *Context) AddDebug(message string) {
	c.DebugMessages = append(c.DebugMessages, message)
}

func NewContext() *Context {
	return &Context{}
}

// Model evaluation response object.
type ModelEvaluatorOutput struct {
	// Context containing metadata of the evaluation of a given OpenRTB request.
	Context Context

	// Status of the evaluation. One of "SUCCESS", "ERROR", and "TIMEOUT".
	Status ModelEvaluationStatus

	// Raw result of the model evaluation.
	ModelResult ModelResult

	// Model configuration. Derived from configuration/model/config.json file.
	ModelDefinition ModelDefinition

	// Model features and values.
	ModelFeatures []ModelFeature
}

// Model evaluation request object.
type ModelEvaluatorInput struct {
	// Context containing metadata of the evaluation of a given OpenRTB request.
	Context *Context

	// JSON OpenRTB request.
	OpenRtbRequest string

	// Model configuration. Derived from configuration/model/config.json file.
	ModelDefinition *ModelDefinition

	// Map of OpenRTB paths and their raw values.
	FeatureFieldValueMap map[string]string
}

type ModelEvaluationStatus string

const (
	ModelEvaluationStatusSuccess ModelEvaluationStatus = "SUCCESS"
	ModelEvaluationStatusError   ModelEvaluationStatus = "ERROR"
	ModelEvaluationStatusTimeout ModelEvaluationStatus = "TIMEOUT"
)

// Overall recommendation object.
type AggregatedModelEvaluationResult struct {
	ExperimentName     string
	ExperimentType     string
	TreatmentCode      string
	TreatmentCodeInInt int8
	Score              float32
	ScoreWithTreatment float32
	AggregationType    string
}
