// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package repository

import (
	"os"
	"sync"
	"time"

	"github.com/dgraph-io/ristretto/v2"
	"github.com/rs/zerolog"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/util"
)

var Logger zerolog.Logger

func init() {
	Logger = util.GetLogger()
	util.WithComponent("repository")
}

const CacheNameConfiguration = "Configuration"
const CacheNameFileIdentifier = "FileIdentifier"
const CacheKeyModel = "Model"
const CacheKeyModelConfigurationFileIdentifier = "ModelConfigurationFileIdentifier"
const CacheKeyModelResultFileIdentifier = "ModelResultFileIdentifier"
const CacheKeyExperiment = "Experiment"
const CacheKeyExperimentConfigurationFileIdentifier = "ExperimentConfigurationFileIdentifier"

// Implements LocalCacheRegistryInterface.
type LocalCacheRegistry struct {
	rwMutex       sync.RWMutex
	localCacheMap map[string]*ristretto.Cache[string, any]
}

func (t *LocalCacheRegistry) Get(cacheName string) (*ristretto.Cache[string, any], bool) {
	t.rwMutex.RLock()
	defer t.rwMutex.RUnlock()
	val, ok := t.localCacheMap[cacheName]
	return val, ok
}

func (t *LocalCacheRegistry) Register(cacheName string, cache *ristretto.Cache[string, any]) {
	t.rwMutex.Lock()
	defer t.rwMutex.Unlock()
	t.localCacheMap[cacheName] = cache
}

var defaultTTL = 70 * time.Minute

// LocalCacheBuilderConfig is the config for the local cache builder
type LocalCacheBuilderConfig struct {
	CacheName                string
	NumCounters              int64 // 10x of the max number of entries
	MaxCost                  int64 // the max size of the cache in bytes
	BufferItems              int64 // buffer items for the cache
	LocalCacheBuilderVersion string
	EnableRecordStats        bool
}

// default LocalCacheBuilderConfig
var defaultLocalCacheBuilderConfig = LocalCacheBuilderConfig{
	NumCounters:              5000,
	MaxCost:                  100 << 20,
	BufferItems:              32, // Decreased from default 64 to process items more frequently
	LocalCacheBuilderVersion: "v1",
	EnableRecordStats:        true,
}

// Implements LocalCacheFactoryInterface.
type LocalCacheFactory struct {
	rwMutex            sync.RWMutex
	localCacheRegistry interfaces.LocalCacheRegistryInterface
}

func NewLocalCacheFactory() *LocalCacheFactory {
	return &LocalCacheFactory{
		localCacheRegistry: &LocalCacheRegistry{
			localCacheMap: make(map[string]*ristretto.Cache[string, any]),
		},
	}
}

func (t *LocalCacheFactory) createDefaultLocalCache(cacheName string) *ristretto.Cache[string, any] {
	return t.createLocalCache(cacheName, defaultLocalCacheBuilderConfig)
}

func (t *LocalCacheFactory) createLocalCache(cacheName string, config LocalCacheBuilderConfig) *ristretto.Cache[string, any] {
	config.CacheName = cacheName
	newCache, err := ristretto.NewCache(&ristretto.Config[string, any]{
		NumCounters: config.NumCounters,
		MaxCost:     config.MaxCost,
		BufferItems: 64,
		Metrics:     config.EnableRecordStats,
	})
	if err != nil {
		Logger.Error().Msgf("Failed to create cache: %v", err)
		return nil
	}
	newCache.Clear()
	return newCache
}

func (t *LocalCacheFactory) GetOrCreateCache(cacheName string) *ristretto.Cache[string, any] {
	// First check without locking
	cache, exist := t.localCacheRegistry.Get(cacheName)
	if exist {
		return cache
	}

	// If not found, lock and check again
	t.rwMutex.Lock()
	defer t.rwMutex.Unlock()

	// Double-check after acquiring the lock
	cache, exist = t.localCacheRegistry.Get(cacheName)
	if exist {
		return cache
	}

	// If still not found, create new cache
	Logger.Info().Msgf("Cache[%s] not found, creating a new one", cacheName)
	cache = t.createDefaultLocalCache(cacheName)
	//localCacheRegistry.localCacheMap[cacheName] = cache
	t.localCacheRegistry.Register(cacheName, cache)
	return cache
}

func (t *LocalCacheFactory) GetFromLocalCache(cacheName string, key string) (any, bool) {
	Logger.Debug().Msgf("GetFromLocalCache")
	cache := t.GetOrCreateCache(cacheName)
	return cache.Get(key)
}

func (t *LocalCacheFactory) PutToLocalCache(cacheName string, key string, value any) bool {
	Logger.Debug().Msgf("PutToLocalCache")
	cache := t.GetOrCreateCache(cacheName)
	ok := cache.SetWithTTL(key, value, 1, defaultTTL)
	cache.Wait() // make sure all buffered writes have been applied.
	Logger.Debug().Msgf("Cache set key[%s] - value [%s] with TTL: %v", key, value, defaultTTL)
	return ok
}

func (t *LocalCacheFactory) PutToLocalCacheWithTTL(cacheName string, key string, value any, ttlSeconds int64) bool {
	Logger.Debug().Msgf("PutToLocalCacheWithTTL")
	cache := t.GetOrCreateCache(cacheName)
	ok := cache.SetWithTTL(key, value, 1, time.Duration(ttlSeconds)*time.Second) // 0 TTL means key never expires
	cache.Wait()                                                                 // make sure all buffered writes have been applied.
	Logger.Debug().Msgf("Cache set key[%s] - value [%s] with TTL(sec): %v", key, value, ttlSeconds)
	return ok
}

func (t *LocalCacheFactory) ClearLocalCache(cacheName string) {
	Logger.Debug().Msgf("ClearLocalCache")
	cache := t.GetOrCreateCache(cacheName)
	cache.Clear()
	cache.Wait()
	Logger.Debug().Msgf("%s local cache has been cleared", cacheName)
}

func (t *LocalCacheFactory) ShouldRefresh(fileIdentifierCacheKey string, fileIdentifier string) bool {
	fileIdentifierInCache, found := t.GetFromLocalCache(CacheNameFileIdentifier, fileIdentifierCacheKey)
	stale := !found || fileIdentifierInCache != fileIdentifier
	if stale {
		if fileIdentifierCacheKey == CacheKeyModelResultFileIdentifier { // default TTL, since model rules will expire in default TTL
			t.PutToLocalCache(CacheNameFileIdentifier, fileIdentifierCacheKey, fileIdentifier)
		} else { // no TTL, since config files will not expire
			t.PutToLocalCacheWithTTL(CacheNameFileIdentifier, fileIdentifierCacheKey, fileIdentifier, 0)
		}
	}
	return stale
}

func (t *LocalCacheFactory) ShouldRefreshLocal(fileIdentifierCacheKey string, filePointer *os.File) bool {
	fileInfo, err := filePointer.Stat()
	if err != nil {
		Logger.Error().Msgf("error getting file info: %v", err)
		return false
	}
	fileIdentifier := fileInfo.ModTime().String()
	fileIdentifierInCache, found := t.GetFromLocalCache(CacheNameFileIdentifier, fileIdentifierCacheKey)
	stale := !found || fileIdentifierInCache != fileIdentifier
	if stale {
		if fileIdentifierCacheKey == CacheKeyModelResultFileIdentifier { // default TTL, since model rules will expire in default TTL
			t.PutToLocalCache(CacheNameFileIdentifier, fileIdentifierCacheKey, fileIdentifier)
		} else { // no TTL, since config files will not expire
			t.PutToLocalCacheWithTTL(CacheNameFileIdentifier, fileIdentifierCacheKey, fileIdentifier, 0)
		}
	}
	return stale
}
