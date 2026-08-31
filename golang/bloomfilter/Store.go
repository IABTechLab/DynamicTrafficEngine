// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package bloomfilter

import (
	"sync"

	bloom "github.com/OldPanda/bloomfilter"
)

// BloomFilterStore provides concurrent-safe storage of bloom filter instances
// keyed by model identifier. It uses sync.Map for lock-free reads on the hot
// evaluation path. Filters are atomically replaced on successful load — no TTL
// or eviction logic is needed.
type BloomFilterStore struct {
	store sync.Map // map[string]*bloom.BloomFilter
}

// NewBloomFilterStore creates a new empty BloomFilterStore.
func NewBloomFilterStore() *BloomFilterStore {
	return &BloomFilterStore{}
}

// Get retrieves the bloom filter for a model identifier.
// Returns (filter, true) if found, (nil, false) otherwise.
func (s *BloomFilterStore) Get(modelIdentifier string) (*bloom.BloomFilter, bool) {
	value, ok := s.store.Load(modelIdentifier)
	if !ok {
		return nil, false
	}
	return value.(*bloom.BloomFilter), true
}

// Put stores a bloom filter for a model identifier, atomically replacing any existing entry.
func (s *BloomFilterStore) Put(modelIdentifier string, filter *bloom.BloomFilter) {
	s.store.Store(modelIdentifier, filter)
}

// Delete removes the bloom filter for a model identifier.
func (s *BloomFilterStore) Delete(modelIdentifier string) {
	s.store.Delete(modelIdentifier)
}
