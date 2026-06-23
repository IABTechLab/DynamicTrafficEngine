// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package modelfeature

import (
	"bytes"
	"context"
	"encoding/csv"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/rs/zerolog"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/repository"
	"golang.a2z.com/demanddriventrafficevaluator/util"
)

var Logger zerolog.Logger

func init() {
	Logger = util.GetLogger()
	util.WithComponent("modelfeature")
}

const KeyDelimiter = "|"
const MaxKeys = 100

// Applies transformations to generate a value for a specific feature for model evaluation.
func Transform(feature *interfaces.ModelFeature) (*interfaces.ModelFeature, error) {
	configuration := feature.Configuration
	transformerNames := configuration.Transformations

	var transformers []Transformer
	for _, transformerName := range transformerNames {
		transformer, exists := TransformerMap[transformerName]
		if !exists {
			return nil, fmt.Errorf("transformer [%s] not found", transformerName)
		}
		transformers = append(transformers, transformer)
	}

	transformedFeature := feature
	for index, transformer := range transformers {
		var err error
		transformedFeature, err = transformer(transformedFeature)
		if err != nil {
			return nil, fmt.Errorf("transformer [%v] fail to transform the feature [%v] due to error %v", transformerNames[index], transformedFeature, err)
		}
	}

	return transformedFeature, nil
}

// Handles usages of a model output (result) file.
type ModelResultHandler struct {
	sspIdentifier             string
	folderPrefix              string
	daoFactory                interfaces.DaoFactoryInterface
	modelConfigurationHandler interfaces.ModelConfigurationHandlerInterface
	localCacheFactory         interfaces.LocalCacheFactoryInterface
	timeProvider              interfaces.TimeProvider
}

func NewModelResultHandler(sspIdentifier string, folderPrefix string, daoFactory interfaces.DaoFactoryInterface, modelConfigurationHandler interfaces.ModelConfigurationHandlerInterface, localCacheFactory interfaces.LocalCacheFactoryInterface, timeProvider interfaces.TimeProvider) *ModelResultHandler {
	return &ModelResultHandler{
		sspIdentifier:             sspIdentifier,
		folderPrefix:              folderPrefix,
		daoFactory:                daoFactory,
		modelConfigurationHandler: modelConfigurationHandler,
		localCacheFactory:         localCacheFactory,
		timeProvider:              timeProvider,
	}
}

func (t *ModelResultHandler) Load(sspIdentifier string) error {
	modelConfiguration, err := t.modelConfigurationHandler.Provide()
	if err != nil {
		return fmt.Errorf("fail to provide modelConfiguration: %w", err)
	}

	var putItemCounter int
	var putItemTotalSize int64

	for modelIdentifier, modelDefinition := range modelConfiguration.ModelDefinitionByIdentifier {
		if modelDefinition.ModelFormat == interfaces.ModelFormatBloomFilter {
			continue
		}

		modelResultValue, exists := ModelTypeValue[modelDefinition.Type]
		if !exists {
			// default to a low value model type (0.0) if not defined
			Logger.Info().Msgf("model type [%s] not found in the [%+v]. Defaulting to LowValue", modelDefinition.Type, ModelTypeValue)
			modelResultValue = 0.0
		}

		if err := t.loadSingleModel(sspIdentifier, modelIdentifier, modelResultValue, &putItemCounter, &putItemTotalSize); err != nil {
			return err
		}
	}

	Logger.Info().Msgf("Processed %d items with total size of %d bytes", putItemCounter, putItemTotalSize)
	return nil
}

func (t *ModelResultHandler) loadSingleModel(sspIdentifier string, modelIdentifier string, modelResultValue float32, putItemCounter *int, putItemTotalSize *int64) error {
	modelResultFileName := t.BuildModelResultFileName(sspIdentifier, modelIdentifier)

	var modelResult []byte
	var repositoryError error

	if strings.HasPrefix(t.folderPrefix, repository.S3Prefix) {
		// get bucket name from "s3://<bucket-name>"
		s3BucketName := strings.TrimPrefix(t.folderPrefix, repository.S3Prefix)
		getObjectOutput, s3Error := t.daoFactory.GetS3Object(context.TODO(), s3BucketName, modelResultFileName)
		if s3Error != nil {
			Logger.Error().Msgf("Error fetching S3 file %s/%s: %v", s3BucketName, modelResultFileName, s3Error)
			return nil
		}

		defer func() {
			_, _ = io.Copy(io.Discard, getObjectOutput.Body)
			_ = getObjectOutput.Body.Close()
		}()

		if !t.localCacheFactory.ShouldRefresh(repository.CacheKeyModelResultFileIdentifier, *(getObjectOutput.ETag)) {
			Logger.Info().Msgf("Skipping refresh for %s", modelResultFileName)
			return nil
		}

		modelResult, repositoryError = t.daoFactory.ReadContent(getObjectOutput.Body)
	} else {
		// read from local file path
		filePath := filepath.Join(t.folderPrefix, modelResultFileName)
		filePointer, err := os.Open(filePath)
		if err != nil {
			Logger.Error().Msgf("Error opening file %s: %v", filePath, err)
			return nil
		}

		defer func() {
			_ = filePointer.Close()
		}()

		if !t.localCacheFactory.ShouldRefreshLocal(repository.CacheKeyModelResultFileIdentifier, filePointer) {
			Logger.Info().Msgf("Skipping refresh for %s", filePath)
			return nil
		}

		modelResult, repositoryError = t.daoFactory.GetDataFromLocal(filePointer)
	}

	if repositoryError != nil {
		return fmt.Errorf("error getting data %w", repositoryError)
	}

	// clear all entries from cache since new model is detected
	t.localCacheFactory.ClearLocalCache(modelIdentifier)

	reader := csv.NewReader(bytes.NewReader(modelResult))
	// reader.ReuseRecord = true // Reuse the same slice for each record to reduce allocations
	for {
		record, readerError := reader.Read()
		if readerError == io.EOF {
			break
		}
		if readerError != nil {
			Logger.Error().Msgf("Error reading record: %v", readerError)
			continue
		}

		if !t.localCacheFactory.PutToLocalCache(modelIdentifier, record[0], modelResultValue) {
			Logger.Error().Msgf("Error putting model result record to the local cache [%v] with Key [%v]", modelIdentifier, record[0])
			continue
		}

		*putItemCounter++
		*putItemTotalSize += int64(len(record[0])) // Only count the size of the Key, not the entire modelResultKeys
	}
	return nil
}

func (t *ModelResultHandler) Provide(modelIdentifier string, features []interfaces.ModelFeature, defaultValue float32) (*interfaces.ModelResult, error) {
	keys := t.BuildKeys(features)

	if len(keys) == 0 {
		return &interfaces.ModelResult{
			Value:  defaultValue,
			Key:    "",
			Keys:   []string{""},
			Values: []float32{defaultValue},
		}, nil
	}

	allKeys := make([]string, len(keys))
	allValues := make([]float32, len(keys))
	copy(allKeys, keys)

	var firstHitValue float32 = defaultValue
	var firstHitKey string = keys[0]
	found := false

	for i, key := range keys {
		cachedResult, exists := t.localCacheFactory.GetFromLocalCache(modelIdentifier, key)
		if exists {
			result, ok := cachedResult.(float32)
			if ok {
				allValues[i] = result
				if !found {
					firstHitValue = result
					firstHitKey = key
					found = true
				}
			} else {
				allValues[i] = defaultValue
			}
		} else {
			allValues[i] = defaultValue
		}
	}

	Logger.Debug().Msgf("Providing model result for identifier %q: first-hit key %q, value %f, total keys %d", modelIdentifier, firstHitKey, firstHitValue, len(allKeys))

	return &interfaces.ModelResult{
		Value:  firstHitValue,
		Key:    firstHitKey,
		Keys:   allKeys,
		Values: allValues,
	}, nil
}

func (t *ModelResultHandler) BuildModelResultFileName(sspIdentifier string, modelIdentifier string) string {
	now := t.timeProvider.Now().UTC()
	date := now.Format("2006-01-02")
	hour := now.Format("15")
	return fmt.Sprintf("%s/%s/%s/%s.csv", sspIdentifier, date, hour, modelIdentifier)
}

func (t *ModelResultHandler) BuildKey(modelFeatures []interfaces.ModelFeature) string {
	keys := t.BuildKeys(modelFeatures)
	if len(keys) == 0 {
		return ""
	}
	return keys[0]
}

// BuildKeys generates all permutation keys from multi-valued features.
// Returns the Cartesian product of all feature value lists, joined by KeyDelimiter.
// Capped at MaxKeys (100).
func (t *ModelResultHandler) BuildKeys(modelFeatures []interfaces.ModelFeature) []string {
	if len(modelFeatures) == 0 {
		return []string{}
	}

	// Collect value lists, check for empty
	for _, feature := range modelFeatures {
		if len(feature.Values) == 0 {
			return []string{}
		}
	}

	// Compute Cartesian product iteratively
	keys := []string{""}
	for _, feature := range modelFeatures {
		var newKeys []string
		for _, prefix := range keys {
			for _, value := range feature.Values {
				var key string
				if prefix == "" {
					key = value
				} else {
					key = prefix + KeyDelimiter + value
				}
				newKeys = append(newKeys, key)
				if len(newKeys) >= MaxKeys {
					return newKeys
				}
			}
		}
		keys = newKeys
	}
	return keys
}
