// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package bloomfilter

import (
	"context"
	"fmt"
	"strings"
	"sync"

	bloom "github.com/OldPanda/bloomfilter"
	"github.com/aws/aws-sdk-go-v2/service/s3"
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

// CacheKeyBloomFilterFileIdentifier is retained for compatibility.
// Bloom filter validators now live with the loader and its installed filters.
const CacheKeyBloomFilterFileIdentifier = "BloomFilterFileIdentifier"

type loadedBloomFilter struct {
	bucket, key, etag string
	filter            *bloom.BloomFilter
}

// BloomFilterLoader loads bloom filter models from S3 into the BloomFilterStore.
type BloomFilterLoader struct {
	mu                        sync.Mutex
	loaded                    map[string]loadedBloomFilter
	store                     *BloomFilterStore
	daoFactory                interfaces.DaoFactoryInterface
	modelConfigurationHandler interfaces.ModelConfigurationHandlerInterface
	timeProvider              interfaces.TimeProvider
}

// NewBloomFilterLoader creates a new BloomFilterLoader.
// localCacheFactory is retained for compatibility; validators are tracked by the loader.
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
		loaded:                    make(map[string]loadedBloomFilter),
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
// It resolves the S3 path and conditionally fetches the object, then
// deserializes using OldPanda bloomfilter.FromBytes(), and stores the result.
func (l *BloomFilterLoader) loadSingleModel(sspIdentifier string, folderPrefix string, modelIdentifier string, modelDef interfaces.ModelDefinition) error {
	l.mu.Lock()
	defer l.mu.Unlock()
	s3Path := l.BuildS3Path(sspIdentifier, modelDef)

	if !strings.HasPrefix(folderPrefix, repository.S3Prefix) {
		return fmt.Errorf("bloom filter loading only supports S3 paths, got: %s", folderPrefix)
	}

	s3BucketName := strings.TrimPrefix(folderPrefix, repository.S3Prefix)
	previous := l.loaded[modelIdentifier]
	current, present := l.store.Get(modelIdentifier)
	etag := ""
	if present && current == previous.filter && previous.bucket == s3BucketName && previous.key == s3Path {
		etag = previous.etag
	}
	var getObjectOutput *s3.GetObjectOutput
	var err error
	if conditional, ok := l.daoFactory.(interfaces.ConditionalS3ObjectFetcher); ok {
		var unchanged bool
		getObjectOutput, unchanged, err = conditional.GetS3ObjectIfChanged(context.TODO(), s3BucketName, s3Path, etag)
		if err == nil && unchanged {
			if current, present := l.store.Get(modelIdentifier); etag != "" && present && current == previous.filter {
				return nil
			}
			// The filter was evicted while the request was in flight. Reload its body.
			getObjectOutput, err = l.daoFactory.GetS3Object(context.TODO(), s3BucketName, s3Path)
		}
	} else {
		getObjectOutput, err = l.daoFactory.GetS3Object(context.TODO(), s3BucketName, s3Path)
	}
	if err != nil {
		loaderLogger.Warn().Msgf("Bloom filter file not found or S3 error for %s/%s: %v", s3BucketName, s3Path, err)
		return fmt.Errorf("error fetching bloom filter S3 file %s/%s: %w", s3BucketName, s3Path, err)
	}

	if getObjectOutput == nil || getObjectOutput.Body == nil {
		return fmt.Errorf("missing bloom filter body for %s", modelIdentifier)
	}
	// Close without draining: draining an unchanged fallback response downloads it.
	defer getObjectOutput.Body.Close()
	if etag != "" && getObjectOutput.ETag != nil && *getObjectOutput.ETag == etag {
		if current, present := l.store.Get(modelIdentifier); present && current == previous.filter {
			return nil
		}
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
	// Commit the validator only after a successful decode and store replacement.
	next := loadedBloomFilter{bucket: s3BucketName, key: s3Path, filter: filter}
	if getObjectOutput.ETag != nil {
		next.etag = *getObjectOutput.ETag
	}
	l.loaded[modelIdentifier] = next
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
