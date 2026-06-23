// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package modelfeature

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/stretchr/testify/mock"
	"github.com/stretchr/testify/suite"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/repository"
)

func TestModelResultHandlerSuite(t *testing.T) {
	suite.Run(t, new(ModelResultHandlerTestSuite))
}

type ModelResultHandlerTestSuite struct {
	suite.Suite
	modelResultHandler        interfaces.ModelResultHandlerInterface
	folderPrefix              string
	s3FolderPrefix            string
	modelConfigurationHandler *mockInterfaces.ModelConfigurationHandlerInterface
	localCacheFactory         *mockInterfaces.LocalCacheFactoryInterface
	daoFactory                *mockInterfaces.DaoFactoryInterface
	timeProvider              *mockInterfaces.TimeProvider
	modelConfiguration        interfaces.ModelConfiguration
	modelResultData           []byte
}

func (suite *ModelResultHandlerTestSuite) SetupTest() {
	dir, err := os.Getwd()
	suite.NoError(err, "Failed to get current working directory")
	suite.folderPrefix = dir + "/../testdata"
	suite.s3FolderPrefix = "s3://test-ssp"
	suite.modelConfigurationHandler = mockInterfaces.NewModelConfigurationHandlerInterface(suite.T())
	suite.localCacheFactory = mockInterfaces.NewLocalCacheFactoryInterface(suite.T())
	suite.daoFactory = mockInterfaces.NewDaoFactoryInterface(suite.T())
	suite.timeProvider = mockInterfaces.NewTimeProvider(suite.T())

	suite.modelResultHandler = NewModelResultHandler("ssp", suite.folderPrefix, suite.daoFactory, suite.modelConfigurationHandler, suite.localCacheFactory, suite.timeProvider)

	testDataDir := dir + "/../testdata"
	modelConfigurationTestDataFilePath := testDataDir + "/ssp/configuration/model/config.json"
	modelConfigurationData, modelConfigurationDataErr := os.ReadFile(modelConfigurationTestDataFilePath)
	suite.NoError(modelConfigurationDataErr, "Failed to read model configuration test data file")
	jsonErr := json.Unmarshal(modelConfigurationData, &suite.modelConfiguration)
	suite.NoError(jsonErr, "Failed to unmarshal model configuration test data")

	modelResultTestDataFilePath := testDataDir + "/ssp/2024-09-20/00/adsp_low-value_v2.csv"
	var modelResultDataErr error
	suite.modelResultData, modelResultDataErr = os.ReadFile(modelResultTestDataFilePath)
	suite.NoError(modelResultDataErr, "Failed to read model configuration test data file")

	suite.timeProvider.On("Now").Maybe().Return(time.Date(2024, 9, 20, 00, 0, 0, 0, time.UTC))
}

func (suite *ModelResultHandlerTestSuite) TestLoad_S3_Success() {
	suite.modelResultHandler = NewModelResultHandler("ssp", suite.s3FolderPrefix, suite.daoFactory, suite.modelConfigurationHandler, suite.localCacheFactory, suite.timeProvider)

	suite.modelConfigurationHandler.EXPECT().Provide().Return(&suite.modelConfiguration, nil).Once()
	suite.localCacheFactory.EXPECT().ShouldRefresh(repository.CacheKeyModelResultFileIdentifier, mock.Anything).Return(true).Once()
	suite.localCacheFactory.EXPECT().PutToLocalCache(mock.Anything, mock.Anything, mock.Anything).Return(true).Times(4)
	suite.localCacheFactory.EXPECT().ClearLocalCache(mock.Anything).Return()
	suite.daoFactory.EXPECT().GetS3Object(mock.Anything, mock.Anything, mock.Anything).Return(&s3.GetObjectOutput{ETag: &eTagString, Body: io.NopCloser(strings.NewReader(""))}, nil).Once()
	suite.daoFactory.EXPECT().ReadContent(mock.Anything).Return(suite.modelResultData, nil).Once()

	err := suite.modelResultHandler.Load("ssp")

	suite.NoError(err, "Failed to load model result")
}

func (suite *ModelResultHandlerTestSuite) TestLoad_S3_ReturnError_ModelConfigurationHandlerProvideError() {
	suite.modelResultHandler = NewModelResultHandler("ssp", suite.s3FolderPrefix, suite.daoFactory, suite.modelConfigurationHandler, suite.localCacheFactory, suite.timeProvider)

	suite.modelConfigurationHandler.EXPECT().Provide().Return(nil, fmt.Errorf("ModelConfigurationHandlerProvideError")).Once()

	err := suite.modelResultHandler.Load("ssp")

	suite.EqualError(err, "fail to provide modelConfiguration: ModelConfigurationHandlerProvideError")
}

func (suite *ModelResultHandlerTestSuite) TestLoad_S3_Success_InvalidModelDefinition() {
	suite.modelResultHandler = NewModelResultHandler("ssp", suite.s3FolderPrefix, suite.daoFactory, suite.modelConfigurationHandler, suite.localCacheFactory, suite.timeProvider)

	dir, _ := os.Getwd()
	testDataDir := dir + "/../testdata"
	modelConfigurationTestDataFilePath := testDataDir + "/ssp/configuration/model/config.json"
	modelConfigurationData, modelConfigurationDataErr := os.ReadFile(modelConfigurationTestDataFilePath)
	suite.NoError(modelConfigurationDataErr, "Failed to read model configuration test data file")
	var modelConfiguration interfaces.ModelConfiguration
	jsonErr := json.Unmarshal(modelConfigurationData, &modelConfiguration)
	suite.NoError(jsonErr, "Failed to unmarshal model configuration test data")
	modelDef := modelConfiguration.ModelDefinitionByIdentifier["adsp_low-value_v2"]
	modelDef.Type = "invalidType"
	modelConfiguration.ModelDefinitionByIdentifier["adsp_low-value_v2"] = modelDef
	suite.modelConfigurationHandler.EXPECT().Provide().Return(&modelConfiguration, nil).Once()
	suite.localCacheFactory.EXPECT().ShouldRefresh(repository.CacheKeyModelResultFileIdentifier, mock.Anything).Return(true).Once()
	suite.localCacheFactory.EXPECT().PutToLocalCache(mock.Anything, mock.Anything, mock.Anything).Return(true).Times(4)
	suite.localCacheFactory.EXPECT().ClearLocalCache(mock.Anything).Return()
	suite.daoFactory.EXPECT().GetS3Object(mock.Anything, mock.Anything, mock.Anything).Return(&s3.GetObjectOutput{ETag: &eTagString, Body: io.NopCloser(strings.NewReader(""))}, nil).Once()
	suite.daoFactory.EXPECT().ReadContent(mock.Anything).Return(suite.modelResultData, nil).Once()

	err := suite.modelResultHandler.Load("ssp")

	suite.NoError(err, "Failed to load model result")
}

func (suite *ModelResultHandlerTestSuite) TestLoad_S3_Success_FileNotFound() {
	suite.modelResultHandler = NewModelResultHandler("ssp", suite.s3FolderPrefix, suite.daoFactory, suite.modelConfigurationHandler, suite.localCacheFactory, suite.timeProvider)

	suite.modelConfigurationHandler.EXPECT().Provide().Return(&suite.modelConfiguration, nil).Once()
	suite.daoFactory.EXPECT().GetS3Object(mock.Anything, mock.Anything, mock.Anything).Return(nil, fmt.Errorf("failed to get object from S3: error")).Once()

	err := suite.modelResultHandler.Load("ssp")

	suite.NoError(err, "Failed to load model result")
}

func (suite *ModelResultHandlerTestSuite) TestLoad_S3_Success_ShouldNotRefresh() {
	suite.modelResultHandler = NewModelResultHandler("ssp", suite.s3FolderPrefix, suite.daoFactory, suite.modelConfigurationHandler, suite.localCacheFactory, suite.timeProvider)

	suite.modelConfigurationHandler.EXPECT().Provide().Return(&suite.modelConfiguration, nil).Once()
	suite.localCacheFactory.EXPECT().ShouldRefresh(repository.CacheKeyModelResultFileIdentifier, mock.Anything).Return(false).Once()
	suite.daoFactory.EXPECT().GetS3Object(mock.Anything, mock.Anything, mock.Anything).Return(&s3.GetObjectOutput{ETag: &eTagString, Body: io.NopCloser(strings.NewReader(""))}, nil).Once()

	err := suite.modelResultHandler.Load("ssp")

	suite.NoError(err, "Failed to load model result")
}

func (suite *ModelResultHandlerTestSuite) TestLoad_S3_ReturnError_ReadContentError() {
	suite.modelResultHandler = NewModelResultHandler("ssp", suite.s3FolderPrefix, suite.daoFactory, suite.modelConfigurationHandler, suite.localCacheFactory, suite.timeProvider)

	suite.modelConfigurationHandler.EXPECT().Provide().Return(&suite.modelConfiguration, nil).Once()
	suite.localCacheFactory.EXPECT().ShouldRefresh(repository.CacheKeyModelResultFileIdentifier, mock.Anything).Return(true).Once()
	suite.daoFactory.EXPECT().GetS3Object(mock.Anything, mock.Anything, mock.Anything).Return(&s3.GetObjectOutput{ETag: &eTagString, Body: io.NopCloser(strings.NewReader(""))}, nil).Once()
	suite.daoFactory.EXPECT().ReadContent(mock.Anything).Return(nil, fmt.Errorf("ReadContent Error")).Once()

	err := suite.modelResultHandler.Load("ssp")

	suite.EqualError(err, "error getting data ReadContent Error")
}

func (suite *ModelResultHandlerTestSuite) TestLoad_S3_Success_PutToLocalCacheFail() {
	suite.modelResultHandler = NewModelResultHandler("ssp", suite.s3FolderPrefix, suite.daoFactory, suite.modelConfigurationHandler, suite.localCacheFactory, suite.timeProvider)

	suite.modelConfigurationHandler.EXPECT().Provide().Return(&suite.modelConfiguration, nil).Once()
	suite.localCacheFactory.EXPECT().ShouldRefresh(repository.CacheKeyModelResultFileIdentifier, mock.Anything).Return(true).Once()
	suite.daoFactory.EXPECT().GetS3Object(mock.Anything, mock.Anything, mock.Anything).Return(&s3.GetObjectOutput{ETag: &eTagString, Body: io.NopCloser(strings.NewReader(""))}, nil).Once()
	suite.daoFactory.EXPECT().ReadContent(mock.Anything).Return(suite.modelResultData, nil).Once()
	suite.localCacheFactory.EXPECT().PutToLocalCache(mock.Anything, mock.Anything, mock.Anything).Return(false).Times(4)
	suite.localCacheFactory.EXPECT().ClearLocalCache(mock.Anything).Return()

	err := suite.modelResultHandler.Load("ssp")

	suite.NoError(err, "Failed to load model result")
}

func (suite *ModelResultHandlerTestSuite) TestLoad_Success() {
	suite.modelConfigurationHandler.EXPECT().Provide().Return(&suite.modelConfiguration, nil).Once()
	suite.localCacheFactory.EXPECT().ShouldRefreshLocal(repository.CacheKeyModelResultFileIdentifier, mock.Anything).Return(true).Once()
	suite.localCacheFactory.EXPECT().PutToLocalCache(mock.Anything, mock.Anything, mock.Anything).Return(true).Times(4)
	suite.localCacheFactory.EXPECT().ClearLocalCache(mock.Anything).Return()
	suite.daoFactory.EXPECT().GetDataFromLocal(mock.Anything).Return(suite.modelResultData, nil).Once()

	err := suite.modelResultHandler.Load("ssp")

	suite.NoError(err, "Failed to load model result")
}

func (suite *ModelResultHandlerTestSuite) TestLoad_ReturnError_ModelConfigurationHandlerProvideError() {
	suite.modelConfigurationHandler.EXPECT().Provide().Return(nil, fmt.Errorf("ModelConfigurationHandlerProvideError")).Once()

	err := suite.modelResultHandler.Load("ssp")

	suite.EqualError(err, "fail to provide modelConfiguration: ModelConfigurationHandlerProvideError")
}

func (suite *ModelResultHandlerTestSuite) TestLoad_Success_InvalidModelDefinition() {
	dir, _ := os.Getwd()
	testDataDir := dir + "/../testdata"
	modelConfigurationTestDataFilePath := testDataDir + "/ssp/configuration/model/config.json"
	modelConfigurationData, modelConfigurationDataErr := os.ReadFile(modelConfigurationTestDataFilePath)
	suite.NoError(modelConfigurationDataErr, "Failed to read model configuration test data file")
	var modelConfiguration interfaces.ModelConfiguration
	jsonErr := json.Unmarshal(modelConfigurationData, &modelConfiguration)
	suite.NoError(jsonErr, "Failed to unmarshal model configuration test data")
	modelDef := modelConfiguration.ModelDefinitionByIdentifier["adsp_low-value_v2"]
	modelDef.Type = "invalidType"
	modelConfiguration.ModelDefinitionByIdentifier["adsp_low-value_v2"] = modelDef
	suite.modelConfigurationHandler.EXPECT().Provide().Return(&modelConfiguration, nil).Once()
	suite.localCacheFactory.EXPECT().ShouldRefreshLocal(repository.CacheKeyModelResultFileIdentifier, mock.Anything).Return(true).Once()
	suite.localCacheFactory.EXPECT().PutToLocalCache(mock.Anything, mock.Anything, mock.Anything).Return(true).Times(4)
	suite.localCacheFactory.EXPECT().ClearLocalCache(mock.Anything).Return()
	suite.daoFactory.EXPECT().GetDataFromLocal(mock.Anything).Return(suite.modelResultData, nil).Once()

	err := suite.modelResultHandler.Load("ssp")

	suite.NoError(err, "Failed to load model result")
}

func (suite *ModelResultHandlerTestSuite) TestLoad_Success_FileNotFound() {
	suite.modelConfigurationHandler.EXPECT().Provide().Return(&suite.modelConfiguration, nil).Once()
	modelResultHandler := NewModelResultHandler("ssp", "invalid-folder-prefix", suite.daoFactory, suite.modelConfigurationHandler, suite.localCacheFactory, suite.timeProvider)
	err := modelResultHandler.Load("ssp")

	suite.NoError(err, "Failed to load model result")
}

func (suite *ModelResultHandlerTestSuite) TestLoad_Success_ShouldNotRefresh() {
	suite.modelConfigurationHandler.EXPECT().Provide().Return(&suite.modelConfiguration, nil).Once()
	suite.localCacheFactory.EXPECT().ShouldRefreshLocal(repository.CacheKeyModelResultFileIdentifier, mock.Anything).Return(false).Once()

	err := suite.modelResultHandler.Load("ssp")

	suite.NoError(err, "Failed to load model result")
}

func (suite *ModelResultHandlerTestSuite) TestLoad_ReturnError_GetDataFromLocalError() {
	suite.modelConfigurationHandler.EXPECT().Provide().Return(&suite.modelConfiguration, nil).Once()
	suite.localCacheFactory.EXPECT().ShouldRefreshLocal(repository.CacheKeyModelResultFileIdentifier, mock.Anything).Return(true).Once()
	suite.daoFactory.EXPECT().GetDataFromLocal(mock.Anything).Return(nil, fmt.Errorf("GetDataFromLocalError")).Once()

	err := suite.modelResultHandler.Load("ssp")

	suite.EqualError(err, "error getting data GetDataFromLocalError")
}

func (suite *ModelResultHandlerTestSuite) TestLoad_Success_PutToLocalCacheFail() {
	suite.modelConfigurationHandler.EXPECT().Provide().Return(&suite.modelConfiguration, nil).Once()
	suite.localCacheFactory.EXPECT().ShouldRefreshLocal(repository.CacheKeyModelResultFileIdentifier, mock.Anything).Return(true).Once()
	suite.localCacheFactory.EXPECT().PutToLocalCache(mock.Anything, mock.Anything, mock.Anything).Return(false).Times(4)
	suite.localCacheFactory.EXPECT().ClearLocalCache(mock.Anything).Return()
	suite.daoFactory.EXPECT().GetDataFromLocal(mock.Anything).Return(suite.modelResultData, nil).Once()

	err := suite.modelResultHandler.Load("ssp")

	suite.NoError(err, "Failed to load model result")
}

func (suite *ModelResultHandlerTestSuite) TestLoad_CacheValueByModelType() {
	tests := []struct {
		name               string
		modelType          interfaces.ModelType
		expectedCacheValue float32
	}{
		{
			name:               "LowValue model stores 0.0 in cache",
			modelType:          "LowValue",
			expectedCacheValue: 0.0,
		},
		{
			name:               "HighValue model stores 1.0 in cache",
			modelType:          "HighValue",
			expectedCacheValue: 1.0,
		},
		{
			name:               "Unrecognized model type stores 0.0 in cache",
			modelType:          "UnknownType",
			expectedCacheValue: 0.0,
		},
	}

	for _, tt := range tests {
		suite.Run(tt.name, func() {
			// Create fresh mocks for each sub-test
			modelConfigHandler := mockInterfaces.NewModelConfigurationHandlerInterface(suite.T())
			localCache := mockInterfaces.NewLocalCacheFactoryInterface(suite.T())
			daoFactory := mockInterfaces.NewDaoFactoryInterface(suite.T())
			timeProvider := mockInterfaces.NewTimeProvider(suite.T())

			timeProvider.On("Now").Return(time.Date(2024, 9, 20, 0, 0, 0, 0, time.UTC))

			handler := NewModelResultHandler("ssp", suite.folderPrefix, daoFactory, modelConfigHandler, localCache, timeProvider)

			// Build a model configuration with the desired model type
			modelConfig := interfaces.ModelConfiguration{
				ModelDefinitionByIdentifier: map[string]interfaces.ModelDefinition{
					"adsp_low-value_v2": {
						Identifier: "adsp_low-value_v2",
						Type:       tt.modelType,
					},
				},
			}
			modelConfigHandler.EXPECT().Provide().Return(&modelConfig, nil).Once()
			localCache.EXPECT().ShouldRefreshLocal(repository.CacheKeyModelResultFileIdentifier, mock.Anything).Return(true).Once()
			localCache.EXPECT().ClearLocalCache("adsp_low-value_v2").Return()
			daoFactory.EXPECT().GetDataFromLocal(mock.Anything).Return(suite.modelResultData, nil).Once()

			// Verify that PutToLocalCache is called with the expected cache value (3rd argument)
			localCache.EXPECT().PutToLocalCache(
				"adsp_low-value_v2",
				mock.AnythingOfType("string"),
				tt.expectedCacheValue,
			).Return(true).Times(4)

			err := handler.Load("ssp")
			suite.NoError(err)
		})
	}
}

func (suite *ModelResultHandlerTestSuite) TestLoad_S3_SkipsBloomFilterModels() {
	suite.modelResultHandler = NewModelResultHandler("ssp", suite.s3FolderPrefix, suite.daoFactory, suite.modelConfigurationHandler, suite.localCacheFactory, suite.timeProvider)

	// Configure a model configuration with both RULE_BASED and BLOOM_FILTER models
	modelConfig := interfaces.ModelConfiguration{
		ModelDefinitionByIdentifier: map[string]interfaces.ModelDefinition{
			"rule_based_model": {
				Identifier:  "rule_based_model",
				Type:        "LowValue",
				ModelFormat: interfaces.ModelFormatRuleBased,
			},
			"bloom_filter_model": {
				Identifier:  "bloom_filter_model",
				Type:        "LowValue",
				ModelFormat: interfaces.ModelFormatBloomFilter,
			},
		},
	}

	suite.modelConfigurationHandler.EXPECT().Provide().Return(&modelConfig, nil).Once()
	// S3 fetch should only happen for the RULE_BASED model (once, not twice)
	suite.localCacheFactory.EXPECT().ShouldRefresh(repository.CacheKeyModelResultFileIdentifier, mock.Anything).Return(true).Once()
	suite.localCacheFactory.EXPECT().PutToLocalCache(mock.Anything, mock.Anything, mock.Anything).Return(true).Times(4)
	suite.localCacheFactory.EXPECT().ClearLocalCache(mock.Anything).Return()
	suite.daoFactory.EXPECT().GetS3Object(mock.Anything, mock.Anything, mock.Anything).Return(&s3.GetObjectOutput{ETag: &eTagString, Body: io.NopCloser(strings.NewReader(""))}, nil).Once()
	suite.daoFactory.EXPECT().ReadContent(mock.Anything).Return(suite.modelResultData, nil).Once()

	err := suite.modelResultHandler.Load("ssp")

	suite.NoError(err, "Load should succeed")
	// The BLOOM_FILTER model should be skipped entirely - no S3 fetch or cache operations for it.
	// If it weren't skipped, GetS3Object would be called twice causing mock assertion failure.
}

func (suite *ModelResultHandlerTestSuite) TestProvide_Success() {
	modelIdentifier := "modelIdentifier"
	key := "site|video|5895-EB|USA|640x390|u|0"
	features := []interfaces.ModelFeature{
		{Values: []string{"site"}},
		{Values: []string{"video"}},
		{Values: []string{"5895-EB"}},
		{Values: []string{"USA"}},
		{Values: []string{"640x390"}},
		{Values: []string{"u"}},
		{Values: []string{"0"}},
	}
	modelResultValue := float32(0.0)
	suite.localCacheFactory.EXPECT().GetFromLocalCache(modelIdentifier, key).Return(modelResultValue, true).Once()

	modelResult, err := suite.modelResultHandler.Provide(modelIdentifier, features, 1.0)

	suite.NoError(err, "Failed to load model result")
	expectedModelResult := interfaces.ModelResult{
		Value:  modelResultValue,
		Key:    key,
		Keys:   []string{key},
		Values: []float32{modelResultValue},
	}
	suite.Equal(expectedModelResult, *modelResult, "Model result is not as expected")
}

func (suite *ModelResultHandlerTestSuite) TestProvide_ReturnSuccess_GetFromLocalCacheMissing() {
	modelIdentifier := "modelIdentifier"
	key := "site|video|5895-EB|USA|640x390|u|0"
	features := []interfaces.ModelFeature{
		{Values: []string{"site"}},
		{Values: []string{"video"}},
		{Values: []string{"5895-EB"}},
		{Values: []string{"USA"}},
		{Values: []string{"640x390"}},
		{Values: []string{"u"}},
		{Values: []string{"0"}},
	}
	suite.localCacheFactory.EXPECT().GetFromLocalCache(modelIdentifier, key).Return(nil, false).Once()

	modelResult, err := suite.modelResultHandler.Provide(modelIdentifier, features, 1.0)

	suite.NoError(err, "Failed to load model result")
	expectedModelResult := interfaces.ModelResult{
		Value:  1.0,
		Key:    key,
		Keys:   []string{key},
		Values: []float32{1.0},
	}
	suite.Equal(expectedModelResult, *modelResult, "Model result is not as expected")
}

func (suite *ModelResultHandlerTestSuite) TestBuildKeys() {
	handler := suite.modelResultHandler.(*ModelResultHandler)

	tests := []struct {
		name     string
		features []interfaces.ModelFeature
		expected []string
	}{
		{
			name: "single-valued features produce single key",
			features: []interfaces.ModelFeature{
				{Values: []string{"site"}},
				{Values: []string{"video"}},
				{Values: []string{"USA"}},
			},
			expected: []string{"site|video|USA"},
		},
		{
			name: "multi-valued features produce Cartesian product",
			features: []interfaces.ModelFeature{
				{Values: []string{"a", "b"}},
				{Values: []string{"x", "y", "z"}},
			},
			expected: []string{"a|x", "a|y", "a|z", "b|x", "b|y", "b|z"},
		},
		{
			name:     "empty feature list returns empty slice",
			features: []interfaces.ModelFeature{},
			expected: []string{},
		},
		{
			name: "feature with nil Values returns empty slice",
			features: []interfaces.ModelFeature{
				{Values: []string{"a"}},
				{Values: nil},
				{Values: []string{"c"}},
			},
			expected: []string{},
		},
		{
			name: "feature with empty Values slice returns empty slice",
			features: []interfaces.ModelFeature{
				{Values: []string{"a"}},
				{Values: []string{}},
				{Values: []string{"c"}},
			},
			expected: []string{},
		},
		{
			name: "caps at 100 keys when Cartesian product exceeds limit",
			features: []interfaces.ModelFeature{
				{Values: generateValues("f1_", 11)},
				{Values: generateValues("f2_", 11)},
			},
			expected: nil, // checked separately for length
		},
	}

	for _, tt := range tests {
		suite.Run(tt.name, func() {
			result := handler.BuildKeys(tt.features)
			if tt.name == "caps at 100 keys when Cartesian product exceeds limit" {
				// 11 * 11 = 121 > 100, so should be capped at 100
				suite.Equal(100, len(result), "BuildKeys should cap at MaxKeys (100)")
			} else {
				suite.Equal(tt.expected, result)
			}
		})
	}
}

func (suite *ModelResultHandlerTestSuite) TestBuildKeys_BackwardCompatibility() {
	handler := suite.modelResultHandler.(*ModelResultHandler)

	// For single-valued features, BuildKeys[0] must equal BuildKey
	tests := []struct {
		name     string
		features []interfaces.ModelFeature
	}{
		{
			name: "simple single-valued features",
			features: []interfaces.ModelFeature{
				{Values: []string{"site"}},
				{Values: []string{"video"}},
				{Values: []string{"5895-EB"}},
				{Values: []string{"USA"}},
				{Values: []string{"640x390"}},
				{Values: []string{"u"}},
				{Values: []string{"0"}},
			},
		},
		{
			name: "single feature with one value",
			features: []interfaces.ModelFeature{
				{Values: []string{"only-value"}},
			},
		},
		{
			name: "two features with one value each",
			features: []interfaces.ModelFeature{
				{Values: []string{"deal123"}},
				{Values: []string{"pub456"}},
			},
		},
	}

	for _, tt := range tests {
		suite.Run(tt.name, func() {
			keys := handler.BuildKeys(tt.features)
			key := handler.BuildKey(tt.features)
			suite.Require().NotEmpty(keys, "BuildKeys should return at least one key for single-valued features")
			suite.Equal(key, keys[0], "BuildKeys[0] must equal BuildKey for single-valued features")
		})
	}
}

// generateValues creates a slice of n values with the given prefix.
func generateValues(prefix string, n int) []string {
	values := make([]string, n)
	for i := 0; i < n; i++ {
		values[i] = fmt.Sprintf("%s%d", prefix, i)
	}
	return values
}

func (suite *ModelResultHandlerTestSuite) TestProvide_NonFloat32CacheValue_TreatedAsMiss() {
	modelIdentifier := "modelIdentifier"
	key := "site|video|5895-EB|USA|640x390|u|0"
	features := []interfaces.ModelFeature{
		{Values: []string{"site"}},
		{Values: []string{"video"}},
		{Values: []string{"5895-EB"}},
		{Values: []string{"USA"}},
		{Values: []string{"640x390"}},
		{Values: []string{"u"}},
		{Values: []string{"0"}},
	}
	modelResultValue := int(0)
	suite.localCacheFactory.EXPECT().GetFromLocalCache(modelIdentifier, key).Return(modelResultValue, true).Once()

	modelResult, err := suite.modelResultHandler.Provide(modelIdentifier, features, 1.0)

	suite.NoError(err, "Non-float32 should be treated as a miss, not an error")
	expectedModelResult := interfaces.ModelResult{
		Value:  1.0,
		Key:    key,
		Keys:   []string{key},
		Values: []float32{1.0},
	}
	suite.Equal(expectedModelResult, *modelResult, "Model result is not as expected")
}

func (suite *ModelResultHandlerTestSuite) TestProvide_MultiValueFeatures_FirstKeyMisses_SecondKeyHits() {
	modelIdentifier := "modelIdentifier"
	// Two features: first has 2 values, second has 1 value → 2 keys
	features := []interfaces.ModelFeature{
		{Values: []string{"dealA", "dealB"}},
		{Values: []string{"pub1"}},
	}
	// key1 = "dealA|pub1" → miss, key2 = "dealB|pub1" → hit
	suite.localCacheFactory.EXPECT().GetFromLocalCache(modelIdentifier, "dealA|pub1").Return(nil, false).Once()
	suite.localCacheFactory.EXPECT().GetFromLocalCache(modelIdentifier, "dealB|pub1").Return(float32(1.0), true).Once()

	modelResult, err := suite.modelResultHandler.Provide(modelIdentifier, features, 0.0)

	suite.NoError(err)
	suite.Equal(float32(1.0), modelResult.Value, "Value should be the second key's cached value (first hit)")
	suite.Equal("dealB|pub1", modelResult.Key, "Key should be the second key (first hit)")
	suite.Equal([]string{"dealA|pub1", "dealB|pub1"}, modelResult.Keys)
	suite.Equal([]float32{0.0, 1.0}, modelResult.Values, "Values[0]=default (miss), Values[1]=cached (hit)")
	suite.Equal(len(modelResult.Keys), len(modelResult.Values), "Keys and Values must have equal length")
}

func (suite *ModelResultHandlerTestSuite) TestProvide_MultiValueFeatures_AllKeysMiss() {
	modelIdentifier := "modelIdentifier"
	// Two features: first has 2 values, second has 1 value → 2 keys
	features := []interfaces.ModelFeature{
		{Values: []string{"dealX", "dealY"}},
		{Values: []string{"pub2"}},
	}
	// Both keys miss
	suite.localCacheFactory.EXPECT().GetFromLocalCache(modelIdentifier, "dealX|pub2").Return(nil, false).Once()
	suite.localCacheFactory.EXPECT().GetFromLocalCache(modelIdentifier, "dealY|pub2").Return(nil, false).Once()

	modelResult, err := suite.modelResultHandler.Provide(modelIdentifier, features, 0.0)

	suite.NoError(err)
	suite.Equal(float32(0.0), modelResult.Value, "Value should be defaultValue when all keys miss")
	suite.Equal("dealX|pub2", modelResult.Key, "Key should be first key when all miss")
	suite.Equal([]string{"dealX|pub2", "dealY|pub2"}, modelResult.Keys)
	suite.Equal([]float32{0.0, 0.0}, modelResult.Values, "All values should be defaultValue on miss")
	suite.Equal(len(modelResult.Keys), len(modelResult.Values))
}

func (suite *ModelResultHandlerTestSuite) TestProvide_MultiValueFeatures_FirstKeyHits() {
	modelIdentifier := "modelIdentifier"
	// Two features: first has 2 values, second has 1 value → 2 keys
	features := []interfaces.ModelFeature{
		{Values: []string{"dealA", "dealB"}},
		{Values: []string{"pub1"}},
	}
	// key1 = "dealA|pub1" → hit, key2 = "dealB|pub1" → miss
	suite.localCacheFactory.EXPECT().GetFromLocalCache(modelIdentifier, "dealA|pub1").Return(float32(1.0), true).Once()
	suite.localCacheFactory.EXPECT().GetFromLocalCache(modelIdentifier, "dealB|pub1").Return(nil, false).Once()

	modelResult, err := suite.modelResultHandler.Provide(modelIdentifier, features, 0.0)

	suite.NoError(err)
	suite.Equal(float32(1.0), modelResult.Value, "Value should be the first key's cached value")
	suite.Equal("dealA|pub1", modelResult.Key, "Key should be the first key (first hit)")
	suite.Equal([]string{"dealA|pub1", "dealB|pub1"}, modelResult.Keys)
	suite.Equal([]float32{1.0, 0.0}, modelResult.Values, "Values[0]=cached (hit), Values[1]=default (miss)")
	suite.Equal(len(modelResult.Keys), len(modelResult.Values))
}

func (suite *ModelResultHandlerTestSuite) TestProvide_EmptyFeatures_ReturnsDefault() {
	modelIdentifier := "modelIdentifier"
	// Empty features → BuildKeys returns empty → Provide returns default
	features := []interfaces.ModelFeature{}

	modelResult, err := suite.modelResultHandler.Provide(modelIdentifier, features, 1.0)

	suite.NoError(err)
	suite.Equal(float32(1.0), modelResult.Value, "Value should be defaultValue when no features")
	suite.Equal("", modelResult.Key, "Key should be empty string when no features")
	suite.Equal([]string{""}, modelResult.Keys, "Keys should contain a single empty string entry")
	suite.Equal([]float32{1.0}, modelResult.Values, "Values should contain defaultValue")
	suite.Equal(len(modelResult.Keys), len(modelResult.Values))
}

func (suite *ModelResultHandlerTestSuite) TestProvide_FeatureWithEmptyValues_ReturnsDefault() {
	modelIdentifier := "modelIdentifier"
	// Feature with empty values → BuildKeys returns empty → Provide returns default
	features := []interfaces.ModelFeature{
		{Values: []string{"dealA"}},
		{Values: []string{}},
	}

	modelResult, err := suite.modelResultHandler.Provide(modelIdentifier, features, 1.0)

	suite.NoError(err)
	suite.Equal(float32(1.0), modelResult.Value, "Value should be defaultValue when BuildKeys is empty")
	suite.Equal("", modelResult.Key)
	suite.Equal([]string{""}, modelResult.Keys)
	suite.Equal([]float32{1.0}, modelResult.Values)
	suite.Equal(len(modelResult.Keys), len(modelResult.Values))
}

func (suite *ModelResultHandlerTestSuite) TestProvide_MultiValueFeatures_ValuesMatchCacheStatePerKey() {
	modelIdentifier := "modelIdentifier"
	// 3 features with multiple values → multiple keys: verify each Values[i] matches cache state
	features := []interfaces.ModelFeature{
		{Values: []string{"dealA", "dealB", "dealC"}},
		{Values: []string{"pub1"}},
	}
	// key1 = "dealA|pub1" → miss, key2 = "dealB|pub1" → hit(1.0), key3 = "dealC|pub1" → hit(1.0)
	suite.localCacheFactory.EXPECT().GetFromLocalCache(modelIdentifier, "dealA|pub1").Return(nil, false).Once()
	suite.localCacheFactory.EXPECT().GetFromLocalCache(modelIdentifier, "dealB|pub1").Return(float32(1.0), true).Once()
	suite.localCacheFactory.EXPECT().GetFromLocalCache(modelIdentifier, "dealC|pub1").Return(float32(1.0), true).Once()

	modelResult, err := suite.modelResultHandler.Provide(modelIdentifier, features, 0.0)

	suite.NoError(err)
	// First hit is "dealB|pub1"
	suite.Equal(float32(1.0), modelResult.Value)
	suite.Equal("dealB|pub1", modelResult.Key)
	suite.Equal([]string{"dealA|pub1", "dealB|pub1", "dealC|pub1"}, modelResult.Keys)
	// Values[0]=default (miss), Values[1]=cached (hit), Values[2]=cached (hit)
	suite.Equal([]float32{0.0, 1.0, 1.0}, modelResult.Values)
	suite.Equal(len(modelResult.Keys), len(modelResult.Values))

	// Verify each value individually
	for i, key := range modelResult.Keys {
		if key == "dealA|pub1" {
			suite.Equal(float32(0.0), modelResult.Values[i], "Miss key should have defaultValue")
		} else {
			suite.Equal(float32(1.0), modelResult.Values[i], "Hit key should have cached value")
		}
	}
}

func TestTransformTestSuite(t *testing.T) {
	suite.Run(t, new(TransformTestSuite))
}

type TransformTestSuite struct {
	suite.Suite
	transformer1 interfaces.TransformerName
	transformer2 interfaces.TransformerName
}

func (suite *TransformTestSuite) SetupSuite() {
	suite.transformer1 = "transformer1"
	suite.transformer2 = "transformer2"
}

func (suite *TransformTestSuite) TestTransform_Success() {
	// Setup
	TransformerMap = map[interfaces.TransformerName]Transformer{
		suite.transformer1: func(f *interfaces.ModelFeature) (*interfaces.ModelFeature, error) {
			f.Values = append(f.Values, "1")
			return f, nil
		},
		suite.transformer2: func(f *interfaces.ModelFeature) (*interfaces.ModelFeature, error) {
			f.Values = append(f.Values, "2")
			return f, nil
		},
	}

	feature := &interfaces.ModelFeature{
		Configuration: &interfaces.FeatureConfiguration{
			Transformations: []interfaces.TransformerName{suite.transformer1, suite.transformer2},
		},
		Values: []string{"initial"},
	}

	// Execute
	result, err := Transform(feature)

	// Assert
	suite.NoError(err)
	suite.Equal([]string{"initial", "1", "2"}, result.Values)
}

func (suite *TransformTestSuite) TestTransform_ReturnError_NonExistentTransformer() {
	// Setup
	TransformerMap = map[interfaces.TransformerName]Transformer{
		suite.transformer1: func(f *interfaces.ModelFeature) (*interfaces.ModelFeature, error) {
			f.Values = append(f.Values, "1")
			return f, nil
		},
	}

	feature := &interfaces.ModelFeature{
		Configuration: &interfaces.FeatureConfiguration{
			Transformations: []interfaces.TransformerName{suite.transformer1, suite.transformer2},
		},
		Values: []string{"initial"},
	}

	// Execute
	result, err := Transform(feature)

	// Assert
	suite.Error(err)
	suite.Nil(result)
	suite.Contains(err.Error(), "transformer [transformer2] not found")
}

func (suite *TransformTestSuite) TestTransform_ReturnError_OneTransformerError() {
	// Setup
	TransformerMap = map[interfaces.TransformerName]Transformer{
		suite.transformer1: func(f *interfaces.ModelFeature) (*interfaces.ModelFeature, error) {
			return f, nil
		},
		suite.transformer2: func(f *interfaces.ModelFeature) (*interfaces.ModelFeature, error) {
			return nil, fmt.Errorf("transformer2 error")
		},
	}

	feature := &interfaces.ModelFeature{
		Configuration: &interfaces.FeatureConfiguration{
			Transformations: []interfaces.TransformerName{suite.transformer1, suite.transformer2},
		},
		Values: []string{"initial"},
	}

	// Execute
	result, err := Transform(feature)

	// Assert
	suite.Error(err)
	suite.Nil(result)
	suite.Contains(err.Error(), "transformer [transformer2] fail to transform the feature")
	suite.Contains(err.Error(), "transformer2 error")
}

func (suite *TransformTestSuite) TestTransform_Success_NoTransformations() {
	// Setup
	feature := &interfaces.ModelFeature{
		Configuration: &interfaces.FeatureConfiguration{
			Transformations: []interfaces.TransformerName{},
		},
		Values: []string{"initial"},
	}

	// Execute
	result, err := Transform(feature)

	// Assert
	suite.NoError(err)
	suite.Equal(feature, result)
}
