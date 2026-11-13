// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package repository

import (
	"fmt"
	"os"
	"sync"
	"testing"
	"time"

	"github.com/dgraph-io/ristretto/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
	"github.com/stretchr/testify/suite"
)

type LocalCacheRegistryTestSuite struct {
	suite.Suite
	registry *LocalCacheRegistry
}

func (suite *LocalCacheRegistryTestSuite) SetupTest() {
	suite.registry = &LocalCacheRegistry{
		localCacheMap: make(map[string]*ristretto.Cache[string, any]),
	}
}

func (suite *LocalCacheRegistryTestSuite) TestRegisterAndGet() {
	cacheName := "testCache"
	cache, err := ristretto.NewCache[string, any](&ristretto.Config[string, any]{
		NumCounters: 1000,
		MaxCost:     100,
		BufferItems: 64,
	})
	assert.NoError(suite.T(), err)

	// Test Register
	suite.registry.Register(cacheName, cache)

	// Test Get
	retrievedCache, exists := suite.registry.Get(cacheName)
	assert.True(suite.T(), exists)
	assert.Equal(suite.T(), cache, retrievedCache)

	// Test Get for non-existent cache
	_, exists = suite.registry.Get("nonExistentCache")
	assert.False(suite.T(), exists)
}

func (suite *LocalCacheRegistryTestSuite) TestConcurrentAccess() {
	const numGoroutines = 100
	const numOperations = 1000

	var wg sync.WaitGroup
	wg.Add(numGoroutines)

	for i := 0; i < numGoroutines; i++ {
		go func(id int) {
			defer wg.Done()
			cacheName := fmt.Sprintf("cache%d", id)
			cache, _ := ristretto.NewCache[string, any](&ristretto.Config[string, any]{
				NumCounters: 1000,
				MaxCost:     100,
				BufferItems: 64,
			})

			for j := 0; j < numOperations; j++ {
				if j%2 == 0 {
					suite.registry.Register(cacheName, cache)
				} else {
					suite.registry.Get(cacheName)
				}
			}
		}(i)
	}

	wg.Wait()

	// Verify that all caches are present
	for i := 0; i < numGoroutines; i++ {
		cacheName := fmt.Sprintf("cache%d", i)
		_, exists := suite.registry.Get(cacheName)
		assert.True(suite.T(), exists, "Cache %s should exist", cacheName)
	}
}

func TestLocalCacheRegistrySuite(t *testing.T) {
	suite.Run(t, new(LocalCacheRegistryTestSuite))
}

// Mock LocalCacheRegistry
type MockLocalCacheRegistry struct {
	mock.Mock
}

func (m *MockLocalCacheRegistry) Get(cacheName string) (*ristretto.Cache[string, any], bool) {
	args := m.Called(cacheName)
	return args.Get(0).(*ristretto.Cache[string, any]), args.Bool(1)
}

func (m *MockLocalCacheRegistry) Register(cacheName string, cache *ristretto.Cache[string, any]) {
	m.Called(cacheName, cache)
}

type LocalCacheFactoryTestSuite struct {
	suite.Suite
	factory      *LocalCacheFactory
	mockRegistry *MockLocalCacheRegistry
}

func (suite *LocalCacheFactoryTestSuite) SetupTest() {
	suite.mockRegistry = new(MockLocalCacheRegistry)
	suite.factory = &LocalCacheFactory{
		localCacheRegistry: suite.mockRegistry,
	}
}

func (suite *LocalCacheFactoryTestSuite) TestNewLocalCacheFactory() {
	localCacheFactory := NewLocalCacheFactory()
	assert.NotNil(suite.T(), localCacheFactory)
}

func (suite *LocalCacheFactoryTestSuite) TestCreateLocalCache_ReturnNil_ConfigError() {
	invalidConfig := LocalCacheBuilderConfig{
		NumCounters:              0,
		MaxCost:                  0,
		BufferItems:              0, // Decreased from default 64 to process items more frequently
		LocalCacheBuilderVersion: "v1",
		EnableRecordStats:        true,
	}
	cache := suite.factory.createLocalCache("cacheName", invalidConfig)
	assert.Nil(suite.T(), cache)
}

func (suite *LocalCacheFactoryTestSuite) TestGetOrCreateCache() {
	cacheName := "testCache"

	// Test cache miss, then create
	suite.mockRegistry.On("Get", cacheName).Return((*ristretto.Cache[string, any])(nil), false).Twice()
	suite.mockRegistry.On("Register", cacheName, mock.AnythingOfType("*ristretto.Cache[string,interface {}]")).Return().Once()

	cache := suite.factory.GetOrCreateCache(cacheName)
	assert.NotNil(suite.T(), cache)

	// Test cache miss first time, then hit the cache
	suite.mockRegistry.On("Get", cacheName).Return((*ristretto.Cache[string, any])(nil), false).Once()
	suite.mockRegistry.On("Get", cacheName).Return(cache, true).Once()

	returnedCache := suite.factory.GetOrCreateCache(cacheName)
	assert.Equal(suite.T(), cache, returnedCache)

	// Test cache hit
	suite.mockRegistry.On("Get", cacheName).Return(cache, true).Once()

	cachedResult := suite.factory.GetOrCreateCache(cacheName)
	assert.Equal(suite.T(), cache, cachedResult)

	suite.mockRegistry.AssertExpectations(suite.T())
}

func (suite *LocalCacheFactoryTestSuite) TestGetFromLocalCache() {
	cacheName := "testCache"
	key := "testKey"
	value := "testValue"

	cache, _ := ristretto.NewCache[string, any](&ristretto.Config[string, any]{
		NumCounters: 5000,
		MaxCost:     100 << 20,
		BufferItems: 32,
	})
	cache.Set(key, value, 1)
	cache.Wait()

	suite.mockRegistry.On("Get", cacheName).Return(cache, true).Once()

	result, found := suite.factory.GetFromLocalCache(cacheName, key)
	assert.True(suite.T(), found)
	assert.Equal(suite.T(), value, result)

	suite.mockRegistry.AssertExpectations(suite.T())
}

func (suite *LocalCacheFactoryTestSuite) TestPutToLocalCache() {
	cacheName := "testCache"
	key := "testKey"
	key2 := "testKey2"
	value := "testValue"
	value2 := "testValue2"

	cache, _ := ristretto.NewCache[string, any](&ristretto.Config[string, any]{
		NumCounters: 5000,
		MaxCost:     100 << 20,
		BufferItems: 32,
	})

	suite.mockRegistry.On("Get", cacheName).Return(cache, true)

	success := suite.factory.PutToLocalCache(cacheName, key, value)
	assert.True(suite.T(), success)

	success2 := suite.factory.PutToLocalCache(cacheName, key2, value2)
	assert.True(suite.T(), success2)

	// Verify the value was set
	result, found := suite.factory.GetFromLocalCache(cacheName, key)
	assert.True(suite.T(), found)
	assert.Equal(suite.T(), value, result)

	result2, found2 := suite.factory.GetFromLocalCache(cacheName, key2)
	assert.True(suite.T(), found2)
	assert.Equal(suite.T(), value2, result2)

	suite.mockRegistry.AssertExpectations(suite.T())
}

func (suite *LocalCacheFactoryTestSuite) TestPutToLocalCacheWithTTL() {
	cacheName := "testCache"
	key := "testKey"
	key2 := "testKey2"
	value := "testValue"
	value2 := "testValue2"

	cache, _ := ristretto.NewCache[string, any](&ristretto.Config[string, any]{
		NumCounters: 5000,
		MaxCost:     100 << 20,
		BufferItems: 32,
	})

	suite.mockRegistry.On("Get", cacheName).Return(cache, true)

	success := suite.factory.PutToLocalCacheWithTTL(cacheName, key, value, 0)
	assert.True(suite.T(), success)

	success2 := suite.factory.PutToLocalCacheWithTTL(cacheName, key2, value2, 1)
	assert.True(suite.T(), success2)

	// Verify the value was set
	result, found := suite.factory.GetFromLocalCache(cacheName, key)
	assert.True(suite.T(), found)
	assert.Equal(suite.T(), value, result)

	result2, found2 := suite.factory.GetFromLocalCache(cacheName, key2)
	assert.True(suite.T(), found2)
	assert.Equal(suite.T(), value2, result2)

	// Wait for second key to expire
	time.Sleep(1 * time.Second)

	// Verify key1 is still present, key2 is not
	result3, found3 := suite.factory.GetFromLocalCache(cacheName, key)
	assert.True(suite.T(), found3)
	assert.Equal(suite.T(), value, result3)

	_, found4 := suite.factory.GetFromLocalCache(cacheName, key2)
	assert.False(suite.T(), found4)

	suite.mockRegistry.AssertExpectations(suite.T())
}

func (suite *LocalCacheFactoryTestSuite) TestClearLocalCache() {
	cacheName := "testCache"
	key := "testKey"
	key2 := "testKey2"
	value := "testValue"

	cache, _ := ristretto.NewCache[string, any](&ristretto.Config[string, any]{
		NumCounters: 5000,
		MaxCost:     100 << 20,
		BufferItems: 32,
	})

	suite.mockRegistry.On("Get", cacheName).Return(cache, true)

	success := suite.factory.PutToLocalCache(cacheName, key, value)
	assert.True(suite.T(), success)

	success2 := suite.factory.PutToLocalCache(cacheName, key2, value)
	assert.True(suite.T(), success2)

	// Verify the values were set
	result, found := cache.Get(key)
	assert.True(suite.T(), found)
	assert.Equal(suite.T(), value, result)

	result2, found2 := cache.Get(key2)
	assert.True(suite.T(), found2)
	assert.Equal(suite.T(), value, result2)

	suite.factory.ClearLocalCache(cacheName)

	_, found = cache.Get(key)
	assert.False(suite.T(), found)

	_, found2 = cache.Get(key2)
	assert.False(suite.T(), found2)

	suite.mockRegistry.AssertExpectations(suite.T())
}

func (suite *LocalCacheFactoryTestSuite) TestShouldRefreshLocal() {
	// Create a temporary file
	tmpfile, err := os.CreateTemp("", "example")
	assert.NoError(suite.T(), err)
	defer os.Remove(tmpfile.Name())

	fileIdentifierCacheKey := "testFile"

	// Test when file is not in cache
	cache, _ := ristretto.NewCache[string, any](&ristretto.Config[string, any]{
		NumCounters: 1000,
		MaxCost:     100,
		BufferItems: 64,
	})
	suite.mockRegistry.On("Get", CacheNameFileIdentifier).Return(cache, true)

	shouldRefresh := suite.factory.ShouldRefreshLocal(fileIdentifierCacheKey, tmpfile)
	assert.True(suite.T(), shouldRefresh)

	// Verify the value was set
	fileInfo, _ := tmpfile.Stat()
	cacheValue := fileInfo.ModTime().String()

	result, found := suite.factory.GetFromLocalCache(CacheNameFileIdentifier, fileIdentifierCacheKey)
	assert.True(suite.T(), found)
	assert.Equal(suite.T(), cacheValue, result)

	// Test when file is in cache but modified
	time.Sleep(time.Second) // Ensure file modification time changes
	err = os.WriteFile(tmpfile.Name(), []byte("hello world"), 0644)
	assert.NoError(suite.T(), err)

	shouldRefresh = suite.factory.ShouldRefreshLocal(fileIdentifierCacheKey, tmpfile)
	assert.True(suite.T(), shouldRefresh)

	// Verify the value was set with new value
	fileInfo, _ = tmpfile.Stat()
	cacheValue = fileInfo.ModTime().String()

	result, found = suite.factory.GetFromLocalCache(CacheNameFileIdentifier, fileIdentifierCacheKey)
	assert.True(suite.T(), found)
	assert.Equal(suite.T(), cacheValue, result)

	// Test when file is in cache and not modified
	shouldRefresh = suite.factory.ShouldRefreshLocal(fileIdentifierCacheKey, tmpfile)
	assert.False(suite.T(), shouldRefresh)

	// Verify the value is still set with the same value
	result, found = suite.factory.GetFromLocalCache(CacheNameFileIdentifier, fileIdentifierCacheKey)
	assert.True(suite.T(), found)
	assert.Equal(suite.T(), cacheValue, result)

	// Test when file is not found
	tmpfile.Close()
	shouldRefresh = suite.factory.ShouldRefreshLocal(fileIdentifierCacheKey, tmpfile)
	assert.False(suite.T(), shouldRefresh)

	suite.mockRegistry.AssertExpectations(suite.T())
}

func (suite *LocalCacheFactoryTestSuite) TestShouldRefresh() {
	fileIdentifierCacheKey := "testFile"
	fileEtag := "etag1"
	fileEtag2 := "etag2"

	// Test when file is not in cache
	cache, _ := ristretto.NewCache[string, any](&ristretto.Config[string, any]{
		NumCounters: 5000,
		MaxCost:     100 << 20,
		BufferItems: 32,
	})
	suite.mockRegistry.On("Get", CacheNameFileIdentifier).Return(cache, true)

	shouldRefresh := suite.factory.ShouldRefresh(fileIdentifierCacheKey, fileEtag)
	assert.True(suite.T(), shouldRefresh)

	// Verify the value was set
	result, found := suite.factory.GetFromLocalCache(CacheNameFileIdentifier, fileIdentifierCacheKey)
	assert.True(suite.T(), found)
	assert.Equal(suite.T(), fileEtag, result)

	// Test when file is in cache but etag is modified
	shouldRefresh = suite.factory.ShouldRefresh(fileIdentifierCacheKey, fileEtag2)
	assert.True(suite.T(), shouldRefresh)

	// Verify the value was set with new value
	result, found = suite.factory.GetFromLocalCache(CacheNameFileIdentifier, fileIdentifierCacheKey)
	assert.True(suite.T(), found)
	assert.Equal(suite.T(), fileEtag2, result)

	// Test when file is in cache and etag is not modified
	shouldRefresh = suite.factory.ShouldRefresh(fileIdentifierCacheKey, fileEtag2)
	assert.False(suite.T(), shouldRefresh)

	// Verify the value is still set with the same value
	result, found = suite.factory.GetFromLocalCache(CacheNameFileIdentifier, fileIdentifierCacheKey)
	assert.True(suite.T(), found)
	assert.Equal(suite.T(), fileEtag2, result)

	suite.mockRegistry.AssertExpectations(suite.T())
}

// Model result file identifiers (bare or per-model key) expire with the model data's default TTL.
// All other file identifiers (configurations, bloom filters) never expire.
func (suite *LocalCacheFactoryTestSuite) TestShouldRefresh_FileIdentifierTTLByKey() {
	cache, _ := ristretto.NewCache[string, any](&ristretto.Config[string, any]{
		NumCounters: 5000,
		MaxCost:     100 << 20,
		BufferItems: 32,
	})
	suite.mockRegistry.On("Get", CacheNameFileIdentifier).Return(cache, true)

	for _, key := range []string{ModelResultFileIdentifierCacheKey("model_a"), CacheKeyModelResultFileIdentifier} {
		assert.True(suite.T(), suite.factory.ShouldRefresh(key, "etag"), key)
		ttl, found := cache.GetTTL(key)
		assert.True(suite.T(), found, key)
		assert.Greater(suite.T(), ttl, defaultTTL-time.Minute, key)
		assert.LessOrEqual(suite.T(), ttl, defaultTTL, key)
	}

	for _, key := range []string{CacheKeyModelConfigurationFileIdentifier, CacheKeyExperimentConfigurationFileIdentifier, "BloomFilterFileIdentifier_model_a"} {
		assert.True(suite.T(), suite.factory.ShouldRefresh(key, "etag"), key)
		ttl, found := cache.GetTTL(key)
		assert.True(suite.T(), found, key)
		assert.Equal(suite.T(), time.Duration(0), ttl, key) // 0 with found=true means no expiration
	}

	suite.mockRegistry.AssertExpectations(suite.T())
}

func (suite *LocalCacheFactoryTestSuite) TestModelResultFileIdentifierCacheKey() {
	assert.Equal(suite.T(), "ModelResultFileIdentifier_model_a", ModelResultFileIdentifierCacheKey("model_a"))
	assert.NotEqual(suite.T(), ModelResultFileIdentifierCacheKey("model_a"), ModelResultFileIdentifierCacheKey("model_b"))
}

func TestLocalCacheFactorySuite(t *testing.T) {
	suite.Run(t, new(LocalCacheFactoryTestSuite))
}

// ResetFileIdentifier makes the next ShouldRefresh report a change even when the identifier is unchanged.
func (suite *LocalCacheFactoryTestSuite) TestResetFileIdentifier_NextShouldRefreshReportsChange() {
	cache, _ := ristretto.NewCache[string, any](&ristretto.Config[string, any]{
		NumCounters: 5000,
		MaxCost:     100 << 20,
		BufferItems: 32,
	})
	suite.mockRegistry.On("Get", CacheNameFileIdentifier).Return(cache, true)
	key := ModelResultFileIdentifierCacheKey("model_a")

	assert.True(suite.T(), suite.factory.ShouldRefresh(key, "etag"), "first sight")
	assert.False(suite.T(), suite.factory.ShouldRefresh(key, "etag"), "unchanged")

	ResetFileIdentifier(suite.factory, key)

	assert.True(suite.T(), suite.factory.ShouldRefresh(key, "etag"), "unchanged identifier after reset")
	suite.mockRegistry.AssertExpectations(suite.T())
}
