// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package modelfeature

import (
	"fmt"

	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
)

// currently unused
const (
	LowValue  interfaces.ModelType = "LowValue"
	HighValue interfaces.ModelType = "HighValue"
)

// currently unused
var ModelTypeValue = map[interfaces.ModelType]float32{
	LowValue:  0.0,
	HighValue: 1.0,
}

/**
Extractor
*/

// Constants for FeatureExtractorType
const (
	JsonExtractor     interfaces.FeatureExtractorType = "JsonExtractor"
	ProtobufExtractor interfaces.FeatureExtractorType = "ProtobufExtractor" // unused
)

/**
Transformer
*/

// Transformer the function type for the Transformer
type Transformer func(modelFeature *interfaces.ModelFeature) (*interfaces.ModelFeature, error)

const (
	Exists            interfaces.TransformerName = "Exists"
	GetFirstNotEmpty  interfaces.TransformerName = "GetFirstNotEmpty"
	ApplyMappings     interfaces.TransformerName = "ApplyMappings"
	ConcatenateByPair interfaces.TransformerName = "ConcatenateByPair"
)

var TransformerMap = map[interfaces.TransformerName]Transformer{
	Exists:            ExistsTransformer,
	GetFirstNotEmpty:  GetFirstNotEmptyTransformer,
	ApplyMappings:     ApplyMappingsTransformer,
	ConcatenateByPair: ConcatenateByPairTransformer,
}

// A transformer that checks for the existence of non-empty values in a ModelFeature.
//
// This function transforms the values of a ModelFeature into binary indicators of existence.
// It converts each value to "1" if it exists and is non-empty, or "0" if it's empty or null.
func ExistsTransformer(modelFeature *interfaces.ModelFeature) (*interfaces.ModelFeature, error) {
	var transformedValues []string
	for _, value := range modelFeature.Values {
		if value == "" {
			transformedValues = append(transformedValues, "0")
		} else {
			transformedValues = append(transformedValues, "1")
		}
	}

	return &interfaces.ModelFeature{
		Configuration: modelFeature.Configuration,
		Values:        transformedValues,
	}, nil
}

// A transformer that retrieves the first non-empty value from a ModelFeature.
//
// This function transforms a ModelFeature by selecting the first non-null and non-empty
// value from its list of values. If no such value is found, it returns an  empty string.
func GetFirstNotEmptyTransformer(modelFeature *interfaces.ModelFeature) (*interfaces.ModelFeature, error) {
	var firstNotEmpty string
	for _, value := range modelFeature.Values {
		if value != "" {
			firstNotEmpty = value
			break
		}
	}

	return &interfaces.ModelFeature{
		Configuration: modelFeature.Configuration,
		Values:        []string{firstNotEmpty},
	}, nil
}

// A transformer that applies predefined mappings to model feature values.
//
// This function transforms the values of a ModelFeature based on a mapping defined in
// the feature's configuration. If a value doesn't have a defined mapping, a default value is used.
func ApplyMappingsTransformer(modelFeature *interfaces.ModelFeature) (*interfaces.ModelFeature, error) {
	configuration := modelFeature.Configuration
	mapping := configuration.Mapping
	mappingDefaultValue := configuration.MappingDefaultValue

	var transformedValues []string
	for _, value := range modelFeature.Values {
		mappedValue, ok := mapping[value]
		if ok {
			transformedValues = append(transformedValues, mappedValue)
		} else {
			transformedValues = append(transformedValues, mappingDefaultValue)
		}
	}

	return &interfaces.ModelFeature{
		Configuration: configuration,
		Values:        transformedValues,
	}, nil
}

// A transformer that concatenates pairs of values in a ModelFeature.
//
// This function transforms the values of a ModelFeature by concatenating adjacent pairs
// of values. The concatenation is done using 'x' as a separator.
func ConcatenateByPairTransformer(modelFeature *interfaces.ModelFeature) (*interfaces.ModelFeature, error) {
	originalValues := modelFeature.Values
	inputSize := len(originalValues)
	var transformedValues []string

	for i := 0; i < inputSize/2; i++ {
		first := originalValues[i*2]
		second := originalValues[i*2+1]
		if first != "" && second != "" {
			transformedValues = append(transformedValues, fmt.Sprintf("%sx%s", first, second))
		}
	}

	return &interfaces.ModelFeature{
		Configuration: modelFeature.Configuration,
		Values:        transformedValues,
	}, nil
}

// Handles usages of a model configuration file.
type ModelConfigurationHandler struct {
	configurationHandler interfaces.ConfigurationHandlerInterface[interfaces.ModelConfiguration]
}

func NewModelConfigurationHandler(configurationHandler interfaces.ConfigurationHandlerInterface[interfaces.ModelConfiguration]) *ModelConfigurationHandler {
	return &ModelConfigurationHandler{
		configurationHandler: configurationHandler,
	}
}

func (t *ModelConfigurationHandler) Provide() (*interfaces.ModelConfiguration, error) {
	return t.configurationHandler.Provide()
}

func (t *ModelConfigurationHandler) Load() (bool, error) {
	return t.configurationHandler.Load()
}

func (t *ModelConfigurationHandler) GetAllUniqueFeatureFields() ([]string, error) {
	modelConfig, err := t.configurationHandler.Provide()
	if err != nil {
		return nil, fmt.Errorf("error getting ModelDefinition from local cache [Configuration] with Key [ModelConfiguration]: %w", err)
	}

	uniqueFields := make(map[string]struct{})

	for _, modelDef := range modelConfig.ModelDefinitionByIdentifier {
		for _, feature := range modelDef.Features {
			for _, field := range feature.Fields {
				uniqueFields[field] = struct{}{}
			}
		}
	}

	result := make([]string, 0, len(uniqueFields))
	for field := range uniqueFields {
		result = append(result, field)
	}

	return result, nil
}
