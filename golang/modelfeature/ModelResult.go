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
		modelResultFileName := t.BuildModelResultFileName(sspIdentifier, modelIdentifier)
		modelResultValue, exists := ModelTypeValue[modelDefinition.Type]
		if !exists {
			// default to a low value model type (0.0) if not defined
			Logger.Info().Msgf("model type [%s] not found in the [%+v]. Defaulting to LowValue", modelDefinition.Type, ModelTypeValue)
			modelResultValue = 0.0
		}

		var modelResult []byte
		var repositoryError error
		if strings.HasPrefix(t.folderPrefix, repository.S3Prefix) {
			// get bucket name from "s3://<bucket-name>"
			s3BucketName := strings.TrimPrefix(t.folderPrefix, repository.S3Prefix)
			getObjectOutput, s3Error := t.daoFactory.GetS3Object(context.TODO(), s3BucketName, modelResultFileName)
			if s3Error != nil {
				Logger.Error().Msgf("Error fetching S3 file %s/%s: %v", s3BucketName, modelResultFileName, s3Error)
				continue

			}
			if !t.localCacheFactory.ShouldRefresh(repository.CacheKeyModelResultFileIdentifier, *(getObjectOutput.ETag)) {
				Logger.Info().Msgf("Skipping refresh for %s", modelResultFileName)
				continue
			}

			modelResult, repositoryError = t.daoFactory.ReadContent(getObjectOutput.Body)
		} else {
			// read from local file path
			filePath := filepath.Join(t.folderPrefix, modelResultFileName)
			filePointer, err := os.Open(filePath)
			if err != nil {
				Logger.Error().Msgf("Error opening file %s: %v", filePath, err)
				continue
			}
			if !t.localCacheFactory.ShouldRefreshLocal(repository.CacheKeyModelResultFileIdentifier, filePointer) {
				Logger.Info().Msgf("Skipping refresh for %s", filePath)
				continue
			}

			modelResult, repositoryError = t.daoFactory.GetDataFromLocal(filePointer)
			_ = filePointer.Close()
		}

		if repositoryError != nil {
			return fmt.Errorf("error getting data %w", repositoryError)
		}

		// clear all entries from cache since new model is detected
		t.localCacheFactory.ClearLocalCache(modelIdentifier)

		reader := csv.NewReader(bytes.NewReader(modelResult))
		//reader.ReuseRecord = true // Reuse the same slice for each record to reduce allocations
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

			putItemCounter++
			putItemTotalSize += int64(len(record[0])) // Only count the size of the Key, not the entire modelResultKeys
		}
	}

	Logger.Info().Msgf("Processed %d items with total size of %d bytes", putItemCounter, putItemTotalSize)
	return nil
}

func (t *ModelResultHandler) Provide(modelIdentifier string, features []interfaces.ModelFeature, defaultValue float32) (*interfaces.ModelResult, error) {
	key := t.BuildKey(features)
	Logger.Debug().Msgf("Providing model result for identifier %q and Key %q", modelIdentifier, key)
	modelResult, exists := t.localCacheFactory.GetFromLocalCache(modelIdentifier, key)
	if !exists {
		Logger.Info().Msgf("No entry exists for identifer %q and key %q. Default return value is %f", modelIdentifier, key, defaultValue)
		return &interfaces.ModelResult{
			Value: defaultValue,
			Key:   key,
		}, nil
	}

	result, ok := modelResult.(float32)
	if !ok {
		return nil, fmt.Errorf("invalid model result type for identifier %q and Key %q: expected float32, got %T", modelIdentifier, key, modelResult)
	}

	return &interfaces.ModelResult{
		Value: result,
		Key:   key,
	}, nil
}

func (t *ModelResultHandler) BuildModelResultFileName(sspIdentifier string, modelIdentifier string) string {
	now := t.timeProvider.Now().UTC()
	date := now.Format("2006-01-02")
	hour := now.Format("15")
	return fmt.Sprintf("%s/%s/%s/%s.csv", sspIdentifier, date, hour, modelIdentifier)
}

func (t *ModelResultHandler) BuildKey(modelFeatures []interfaces.ModelFeature) string {
	var allValues []string

	for _, feature := range modelFeatures {
		allValues = append(allValues, feature.Values...)
	}

	return strings.Join(allValues, KeyDelimiter)
}
