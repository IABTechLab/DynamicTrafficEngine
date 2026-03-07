// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package modelfeature

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/repository"
)

// ConfigurationHandler is responsible for loading and providing model and experiment configurations.
// It takes a folderPrefix, sspIdentifier, fileIdentifierCacheKey, dataCacheKey, configType, and localCacheFactory as dependencies.
// It implements the ConfigurationHandlerInterface.
// The Load method reads the configuration file, parses it, and stores it in the local cache.
// The Provide method retrieves the configuration from the local cache.
// The buildConfigurationFileName method constructs the file path for the configuration file based on the provided parameters.
type ConfigurationHandler[T any] struct {
	folderPrefix           string
	sspIdentifier          string
	daoFactory             interfaces.DaoFactoryInterface
	fileIdentifierCacheKey string
	dataCacheKey           string
	configType             string
	localCacheFactory      interfaces.LocalCacheFactoryInterface
}

func NewConfigurationHandler[T any](folderPrefix string, sspIdentifier string, daoFactory interfaces.DaoFactoryInterface, fileIdentifierCacheKey string, dataCacheKey string, configType string, localCacheFactory interfaces.LocalCacheFactoryInterface) *ConfigurationHandler[T] {
	return &ConfigurationHandler[T]{
		folderPrefix:           folderPrefix,
		sspIdentifier:          sspIdentifier,
		daoFactory:             daoFactory,
		fileIdentifierCacheKey: fileIdentifierCacheKey,
		dataCacheKey:           dataCacheKey,
		configType:             configType,
		localCacheFactory:      localCacheFactory,
	}
}

// Load reads the configuration file, parses it, and stores it in the local cache.
func (t *ConfigurationHandler[T]) Load() (bool, error) {
	filename := t.buildConfigurationFileName()

	var jsonData []byte
	var err error
	if strings.HasPrefix(t.folderPrefix, repository.S3Prefix) {
		// get bucket name from "s3://<bucket-name>"
		s3BucketName := strings.TrimPrefix(t.folderPrefix, repository.S3Prefix)
		Logger.Info().Msgf("bucketname: %s", s3BucketName)

		getObjectOutput, s3Error := t.daoFactory.GetS3Object(context.TODO(), s3BucketName, filename)
		if s3Error != nil {
			return false, fmt.Errorf("error fetching s3 file: %v", s3Error)
		}
		if !t.localCacheFactory.ShouldRefresh(t.fileIdentifierCacheKey, *getObjectOutput.ETag) {
			Logger.Info().Msgf("Skipping refresh for %s", filename)
			return false, nil
		}

		jsonData, err = t.daoFactory.ReadContent(getObjectOutput.Body)
	} else {
		// read from local file path
		filePath := filepath.Join(t.folderPrefix, filename)
		filePointer, localErr := os.Open(filePath)
		if localErr != nil {
			return false, fmt.Errorf("error opening file: %v", localErr)
		}
		if !t.localCacheFactory.ShouldRefreshLocal(t.fileIdentifierCacheKey, filePointer) {
			Logger.Info().Msgf("Skipping refresh for %s", filename)
			return false, nil
		}

		jsonData, err = t.daoFactory.GetDataFromLocal(filePointer)
		_ = filePointer.Close()
	}

	if err != nil {
		return false, fmt.Errorf("error getting data: %v", err)
	}

	// Create an instance of your struct
	var config T
	// Unmarshal the JSON data into the struct
	err = json.Unmarshal(jsonData, &config)
	Logger.Info().Msgf("data= %s", jsonData)
	if err != nil {
		return false, fmt.Errorf("error unmarshaling JSON: %v", err)
	}

	isSuccess := t.localCacheFactory.PutToLocalCacheWithTTL(repository.CacheNameConfiguration, t.dataCacheKey, config, 0)
	if !isSuccess {
		return false, fmt.Errorf("error setting data to local cache [Configuration] with identifier [%v] and Value [%v]", t.dataCacheKey, config)
	}
	return true, nil
}

// Provide provides the model definition for a given identifier.
func (t *ConfigurationHandler[T]) Provide() (*T, error) {
	config, found := t.localCacheFactory.GetFromLocalCache(repository.CacheNameConfiguration, t.dataCacheKey)
	if !found {
		return nil, fmt.Errorf("error getting Config from local cache [%s] with Key [%s]", repository.CacheNameConfiguration, t.dataCacheKey)
	}
	typedConfig, ok := config.(T)
	if !ok {
		return nil, fmt.Errorf("retrieved config is not of type [%s]", t.configType)
	}

	return &typedConfig, nil
}

func (t *ConfigurationHandler[T]) buildConfigurationFileName() string {
	return fmt.Sprintf("%s/configuration/%s/config.json", t.sspIdentifier, t.configType)
}
