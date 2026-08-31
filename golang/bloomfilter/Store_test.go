// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package bloomfilter

import (
	"sync"
	"testing"

	bloom "github.com/OldPanda/bloomfilter"
	"github.com/stretchr/testify/suite"
)

func TestBloomFilterStoreSuite(t *testing.T) {
	suite.Run(t, new(BloomFilterStoreSuite))
}

type BloomFilterStoreSuite struct {
	suite.Suite
	store *BloomFilterStore
}

func (s *BloomFilterStoreSuite) SetupTest() {
	s.store = NewBloomFilterStore()
}

func (s *BloomFilterStoreSuite) createTestFilter(keys ...string) *bloom.BloomFilter {
	filter, err := bloom.NewBloomFilter(100, 0.01)
	s.Require().NoError(err)
	for _, key := range keys {
		filter.Put(key)
	}
	return filter
}

// --- Put then Get returns the stored filter ---

func (s *BloomFilterStoreSuite) TestPutThenGet_ReturnsStoredFilter() {
	filter := s.createTestFilter("key1", "key2")

	s.store.Put("model_a", filter)

	result, ok := s.store.Get("model_a")
	s.True(ok)
	s.Equal(filter, result)
	// Verify the filter still works correctly
	s.True(result.MightContain("key1"))
	s.True(result.MightContain("key2"))
}

func (s *BloomFilterStoreSuite) TestPutThenGet_MultipleIdentifiers() {
	filterA := s.createTestFilter("alpha")
	filterB := s.createTestFilter("beta")

	s.store.Put("model_a", filterA)
	s.store.Put("model_b", filterB)

	resultA, okA := s.store.Get("model_a")
	resultB, okB := s.store.Get("model_b")

	s.True(okA)
	s.True(okB)
	s.Equal(filterA, resultA)
	s.Equal(filterB, resultB)
}

// --- Get on missing identifier returns nil and false ---

func (s *BloomFilterStoreSuite) TestGet_MissingIdentifier_ReturnsNilAndFalse() {
	result, ok := s.store.Get("nonexistent_model")

	s.False(ok)
	s.Nil(result)
}

func (s *BloomFilterStoreSuite) TestGet_EmptyStore_ReturnsNilAndFalse() {
	result, ok := s.store.Get("")

	s.False(ok)
	s.Nil(result)
}

// --- Put replaces existing filter (latest wins) ---

func (s *BloomFilterStoreSuite) TestPut_ReplacesExistingFilter_LatestWins() {
	firstFilter := s.createTestFilter("original_key")
	secondFilter := s.createTestFilter("replacement_key")

	s.store.Put("model_a", firstFilter)
	s.store.Put("model_a", secondFilter)

	result, ok := s.store.Get("model_a")
	s.True(ok)
	s.Equal(secondFilter, result)
	// Verify the second filter's content is accessible
	s.True(result.MightContain("replacement_key"))
	// The first filter's key should not be in the second filter
	s.False(result.MightContain("original_key"))
}

// --- Delete removes the filter so subsequent get returns false ---

func (s *BloomFilterStoreSuite) TestDelete_RemovesFilter_SubsequentGetReturnsFalse() {
	filter := s.createTestFilter("key1")

	s.store.Put("model_a", filter)
	s.store.Delete("model_a")

	result, ok := s.store.Get("model_a")
	s.False(ok)
	s.Nil(result)
}

func (s *BloomFilterStoreSuite) TestDelete_NonexistentIdentifier_NoError() {
	// Deleting a non-existent key should not panic or error
	s.store.Delete("nonexistent_model")

	result, ok := s.store.Get("nonexistent_model")
	s.False(ok)
	s.Nil(result)
}

func (s *BloomFilterStoreSuite) TestDelete_OnlyRemovesSpecifiedIdentifier() {
	filterA := s.createTestFilter("alpha")
	filterB := s.createTestFilter("beta")

	s.store.Put("model_a", filterA)
	s.store.Put("model_b", filterB)

	s.store.Delete("model_a")

	_, okA := s.store.Get("model_a")
	resultB, okB := s.store.Get("model_b")

	s.False(okA)
	s.True(okB)
	s.Equal(filterB, resultB)
}

// --- Concurrent safety tests (validated by running with -race flag) ---

func (s *BloomFilterStoreSuite) TestConcurrentPutAndGet_NoDataRace() {
	var wg sync.WaitGroup
	const goroutines = 50

	// Concurrent writers
	for i := 0; i < goroutines; i++ {
		wg.Add(1)
		go func(id int) {
			defer wg.Done()
			filter := s.createTestFilter("concurrent_key")
			s.store.Put("shared_model", filter)
		}(i)
	}

	// Concurrent readers
	for i := 0; i < goroutines; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			s.store.Get("shared_model")
		}()
	}

	wg.Wait()

	// After all goroutines complete, the store should have a valid entry
	result, ok := s.store.Get("shared_model")
	s.True(ok)
	s.NotNil(result)
}

func (s *BloomFilterStoreSuite) TestConcurrentPutDeleteAndGet_NoDataRace() {
	var wg sync.WaitGroup
	const goroutines = 50

	// Pre-populate
	filter := s.createTestFilter("initial_key")
	s.store.Put("contested_model", filter)

	// Mix of put, get, and delete operations
	for i := 0; i < goroutines; i++ {
		wg.Add(3)
		go func() {
			defer wg.Done()
			f := s.createTestFilter("new_key")
			s.store.Put("contested_model", f)
		}()
		go func() {
			defer wg.Done()
			s.store.Get("contested_model")
		}()
		go func() {
			defer wg.Done()
			s.store.Delete("contested_model")
		}()
	}

	wg.Wait()
	// No assertion on final state since it's indeterminate — the race detector validates correctness
}
