// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package interfaces

import (
	"context"
	"io"
	"os"

	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/dgraph-io/ristretto/v2"
)

// An interface for a local cache to store model and experiment data. Uses Ristretto.
type LocalCacheRegistryInterface interface {
	// Retrieve a local cache associated to the cacheName.
	Get(cacheName string) (*ristretto.Cache[string, any], bool)

	// Assign a local cache to the cacheName.
	Register(cacheName string, cache *ristretto.Cache[string, any])
}

// And interface that defines the contract for creating and managing local caches.
// It provides methods for retrieving, storing, and managing data in local caches.
type LocalCacheFactoryInterface interface {
	// Retrieve the value associated to key in the local cache associated to cacheName.
	GetFromLocalCache(cacheName string, key string) (any, bool)

	// Add the value associated to key in the local cache asociated to cacheName with the default TTL.
	//
	// Waits for the operation to complete to avoid race conditions.
	PutToLocalCache(cacheName string, key string, value any) bool

	// Add the value associated to key in the local cache asociated to cacheName with a provided TTL in seconds.
	//
	// Waits for the operation to complete to avoid race conditions.
	PutToLocalCacheWithTTL(cacheName string, key string, value any, ttlSeconds int64) bool

	// Invalidates all entries in the local cache associated to cacheName.
	//
	// wait for the operation to complete to avoid race conditions.
	ClearLocalCache(cacheName string)

	// Determines if the cache version of contents of an S3 object is different than the
	// fetched contents of the current S3 object. Returns true if the eTags of the two objects are not equal.
	ShouldRefresh(fileIdentifierCacheKey string, fileIdentifier string) bool

	// Determines if the cache version of contents of a local file is different than the
	// fetched contents of the current local file. Returns true if the hashes of the two files are not equal.
	ShouldRefreshLocal(fileIdentifierCacheKey string, filePointer *os.File) bool

	// Returns the local cache associated to cacheName if present. If not present, creates and registers a
	// new local cache associated to cacheName.
	GetOrCreateCache(cacheName string) *ristretto.Cache[string, any]
}

// An interface that fetches loal files and S3 objects.
type DaoFactoryInterface interface {
	// Fetch the contents of a local file.
	GetDataFromLocal(filePointer *os.File) ([]byte, error)

	// Read the contents of an S3 object.
	ReadContent(data io.ReadCloser) ([]byte, error)

	// Fetch the metadata of an S3 object. This does not fetch the contents of the object.
	GetS3Object(ctx context.Context, bucket string, key string) (*s3.GetObjectOutput, error)
}
