// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package bloomfilter

import (
	"context"
	"fmt"
	"io"
	"strings"

	bloom "github.com/OldPanda/bloomfilter"
	"github.com/rs/zerolog"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/repository"
	"golang.a2z.com/demanddriventrafficevaluator/util"
)

var loaderLogger zerolog.Logger

func init() {
	loaderLogger = util.GetLogger()
	util.WithComponent("bloomfilter")
}

// CacheKeyBloomFilterFileIdentifier is the cache key prefix used for bloom filter ETag tracking.
const CacheKeyBloomFilterFileIdentifier = "BloomFilterFileIdentifier"

// BloomFilterLoader loads bloom filter models from S3 into the BloomFilterStore.
type BloomFilterLoader struct {
	store                     *BloomFilterStore
	daoFactory                interfaces.DaoFactoryInterface
	localCacheFactory         interfaces.LocalCacheFactoryInterface
	modelConfigurationHandler interfaces.ModelConfigurationHandlerInterface
	timeProvider              interfaces.TimeProvider
}

// NewBloomFilterLoader creates a new BloomFilterLoader with all required dependencies.
func NewBloomFilterLoader(
	store *BloomFilterStore,
	daoFactory interfaces.DaoFactoryInterface,
	localCacheFactory interfaces.LocalCacheFactoryInterface,
	modelConfigurationHandler interfaces.ModelConfigurationHandlerInterface,
	timeProvider interfaces.TimeProvider,
) *BloomFilterLoader {
	return &BloomFilterLoader{
		store:                     store,
		daoFactory:                daoFactory,
		localCacheFactory:         localCacheFactory,
		modelConfigurationHandler: modelConfigurationHandler,
		timeProvider:              timeProvider,
	}
}

// Load iterates model definitions from configuration, filters for BLOOM_FILTER format,
// and loads each bloom filter model from S3. Errors on individual models are logged
// but do not stop processing of remaining models.
func (l *BloomFilterLoader) Load(sspIdentifier string, folderPrefix string) error {
	modelConfiguration, err := l.modelConfigurationHandler.Provide()
	if err != nil {
		return fmt.Errorf("fail to provide modelConfiguration: %w", err)
	}

	for modelIdentifier, modelDefinition := range modelConfiguration.ModelDefinitionByIdentifier {
		if modelDefinition.ModelFormat != interfaces.ModelFormatBloomFilter {
			continue
		}

		if err := l.loadSingleModel(sspIdentifier, folderPrefix, modelIdentifier, modelDefinition); err != nil {
			loaderLogger.Error().Msgf("Error loading bloom filter model [%s]: %v", modelIdentifier, err)
			// Continue to next model — don't fail the entire load
		}
	}

	return nil
}

// loadSingleModel loads a single bloom filter model from S3 into the store.
// It resolves the S3 path, fetches the object, checks ETag via ShouldRefresh,
// deserializes using OldPanda bloomfilter.FromBytes(), and stores the result.
func (l *BloomFilterLoader) loadSingleModel(sspIdentifier string, folderPrefix string, modelIdentifier string, modelDef interfaces.ModelDefinition) error {
	s3Path := l.BuildS3Path(sspIdentifier, modelDef)

	if !strings.HasPrefix(folderPrefix, repository.S3Prefix) {
		return fmt.Errorf("bloom filter loading only supports S3 paths, got: %s", folderPrefix)
	}

	s3BucketName := strings.TrimPrefix(folderPrefix, repository.S3Prefix)
	getObjectOutput, err := l.daoFactory.GetS3Object(context.TODO(), s3BucketName, s3Path)
	if err != nil {
		loaderLogger.Warn().Msgf("Bloom filter file not found or S3 error for %s/%s: %v", s3BucketName, s3Path, err)
		return fmt.Errorf("error fetching bloom filter S3 file %s/%s: %w", s3BucketName, s3Path, err)
	}

	defer func() {
		_, _ = io.Copy(io.Discard, getObjectOutput.Body)
		_ = getObjectOutput.Body.Close()
	}()

	// Use a per-model cache key for ETag change detection
	cacheKey := CacheKeyBloomFilterFileIdentifier + "_" + modelIdentifier
	if !l.localCacheFactory.ShouldRefresh(cacheKey, *(getObjectOutput.ETag)) {
		loaderLogger.Info().Msgf("Skipping bloom filter refresh for %s (ETag unchanged)", modelIdentifier)
		return nil
	}

	content, err := l.daoFactory.ReadContent(getObjectOutput.Body)
	if err != nil {
		return fmt.Errorf("error reading bloom filter content for %s: %w", modelIdentifier, err)
	}

	filter, err := bloom.FromBytes(content)
	if err != nil {
		return fmt.Errorf("error deserializing bloom filter for %s: %w", modelIdentifier, err)
	}

	l.store.Put(modelIdentifier, filter)
	loaderLogger.Info().Msgf("Successfully loaded bloom filter for model [%s]", modelIdentifier)

	return nil
}

// BuildS3Path resolves the S3 object path based on the model's S3PathMode.
// DYNAMIC (default): {sspIdentifier}/{YYYY-MM-DD}/{HH}/{identifier}.bloom
// STATIC: {sspIdentifier}/models/{identifier}.bloom
func (l *BloomFilterLoader) BuildS3Path(sspIdentifier string, modelDef interfaces.ModelDefinition) string {
	identifier := modelDef.Identifier

	if modelDef.S3PathMode == interfaces.S3PathModeStatic {
		return fmt.Sprintf("%s/models/%s.bloom", sspIdentifier, identifier)
	}

	// Default to DYNAMIC path resolution
	now := l.timeProvider.Now().UTC()
	date := now.Format("2006-01-02")
	hour := now.Format("15")
	return fmt.Sprintf("%s/%s/%s/%s.bloom", sspIdentifier, date, hour, identifier)
}
