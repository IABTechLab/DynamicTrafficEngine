// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package bloomfilter

import (
	"fmt"
	"io"
	"strings"
	"testing"
	"time"

	bloom "github.com/OldPanda/bloomfilter"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/stretchr/testify/mock"
	"github.com/stretchr/testify/suite"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/repository"
)

func TestBloomFilterLoaderSuite(t *testing.T) {
	suite.Run(t, new(BloomFilterLoaderTestSuite))
}

type BloomFilterLoaderTestSuite struct {
	suite.Suite
	store                     *BloomFilterStore
	daoFactory                *mockInterfaces.DaoFactoryInterface
	localCacheFactory         *mockInterfaces.LocalCacheFactoryInterface
	modelConfigurationHandler *mockInterfaces.ModelConfigurationHandlerInterface
	timeProvider              *mockInterfaces.TimeProvider
	loader                    *BloomFilterLoader
}

func (suite *BloomFilterLoaderTestSuite) SetupTest() {
	suite.store = NewBloomFilterStore()
	suite.daoFactory = mockInterfaces.NewDaoFactoryInterface(suite.T())
	suite.localCacheFactory = mockInterfaces.NewLocalCacheFactoryInterface(suite.T())
	suite.modelConfigurationHandler = mockInterfaces.NewModelConfigurationHandlerInterface(suite.T())
	suite.timeProvider = mockInterfaces.NewTimeProvider(suite.T())
	suite.loader = NewBloomFilterLoader(
		suite.store,
		suite.daoFactory,
		suite.localCacheFactory,
		suite.modelConfigurationHandler,
		suite.timeProvider,
	)
}

// Helper: serialize a bloom filter to bytes for mocking S3 responses
func (suite *BloomFilterLoaderTestSuite) createBloomFilterBytes(keys ...string) []byte {
	filter, err := bloom.NewBloomFilter(10, 0.01)
	suite.Require().NoError(err)
	for _, key := range keys {
		filter.Put(key)
	}
	return filter.ToBytes()
}

// --- BuildS3Path Tests ---

func (suite *BloomFilterLoaderTestSuite) TestBuildS3Path_DynamicMode_ProducesCorrectDateHourPath() {
	// Fixed time: 2024-03-15 14:30:00 UTC
	fixedTime := time.Date(2024, 3, 15, 14, 30, 0, 0, time.UTC)
	suite.timeProvider.EXPECT().Now().Return(fixedTime).Once()

	modelDef := interfaces.ModelDefinition{
		Identifier: "adsp_rsp_v1",
		S3PathMode: interfaces.S3PathModeDynamic,
	}

	result := suite.loader.BuildS3Path("test_ssp", modelDef)

	suite.Equal("test_ssp/2024-03-15/14/adsp_rsp_v1.bloom", result)
}

func (suite *BloomFilterLoaderTestSuite) TestBuildS3Path_StaticMode_ProducesCorrectPath() {
	modelDef := interfaces.ModelDefinition{
		Identifier: "adsp_rsp_v1",
		S3PathMode: interfaces.S3PathModeStatic,
	}

	result := suite.loader.BuildS3Path("test_ssp", modelDef)

	suite.Equal("test_ssp/models/adsp_rsp_v1.bloom", result)
}

func (suite *BloomFilterLoaderTestSuite) TestBuildS3Path_EmptyS3PathMode_DefaultsToDynamic() {
	// Fixed time: 2024-06-01 09:00:00 UTC
	fixedTime := time.Date(2024, 6, 1, 9, 0, 0, 0, time.UTC)
	suite.timeProvider.EXPECT().Now().Return(fixedTime).Once()

	modelDef := interfaces.ModelDefinition{
		Identifier: "my_model_v2",
		S3PathMode: "", // empty defaults to DYNAMIC
	}

	result := suite.loader.BuildS3Path("vendor_a", modelDef)

	suite.Equal("vendor_a/2024-06-01/09/my_model_v2.bloom", result)
}

// --- loadSingleModel Tests ---

func (suite *BloomFilterLoaderTestSuite) TestLoadSingleModel_SkipsWhenETagUnchanged() {
	fixedTime := time.Date(2024, 3, 15, 14, 0, 0, 0, time.UTC)
	suite.timeProvider.EXPECT().Now().Return(fixedTime).Once()

	modelDef := interfaces.ModelDefinition{
		Identifier: "test_model",
		S3PathMode: interfaces.S3PathModeDynamic,
	}

	etag := "etag-unchanged-123"
	suite.daoFactory.EXPECT().
		GetS3Object(mock.Anything, "test-bucket", "test_ssp/2024-03-15/14/test_model.bloom").
		Return(&s3.GetObjectOutput{
			ETag: &etag,
			Body: io.NopCloser(strings.NewReader("")),
		}, nil).Once()

	// ShouldRefresh returns false — ETag unchanged
	suite.localCacheFactory.EXPECT().
		ShouldRefresh(CacheKeyBloomFilterFileIdentifier+"_test_model", etag).
		Return(false).Once()

	err := suite.loader.loadSingleModel("test_ssp", repository.S3Prefix+"test-bucket", "test_model", modelDef)

	suite.NoError(err)
	// Verify store was NOT updated (no filter stored)
	_, found := suite.store.Get("test_model")
	suite.False(found)
}

func (suite *BloomFilterLoaderTestSuite) TestLoadSingleModel_StoresFilterOnSuccessfulLoad() {
	fixedTime := time.Date(2024, 3, 15, 14, 0, 0, 0, time.UTC)
	suite.timeProvider.EXPECT().Now().Return(fixedTime).Once()

	modelDef := interfaces.ModelDefinition{
		Identifier: "test_model",
		S3PathMode: interfaces.S3PathModeDynamic,
	}

	// Create valid bloom filter bytes
	bloomData := suite.createBloomFilterBytes("key1", "key2")

	etag := "etag-new-456"
	suite.daoFactory.EXPECT().
		GetS3Object(mock.Anything, "test-bucket", "test_ssp/2024-03-15/14/test_model.bloom").
		Return(&s3.GetObjectOutput{
			ETag: &etag,
			Body: io.NopCloser(strings.NewReader("")),
		}, nil).Once()

	// ShouldRefresh returns true — ETag changed
	suite.localCacheFactory.EXPECT().
		ShouldRefresh(CacheKeyBloomFilterFileIdentifier+"_test_model", etag).
		Return(true).Once()

	// ReadContent returns valid bloom filter bytes
	suite.daoFactory.EXPECT().
		ReadContent(mock.Anything).
		Return(bloomData, nil).Once()

	err := suite.loader.loadSingleModel("test_ssp", repository.S3Prefix+"test-bucket", "test_model", modelDef)

	suite.NoError(err)
	// Verify store has the filter
	filter, found := suite.store.Get("test_model")
	suite.True(found)
	suite.NotNil(filter)
	// Verify the filter can check membership
	suite.True(filter.MightContain("key1"))
	suite.True(filter.MightContain("key2"))
}

func (suite *BloomFilterLoaderTestSuite) TestLoadSingleModel_PreservesExistingStateOnS3NotFoundError() {
	fixedTime := time.Date(2024, 3, 15, 14, 0, 0, 0, time.UTC)
	suite.timeProvider.EXPECT().Now().Return(fixedTime).Once()

	modelDef := interfaces.ModelDefinition{
		Identifier: "existing_model",
		S3PathMode: interfaces.S3PathModeDynamic,
	}

	// Pre-populate the store with an existing filter
	existingFilter, err := bloom.NewBloomFilter(100, 0.01)
	suite.Require().NoError(err)
	existingFilter.Put("existing_key")
	suite.store.Put("existing_model", existingFilter)

	// S3 returns not-found error
	suite.daoFactory.EXPECT().
		GetS3Object(mock.Anything, "test-bucket", "test_ssp/2024-03-15/14/existing_model.bloom").
		Return(nil, fmt.Errorf("NoSuchKey: The specified key does not exist")).Once()

	loadErr := suite.loader.loadSingleModel("test_ssp", repository.S3Prefix+"test-bucket", "existing_model", modelDef)

	// Error is returned
	suite.Error(loadErr)
	suite.Contains(loadErr.Error(), "error fetching bloom filter S3 file")

	// Existing filter is preserved
	filter, found := suite.store.Get("existing_model")
	suite.True(found)
	suite.NotNil(filter)
	suite.True(filter.MightContain("existing_key"))
}

func (suite *BloomFilterLoaderTestSuite) TestLoadSingleModel_PreservesExistingStateOnDeserializationFailure() {
	fixedTime := time.Date(2024, 3, 15, 14, 0, 0, 0, time.UTC)
	suite.timeProvider.EXPECT().Now().Return(fixedTime).Once()

	modelDef := interfaces.ModelDefinition{
		Identifier: "existing_model",
		S3PathMode: interfaces.S3PathModeDynamic,
	}

	// Pre-populate the store with an existing filter
	existingFilter, err := bloom.NewBloomFilter(100, 0.01)
	suite.Require().NoError(err)
	existingFilter.Put("existing_key")
	suite.store.Put("existing_model", existingFilter)

	etag := "etag-new-789"
	suite.daoFactory.EXPECT().
		GetS3Object(mock.Anything, "test-bucket", "test_ssp/2024-03-15/14/existing_model.bloom").
		Return(&s3.GetObjectOutput{
			ETag: &etag,
			Body: io.NopCloser(strings.NewReader("")),
		}, nil).Once()

	// ShouldRefresh returns true — ETag changed
	suite.localCacheFactory.EXPECT().
		ShouldRefresh(CacheKeyBloomFilterFileIdentifier+"_existing_model", etag).
		Return(true).Once()

	// ReadContent returns invalid bytes that can't be deserialized
	suite.daoFactory.EXPECT().
		ReadContent(mock.Anything).
		Return([]byte("invalid-bloom-data-garbage"), nil).Once()

	loadErr := suite.loader.loadSingleModel("test_ssp", repository.S3Prefix+"test-bucket", "existing_model", modelDef)

	// Error is returned
	suite.Error(loadErr)
	suite.Contains(loadErr.Error(), "error deserializing bloom filter")

	// Existing filter is preserved
	filter, found := suite.store.Get("existing_model")
	suite.True(found)
	suite.NotNil(filter)
	suite.True(filter.MightContain("existing_key"))
}

// --- Load Tests ---

func (suite *BloomFilterLoaderTestSuite) TestLoad_SkipsNonBloomFilterModels() {
	// Configure model configuration with both RULE_BASED and BLOOM_FILTER models
	modelConfig := &interfaces.ModelConfiguration{
		ModelDefinitionByIdentifier: map[string]interfaces.ModelDefinition{
			"rule_model": {
				Identifier:  "rule_model",
				ModelFormat: interfaces.ModelFormatRuleBased,
				S3PathMode:  interfaces.S3PathModeDynamic,
			},
			"empty_format_model": {
				Identifier:  "empty_format_model",
				ModelFormat: "", // empty means RULE_BASED
				S3PathMode:  interfaces.S3PathModeDynamic,
			},
			"bloom_model": {
				Identifier:  "bloom_model",
				ModelFormat: interfaces.ModelFormatBloomFilter,
				S3PathMode:  interfaces.S3PathModeStatic,
			},
		},
	}

	suite.modelConfigurationHandler.EXPECT().Provide().Return(modelConfig, nil).Once()

	// Only bloom_model should trigger S3 interaction — STATIC mode so no time needed
	bloomData := suite.createBloomFilterBytes("bloom_key")
	etag := "etag-bloom"
	suite.daoFactory.EXPECT().
		GetS3Object(mock.Anything, "my-bucket", "test_ssp/models/bloom_model.bloom").
		Return(&s3.GetObjectOutput{
			ETag: &etag,
			Body: io.NopCloser(strings.NewReader("")),
		}, nil).Once()

	suite.localCacheFactory.EXPECT().
		ShouldRefresh(CacheKeyBloomFilterFileIdentifier+"_bloom_model", etag).
		Return(true).Once()

	suite.daoFactory.EXPECT().
		ReadContent(mock.Anything).
		Return(bloomData, nil).Once()

	err := suite.loader.Load("test_ssp", repository.S3Prefix+"my-bucket")

	suite.NoError(err)
	// Verify only the bloom filter model was loaded into the store
	filter, found := suite.store.Get("bloom_model")
	suite.True(found)
	suite.NotNil(filter)

	// Rule-based models should not have been loaded into bloom filter store
	_, foundRule := suite.store.Get("rule_model")
	suite.False(foundRule)
	_, foundEmpty := suite.store.Get("empty_format_model")
	suite.False(foundEmpty)
}
