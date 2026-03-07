// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package demanddriventrafficevaluator

import (
	"context"
	"sync"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/modelfeature"
	"golang.a2z.com/demanddriventrafficevaluator/repository"
	"golang.a2z.com/demanddriventrafficevaluator/util"
)

// DummyCredentialsProvider implements aws.CredentialsProvider
type DummyCredentialsProvider struct {
	creds aws.Credentials
}

func NewDummyCredentialsProvider() aws.CredentialsProvider {
	return &DummyCredentialsProvider{
		creds: aws.Credentials{
			AccessKeyID:     "dummy-access-key",
			SecretAccessKey: "dummy-secret-key",
			SessionToken:    "dummy-session-token",
			Source:          "DummyCredentialsProvider",
			Expires:         time.Now().Add(1 * time.Hour),
		},
	}
}

func (p *DummyCredentialsProvider) Retrieve(ctx context.Context) (aws.Credentials, error) {
	return p.creds, nil
}

type InitializerTestSuite struct {
	suite.Suite
	mockTrafficAllocator   *mockInterfaces.TrafficAllocatorInterface
	mockModelConfigHandler *mockInterfaces.ModelConfigurationHandlerInterface
}

func (suite *InitializerTestSuite) SetupTest() {
	// Reset global variables before each test
	modelConfigurationHandler = nil
	experimentConfigurationHandler = nil
	modelResultHandler = nil
	trafficAllocator = nil
	daoFactory = nil
	localCacheFactory = nil
	timeProvider = nil

	modelConfigOnce = sync.Once{}
	experimentConfigOnce = sync.Once{}
	modelResultOnce = sync.Once{}
	trafficAllocatorOnce = sync.Once{}
	daoFactoryOnce = sync.Once{}
	localCacheFactoryOnce = sync.Once{}
	timeProviderOnce = sync.Once{}
}

func (suite *InitializerTestSuite) TestNewTaskInitializer() {
	dummyCredentialsProvider := NewDummyCredentialsProvider()
	initializer := NewTaskInitializer("testSupplier", dummyCredentialsProvider, "us-east-1", "test/folder", 1000)
	assert.NotNil(suite.T(), initializer)
	assert.Len(suite.T(), initializer.StageOneTasks, 2)
	assert.Len(suite.T(), initializer.StageTwoTasks, 1)
}

func (suite *InitializerTestSuite) TestNewRequestEvaluator() {
	dummyCredentialsProvider := NewDummyCredentialsProvider()
	evaluator := NewRequestEvaluator("testSupplier", dummyCredentialsProvider, "us-east-1", "test/folder")
	assert.NotNil(suite.T(), evaluator)
}

func (suite *InitializerTestSuite) TestGetModelConfigurationHandler() {
	dummyCredentialsProvider := NewDummyCredentialsProvider()
	handler := getModelConfigurationHandler("testSupplier", "test/folder", "us-east-1", dummyCredentialsProvider)
	assert.NotNil(suite.T(), handler)
	assert.IsType(suite.T(), &modelfeature.ModelConfigurationHandler{}, handler)

	// Call again to test singleton behavior
	handler2 := getModelConfigurationHandler("testSupplier", "test/folder", "us-east-1", dummyCredentialsProvider)
	assert.Equal(suite.T(), handler, handler2)
}

func (suite *InitializerTestSuite) TestGetExperimentConfigurationHandler() {
	dummyCredentialsProvider := NewDummyCredentialsProvider()
	handler := getExperimentConfigurationHandler("testSupplier", "test/folder", "us-east-1", dummyCredentialsProvider, suite.mockTrafficAllocator)
	assert.NotNil(suite.T(), handler)
	assert.IsType(suite.T(), &modelfeature.ExperimentConfigurationHandler{}, handler)

	// Call again to test singleton behavior
	handler2 := getExperimentConfigurationHandler("testSupplier", "test/folder", "us-east-1", dummyCredentialsProvider, suite.mockTrafficAllocator)
	assert.Equal(suite.T(), handler, handler2)
}

func (suite *InitializerTestSuite) TestGetModelResultHandler() {
	dummyCredentialsProvider := NewDummyCredentialsProvider()
	handler := getModelResultHandler("testSupplier", "test/folder", "us-east-1", dummyCredentialsProvider, suite.mockModelConfigHandler)
	assert.NotNil(suite.T(), handler)
	assert.IsType(suite.T(), &modelfeature.ModelResultHandler{}, handler)

	// Call again to test singleton behavior
	handler2 := getModelResultHandler("testSupplier", "test/folder", "us-east-1", dummyCredentialsProvider, suite.mockModelConfigHandler)
	assert.Equal(suite.T(), handler, handler2)
}

func (suite *InitializerTestSuite) TestGetTrafficAllocator() {
	allocator := getTrafficAllocator()
	assert.NotNil(suite.T(), allocator)
	assert.IsType(suite.T(), &modelfeature.TrafficAllocator{}, allocator)

	// Call again to test singleton behavior
	allocator2 := getTrafficAllocator()
	assert.Equal(suite.T(), allocator, allocator2)
}

func (suite *InitializerTestSuite) TestGetDaoFactory() {
	dummyCredentialsProvider := NewDummyCredentialsProvider()
	factory := getDaoFactory("us-east-1", dummyCredentialsProvider)
	assert.NotNil(suite.T(), factory)
	assert.IsType(suite.T(), &repository.DaoFactory{}, factory)

	// Call again to test singleton behavior
	factory2 := getDaoFactory("us-east-1", dummyCredentialsProvider)
	assert.Equal(suite.T(), factory, factory2)
}

func (suite *InitializerTestSuite) TestGetLocalCacheFactory() {
	factory := getLocalCacheFactory()
	assert.NotNil(suite.T(), factory)
	assert.IsType(suite.T(), &repository.LocalCacheFactory{}, factory)

	// Call again to test singleton behavior
	factory2 := getLocalCacheFactory()
	assert.Equal(suite.T(), factory, factory2)
}

func (suite *InitializerTestSuite) TestGetTimeProvider() {
	provider := getTimeProvider()
	assert.NotNil(suite.T(), provider)
	assert.IsType(suite.T(), &util.RealTimeProvider{}, provider)

	// Call again to test singleton behavior
	provider2 := getTimeProvider()
	assert.Equal(suite.T(), provider, provider2)
}

func TestInitializerSuite(t *testing.T) {
	suite.Run(t, new(InitializerTestSuite))
}
