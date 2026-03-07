// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package modelfeature

import (
	"fmt"
	"os"
	"testing"

	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/stretchr/testify/mock"
	"github.com/stretchr/testify/suite"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/repository"
)

func TestConfigurationHandlerSuite(t *testing.T) {
	suite.Run(t, new(ConfigurationHandlerTestSuite))
}

var eTagString = "string"

type ConfigurationHandlerTestSuite struct {
	suite.Suite
	experimentConfigurationHandler *ConfigurationHandler[interfaces.ExperimentConfiguration]
	localCacheFactory              *mockInterfaces.LocalCacheFactoryInterface
	daoFactory                     *mockInterfaces.DaoFactoryInterface
	folderPrefix                   string
	s3FolderPrefix                 string
}

func (suite *ConfigurationHandlerTestSuite) SetupTest() {
	suite.localCacheFactory = mockInterfaces.NewLocalCacheFactoryInterface(suite.T())
	suite.daoFactory = mockInterfaces.NewDaoFactoryInterface(suite.T())

	dir, err := os.Getwd()
	suite.NoError(err, "Failed to get current working directory")
	suite.folderPrefix = dir + "/../testdata"

	suite.s3FolderPrefix = "s3://test-ssp"

	suite.experimentConfigurationHandler = NewConfigurationHandler[interfaces.ExperimentConfiguration](suite.folderPrefix, "ssp", suite.daoFactory, repository.CacheKeyExperimentConfigurationFileIdentifier, repository.CacheKeyExperiment, "experiment", suite.localCacheFactory)
}

func (suite *ConfigurationHandlerTestSuite) TestLoad_ExperimentConfiguration_S3_Success() {
	suite.experimentConfigurationHandler = NewConfigurationHandler[interfaces.ExperimentConfiguration](suite.s3FolderPrefix, "ssp", suite.daoFactory, repository.CacheKeyExperimentConfigurationFileIdentifier, repository.CacheKeyExperiment, "experiment", suite.localCacheFactory)

	suite.localCacheFactory.EXPECT().
		ShouldRefresh(repository.CacheKeyExperimentConfigurationFileIdentifier, mock.Anything).
		Return(true).
		Once()
	fileContent, err := os.ReadFile(suite.folderPrefix + "/ssp/configuration/experiment/config.json")
	suite.NoError(err, "Failed to read file")
	suite.daoFactory.EXPECT().
		GetS3Object(mock.Anything, mock.Anything, mock.Anything).
		Return(&s3.GetObjectOutput{ETag: &eTagString}, nil).
		Once()
	suite.daoFactory.EXPECT().
		ReadContent(mock.Anything).
		Return(fileContent, nil).
		Once()
	suite.localCacheFactory.EXPECT().
		PutToLocalCacheWithTTL(repository.CacheNameConfiguration, repository.CacheKeyExperiment, mock.Anything, int64(0)).
		Return(true).
		Once()

	success, err := suite.experimentConfigurationHandler.Load()
	suite.NoError(err, "Failed to load experiment configuration")
	suite.True(success, "Experiment configuration should be loaded successfully")
}

func (suite *ConfigurationHandlerTestSuite) TestLoad_ExperimentConfiguration_S3_ReturnError_FileNotFound() {
	suite.experimentConfigurationHandler = NewConfigurationHandler[interfaces.ExperimentConfiguration](suite.s3FolderPrefix, "ssp", suite.daoFactory, repository.CacheKeyExperimentConfigurationFileIdentifier, repository.CacheKeyExperiment, "experiment", suite.localCacheFactory)

	suite.daoFactory.EXPECT().
		GetS3Object(mock.Anything, mock.Anything, mock.Anything).
		Return(nil, fmt.Errorf("failed to get object from S3: error")).
		Once()

	success, err := suite.experimentConfigurationHandler.Load()
	suite.EqualError(err, "error fetching s3 file: failed to get object from S3: error")
	suite.False(success, "Experiment configuration should not be loaded successfully")
}

func (suite *ConfigurationHandlerTestSuite) TestLoad_ExperimentConfiguration_S3_NotLoadSinceShouldNotRefresh() {
	suite.experimentConfigurationHandler = NewConfigurationHandler[interfaces.ExperimentConfiguration](suite.s3FolderPrefix, "ssp", suite.daoFactory, repository.CacheKeyExperimentConfigurationFileIdentifier, repository.CacheKeyExperiment, "experiment", suite.localCacheFactory)

	suite.localCacheFactory.EXPECT().
		ShouldRefresh(repository.CacheKeyExperimentConfigurationFileIdentifier, mock.Anything).
		Return(false).
		Once()
	suite.daoFactory.EXPECT().
		GetS3Object(mock.Anything, mock.Anything, mock.Anything).
		Return(&s3.GetObjectOutput{ETag: &eTagString}, nil).
		Once()

	success, err := suite.experimentConfigurationHandler.Load()
	suite.NoError(err, "Failed to load experiment configuration")
	suite.False(success, "Experiment configuration should not be loaded")
}

func (suite *ConfigurationHandlerTestSuite) TestLoad_ExperimentConfiguration_S3_ReturnError_ReadContentError() {
	suite.experimentConfigurationHandler = NewConfigurationHandler[interfaces.ExperimentConfiguration](suite.s3FolderPrefix, "ssp", suite.daoFactory, repository.CacheKeyExperimentConfigurationFileIdentifier, repository.CacheKeyExperiment, "experiment", suite.localCacheFactory)

	suite.localCacheFactory.EXPECT().
		ShouldRefresh(repository.CacheKeyExperimentConfigurationFileIdentifier, mock.Anything).
		Return(true).
		Once()
	suite.daoFactory.EXPECT().
		GetS3Object(mock.Anything, mock.Anything, mock.Anything).
		Return(&s3.GetObjectOutput{ETag: &eTagString}, nil).
		Once()
	suite.daoFactory.EXPECT().
		ReadContent(mock.Anything).
		Return(nil, fmt.Errorf("ReadContent Error")).
		Once()

	success, err := suite.experimentConfigurationHandler.Load()
	suite.EqualError(err, "error getting data: ReadContent Error")
	suite.False(success, "Experiment configuration should not be loaded successfully")
}

func (suite *ConfigurationHandlerTestSuite) TestLoad_ExperimentConfiguration_S3_ReturnError_InvalidJsonData() {
	suite.experimentConfigurationHandler = NewConfigurationHandler[interfaces.ExperimentConfiguration](suite.s3FolderPrefix, "ssp", suite.daoFactory, repository.CacheKeyExperimentConfigurationFileIdentifier, repository.CacheKeyExperiment, "experiment", suite.localCacheFactory)

	suite.localCacheFactory.EXPECT().
		ShouldRefresh(repository.CacheKeyExperimentConfigurationFileIdentifier, mock.Anything).
		Return(true).
		Once()
	fileContent := []byte("invalid-json-data")
	suite.daoFactory.EXPECT().
		GetS3Object(mock.Anything, mock.Anything, mock.Anything).
		Return(&s3.GetObjectOutput{ETag: &eTagString}, nil).
		Once()
	suite.daoFactory.EXPECT().
		ReadContent(mock.Anything).
		Return(fileContent, nil).
		Once()

	success, err := suite.experimentConfigurationHandler.Load()
	suite.EqualError(err, "error unmarshaling JSON: invalid character 'i' looking for beginning of value")
	suite.False(success, "Experiment configuration should not be loaded successfully")
}

func (suite *ConfigurationHandlerTestSuite) TestLoad_ExperimentConfiguration_S3_ReturnError_PutToLocalCacheError() {
	suite.experimentConfigurationHandler = NewConfigurationHandler[interfaces.ExperimentConfiguration](suite.s3FolderPrefix, "ssp", suite.daoFactory, repository.CacheKeyExperimentConfigurationFileIdentifier, repository.CacheKeyExperiment, "experiment", suite.localCacheFactory)

	suite.localCacheFactory.EXPECT().
		ShouldRefresh(repository.CacheKeyExperimentConfigurationFileIdentifier, mock.Anything).
		Return(true).
		Once()
	fileContent, err := os.ReadFile(suite.folderPrefix + "/ssp/configuration/experiment/config.json")
	suite.NoError(err, "Failed to read file")
	suite.daoFactory.EXPECT().
		GetS3Object(mock.Anything, mock.Anything, mock.Anything).
		Return(&s3.GetObjectOutput{ETag: &eTagString}, nil).
		Once()
	suite.daoFactory.EXPECT().
		ReadContent(mock.Anything).
		Return(fileContent, nil).
		Once()
	suite.localCacheFactory.EXPECT().
		PutToLocalCacheWithTTL(repository.CacheNameConfiguration, repository.CacheKeyExperiment, mock.Anything, int64(0)).
		Return(false).
		Once()

	success, err := suite.experimentConfigurationHandler.Load()
	suite.EqualError(err, "error setting data to local cache [Configuration] with identifier [Experiment] and Value [{ExperimentConfiguration map[DemandDrivenTrafficEvaluatorSoftFilter:{DemandDrivenTrafficEvaluatorSoftFilter soft-filter [{T 80} {C 20}] 1654498800000 1727334000000}] map[adsp_low-value_v2:DemandDrivenTrafficEvaluatorSoftFilter]}]")
	suite.False(success, "Experiment configuration should not be loaded successfully")
}

func (suite *ConfigurationHandlerTestSuite) TestLoad_ExperimentConfiguration_Success() {
	suite.localCacheFactory.EXPECT().
		ShouldRefreshLocal(repository.CacheKeyExperimentConfigurationFileIdentifier, mock.Anything).
		Return(true).
		Once()
	fileContent, err := os.ReadFile(suite.folderPrefix + "/ssp/configuration/experiment/config.json")
	suite.NoError(err, "Failed to read file")
	suite.daoFactory.EXPECT().
		GetDataFromLocal(mock.Anything).
		Return(fileContent, nil).
		Once()
	suite.localCacheFactory.EXPECT().
		PutToLocalCacheWithTTL(repository.CacheNameConfiguration, repository.CacheKeyExperiment, mock.Anything, int64(0)).
		Return(true).
		Once()

	success, err := suite.experimentConfigurationHandler.Load()
	suite.NoError(err, "Failed to load experiment configuration")
	suite.True(success, "Experiment configuration should be loaded successfully")
}

func (suite *ConfigurationHandlerTestSuite) TestLoad_ExperimentConfiguration_ReturnError_FileNotFound() {
	suite.experimentConfigurationHandler.folderPrefix = "unknown-folder-prefix"

	success, err := suite.experimentConfigurationHandler.Load()
	suite.EqualError(err, "error opening file: open unknown-folder-prefix/ssp/configuration/experiment/config.json: no such file or directory")
	suite.False(success, "Experiment configuration should not be loaded successfully")
}

func (suite *ConfigurationHandlerTestSuite) TestLoad_ExperimentConfiguration_NotLoadSinceShouldNotRefresh() {
	suite.localCacheFactory.EXPECT().
		ShouldRefreshLocal(repository.CacheKeyExperimentConfigurationFileIdentifier, mock.Anything).
		Return(false).
		Once()

	success, err := suite.experimentConfigurationHandler.Load()
	suite.NoError(err, "Failed to load experiment configuration")
	suite.False(success, "Experiment configuration should not be loaded")
}

func (suite *ConfigurationHandlerTestSuite) TestLoad_ExperimentConfiguration_ReturnError_GetDataFromLocalError() {
	suite.localCacheFactory.EXPECT().
		ShouldRefreshLocal(repository.CacheKeyExperimentConfigurationFileIdentifier, mock.Anything).
		Return(true).
		Once()
	suite.daoFactory.EXPECT().
		GetDataFromLocal(mock.Anything).
		Return(nil, fmt.Errorf("GetDataFromLocal Error")).
		Once()
	success, err := suite.experimentConfigurationHandler.Load()
	suite.EqualError(err, "error getting data: GetDataFromLocal Error")
	suite.False(success, "Experiment configuration should not be loaded successfully")
}

func (suite *ConfigurationHandlerTestSuite) TestLoad_ExperimentConfiguration_ReturnError_InvalidJsonData() {
	suite.localCacheFactory.EXPECT().
		ShouldRefreshLocal(repository.CacheKeyExperimentConfigurationFileIdentifier, mock.Anything).
		Return(true).
		Once()
	fileContent := []byte("invalid-json-data")
	suite.daoFactory.EXPECT().
		GetDataFromLocal(mock.Anything).
		Return(fileContent, nil).
		Once()

	success, err := suite.experimentConfigurationHandler.Load()
	suite.EqualError(err, "error unmarshaling JSON: invalid character 'i' looking for beginning of value")
	suite.False(success, "Experiment configuration should not be loaded successfully")
}

func (suite *ConfigurationHandlerTestSuite) TestLoad_ExperimentConfiguration_ReturnError_PutToLocalCacheError() {
	suite.localCacheFactory.EXPECT().
		ShouldRefreshLocal(repository.CacheKeyExperimentConfigurationFileIdentifier, mock.Anything).
		Return(true).
		Once()
	fileContent, err := os.ReadFile(suite.folderPrefix + "/ssp/configuration/experiment/config.json")
	suite.NoError(err, "Failed to read file")
	suite.daoFactory.EXPECT().
		GetDataFromLocal(mock.Anything).
		Return(fileContent, nil).
		Once()
	suite.localCacheFactory.EXPECT().
		PutToLocalCacheWithTTL(repository.CacheNameConfiguration, repository.CacheKeyExperiment, mock.Anything, int64(0)).
		Return(false).
		Once()

	success, err := suite.experimentConfigurationHandler.Load()
	suite.EqualError(err, "error setting data to local cache [Configuration] with identifier [Experiment] and Value [{ExperimentConfiguration map[DemandDrivenTrafficEvaluatorSoftFilter:{DemandDrivenTrafficEvaluatorSoftFilter soft-filter [{T 80} {C 20}] 1654498800000 1727334000000}] map[adsp_low-value_v2:DemandDrivenTrafficEvaluatorSoftFilter]}]")
	suite.False(success, "Experiment configuration should not be loaded successfully")
}

func (suite *ConfigurationHandlerTestSuite) TestProvide_ExperimentConfiguration_Success() {
	config := interfaces.ExperimentConfiguration{}
	suite.localCacheFactory.EXPECT().
		GetFromLocalCache(repository.CacheNameConfiguration, repository.CacheKeyExperiment).
		Return(config, true).
		Once()

	actualConfig, err := suite.experimentConfigurationHandler.Provide()
	suite.NoError(err, "No error should occur")
	suite.Equal(config, *actualConfig, "Experiment configuration should be provided successfully")
}

func (suite *ConfigurationHandlerTestSuite) TestProvide_ExperimentConfiguration_ReturnError_GetFromLocalCacheError() {
	suite.localCacheFactory.EXPECT().
		GetFromLocalCache(repository.CacheNameConfiguration, repository.CacheKeyExperiment).
		Return(nil, false).
		Once()

	actualConfig, err := suite.experimentConfigurationHandler.Provide()
	suite.EqualError(err, "error getting Config from local cache [Configuration] with Key [Experiment]")
	suite.Nil(actualConfig, "Experiment configuration should be nil")
}

func (suite *ConfigurationHandlerTestSuite) TestProvide_ExperimentConfiguration_ReturnError_ConfigTypeCastError() {
	config := interfaces.ModelConfiguration{}
	suite.localCacheFactory.EXPECT().
		GetFromLocalCache(repository.CacheNameConfiguration, repository.CacheKeyExperiment).
		Return(config, true).
		Once()

	actualConfig, err := suite.experimentConfigurationHandler.Provide()
	suite.EqualError(err, "retrieved config is not of type [experiment]")
	suite.Nil(actualConfig, "Experiment configuration should be nil")
}
