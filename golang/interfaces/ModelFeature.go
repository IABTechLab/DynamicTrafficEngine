// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package interfaces

// An interface for fetching configuration files for models and experiments.
type ConfigurationHandlerInterface[T any] interface {
	// Loads in a configuration file and stores it for evaluation.
	//
	// This function can either load from an S3 file, or from a locally downloaded file.
	//
	// The data itself will only be loaded into the local cache if the hash/etag of the file has changed.
	Load() (bool, error)

	// Returns the configuration data in a structured object.
	//
	// An error is thrown if the returned data is missing or malformed.
	Provide() (*T, error)
}

// An interface for storing a model configuration. Derived from configuration/model/config.json file.
type ModelConfigurationHandlerInterface interface {
	ConfigurationHandlerInterface[ModelConfiguration]

	// Get all of the OpenRTB path fields needed for the model to evaluate a request.
	GetAllUniqueFeatureFields() ([]string, error)
}

// An interface for storing an experiment configuration. Derived from configuration/experiment/config.json file.
type ExperimentConfigurationHandlerInterface interface {
	ConfigurationHandlerInterface[ExperimentConfiguration]
}

// An interface to handle the allocation of requests to a treatment bucket.
type TrafficAllocatorInterface interface {
	// Updates the allocation strategy based on latest version of the experiment configuration file.
	UpdateConfiguration(experimentConfiguration *ExperimentConfiguration) error

	// Returns the context used to associate models to experiments for each request.
	//
	// Each model is associated to an experiment, which describes how model results should be used to
	// create an overall filter recommendation for the request.
	GetTrafficAllocationContext() TrafficAllocationContextInterface

	// Returns the treatment bucket based on the experiment configuration.
	GetTreatmentCode(thresholds []uint32, treatments []Treatment) (string, error)

	Rand() uint32
}

// An interface that provides relationships between models and experiments.
type TrafficAllocationContextInterface interface {
	// Returns a list of models to a given experiment.
	GetModelIdentifiers() []string

	// Returns a map of experiments to treatment codes for a request.
	GetExperimentArrangement() map[string]string

	// Returns the treatment code for an experiment for a request.
	GetTreatmentCode(experimentName string) string

	// Returns an integer representation of the treatment code for an experiment for a request.
	//
	// 0 represents T (treatment), 1 represents C (control).
	GetTreatmentCodeInInt(experimentName string) int8

	// Returns the experiment configuration by name.
	GetExperimentDefinition(experimentName string) ExperimentDefinition

	// Returns the experiment configuration associated to the model.
	GetExperimentDefinitionByModel(model string) ExperimentDefinition

	// Returns the experiment associated to a specific Experiment Type.
	//
	// Currently, the only experiment type is "soft-filter".
	GetExperimentDefinitionByType(typeStr string) (*ExperimentDefinition, error)

	// Returns a map of mdoels associated to each experiemnt.
	GetModelsByExperiment() map[string][]string
}

// Object that represents all model configurations.
type ModelConfiguration struct {
	ModelDefinitionByIdentifier map[string]ModelDefinition `json:"modelDefinitionByIdentifier"`
}

// Object that represents all experiment configurations.
type ExperimentConfiguration struct {
	Type                       string                          `json:"type"`
	ExperimentDefinitionByName map[string]ExperimentDefinition `json:"experimentDefinitionByName"`
	ModelToExperiment          map[string]string               `json:"modelToExperiment"`
}

// Object that represents the entry corresponding to the request for a given model.
type ModelResult struct {
	Value float32
	Key   string

	// All permutation keys that were looked up, in order.
	Keys []string

	// Parallel array to Keys. Values[i] = cached value for Keys[i], or defaultValue on miss.
	Values []float32
}

// Aggregation operator constants for AggregationNode tree evaluation.
const (
	AggregationOperatorAND = "AND"
	AggregationOperatorOR  = "OR"
)

// AggregationNode represents a node in the aggregation schema tree.
// Branch nodes have an Operator and Conditions; leaf nodes have a ModelIdentifier.
type AggregationNode struct {
	// Operator is "AND" or "OR" for branch nodes, empty for leaf nodes.
	Operator string `json:"operator,omitempty"`

	// Conditions is the list of child nodes for branch nodes.
	Conditions []AggregationNode `json:"conditions,omitempty"`

	// ModelIdentifier references a model for leaf nodes.
	ModelIdentifier string `json:"modelIdentifier,omitempty"`
}

// IsLeaf returns true if this node is a leaf (references a model, has no operator).
func (n *AggregationNode) IsLeaf() bool {
	return n.Operator == "" && n.ModelIdentifier != ""
}

// ExperimentDefinition represents a single experiment definition
type ExperimentDefinition struct {
	// Name of the experiment.
	Name string `json:"name"`

	// Type of the experiment. Currently only the type "soft-filter" is supported.
	Type string `json:"type"`

	// Provides an ordered list of treatments (traffic groups), and how to allocate traffic between these groups.
	Treatments []Treatment `json:"treatments"`

	// Specifies the time on when to start using this experiment. The value is provided as UTC epoch milli-seconds.
	StartTimeUTC int64 `json:"startTimeUTC"`

	// Specifies the time on when to stop using this experiment. The value is provided as UTC epoch milli-seconds.
	EndTimeUTC int64 `json:"endTimeUTC"`

	// AggregationSchema defines the AND/OR tree for combining model results.
	// nil indicates no schema is configured and triggers max-aggregation fallback behavior.
	AggregationSchema *AggregationNode `json:"aggregationSchema,omitempty"`
}

// ModelFormat constants determine the loader and evaluator type for a model.
const (
	ModelFormatRuleBased   = "RULE_BASED"
	ModelFormatBloomFilter = "BLOOM_FILTER"
)

// S3PathMode constants determine how S3 object paths are resolved for model loading.
const (
	S3PathModeDynamic = "DYNAMIC"
	S3PathModeStatic  = "STATIC"
)

type ModelDefinition struct {
	// Unique identifier for the model <dsp>_<name>_<version>
	Identifier string `json:"identifier"`

	// Name of the signal. Unlike the "identifier", this field does not have any version information in the value.
	Name string `json:"name"`

	// Name of the DSP that is sharing this signal. Ex: adsp.
	Dsp string `json:"dsp"`

	// Version of the signal.
	Version string `json:"version"`

	// Type of the model. Supports "LowValue" and "HighValue" model types.
	Type ModelType `json:"modelType"`

	// ModelFormat determines the loader and evaluator type.
	// Valid values: "RULE_BASED", "BLOOM_FILTER". Defaults to "RULE_BASED" if empty.
	ModelFormat string `json:"modelFormat"`

	// S3PathMode determines how S3 object paths are resolved.
	// "DYNAMIC": {ssp}/{date}/{hour}/{s3ObjectKey}
	// "STATIC": literal s3ObjectKey value
	// Defaults to "DYNAMIC" if empty.
	S3PathMode string `json:"s3PathMode"`

	// Specifies how the feature extraction is defined. Currently only "JsonExtractor" is supported,
	// so the json paths are provided in the config to specify how to extract the features from an
	// OpenRTB request in JSON format.
	FeatureExtractorType FeatureExtractorType `json:"featureExtractorType"`

	// Provides an ordered list of features, and information on how to extract and transform each of those features.
	Features []FeatureConfiguration `json:"features"`
}

// Treatment represents a treatment in the experiment
type Treatment struct {
	// Name of the treatment.
	TreatmentCode string `json:"treatmentCode"`

	// Specifies the probability that one request is allocated to one group.
	Weight uint32 `json:"weight"`
}

type ModelType string

type FeatureExtractorType string

// FeatureConfiguration represents the configuration for a feature used by a model.
type FeatureConfiguration struct {
	// Name of the feature.
	Name string `json:"name"`

	// An ordered list of one of more json paths in the OpenRTB request from where the values have to be extracted.
	Fields []string `json:"fields"`

	// An ordered list of transformations that must be applied on the values extracted.
	// Currently supports (1) Exists, (2) ConcatenateByPair, (3) GetFirstNonEmpty and (4) ApplyMappings as the named transformations.
	// A reference implementation for each of these transformations is provided in the library.
	Transformations []TransformerName `json:"transformation"`

	// [Optional] A mapping provided to help with the 'ApplyMappings' transformation.
	Mapping map[string]string `json:"mapping"`

	// [Optional] A mapping provided to help with the 'ApplyMappings' transformation.
	// Use this value when the extracted value cannot be mapped using the 'mapping' field.
	MappingDefaultValue string `json:"mappingDefaultValue"`
}
type TransformerName string

// ModelFeature represents a model feature with its configuration and values.
type ModelFeature struct {
	// Represnts the configuration for a feature used by a model.
	Configuration *FeatureConfiguration

	// Values to be used to determine the feature value.
	Values []string
}
