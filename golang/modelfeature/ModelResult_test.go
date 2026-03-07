// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package modelfeature

import (
	"encoding/json"
	"fmt"
	"os"
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
	suite.daoFactory.EXPECT().GetS3Object(mock.Anything, mock.Anything, mock.Anything).Return(&s3.GetObjectOutput{ETag: &eTagString}, nil).Once()
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
	suite.daoFactory.EXPECT().GetS3Object(mock.Anything, mock.Anything, mock.Anything).Return(&s3.GetObjectOutput{ETag: &eTagString}, nil).Once()
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
	suite.daoFactory.EXPECT().GetS3Object(mock.Anything, mock.Anything, mock.Anything).Return(&s3.GetObjectOutput{ETag: &eTagString}, nil).Once()

	err := suite.modelResultHandler.Load("ssp")

	suite.NoError(err, "Failed to load model result")
}

func (suite *ModelResultHandlerTestSuite) TestLoad_S3_ReturnError_ReadContentError() {
	suite.modelResultHandler = NewModelResultHandler("ssp", suite.s3FolderPrefix, suite.daoFactory, suite.modelConfigurationHandler, suite.localCacheFactory, suite.timeProvider)

	suite.modelConfigurationHandler.EXPECT().Provide().Return(&suite.modelConfiguration, nil).Once()
	suite.localCacheFactory.EXPECT().ShouldRefresh(repository.CacheKeyModelResultFileIdentifier, mock.Anything).Return(true).Once()
	suite.daoFactory.EXPECT().GetS3Object(mock.Anything, mock.Anything, mock.Anything).Return(&s3.GetObjectOutput{ETag: &eTagString}, nil).Once()
	suite.daoFactory.EXPECT().ReadContent(mock.Anything).Return(nil, fmt.Errorf("ReadContent Error")).Once()

	err := suite.modelResultHandler.Load("ssp")

	suite.EqualError(err, "error getting data ReadContent Error")
}

func (suite *ModelResultHandlerTestSuite) TestLoad_S3_Success_PutToLocalCacheFail() {
	suite.modelResultHandler = NewModelResultHandler("ssp", suite.s3FolderPrefix, suite.daoFactory, suite.modelConfigurationHandler, suite.localCacheFactory, suite.timeProvider)

	suite.modelConfigurationHandler.EXPECT().Provide().Return(&suite.modelConfiguration, nil).Once()
	suite.localCacheFactory.EXPECT().ShouldRefresh(repository.CacheKeyModelResultFileIdentifier, mock.Anything).Return(true).Once()
	suite.daoFactory.EXPECT().GetS3Object(mock.Anything, mock.Anything, mock.Anything).Return(&s3.GetObjectOutput{ETag: &eTagString}, nil).Once()
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
		Value: modelResultValue,
		Key:   key,
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
		Value: 1.0,
		Key:   key,
	}
	suite.Equal(expectedModelResult, *modelResult, "Model result is not as expected")
}

func (suite *ModelResultHandlerTestSuite) TestProvide_ReturnError_ModelResultValueTypeError() {
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

	suite.EqualError(err, fmt.Sprintf("invalid model result type for identifier %q and Key %q: expected float32, got %T", modelIdentifier, key, modelResultValue))
	suite.Nil(modelResult, "Model result should be nil")
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
