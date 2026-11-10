// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package bloomfilter

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"sync"
	"testing"

	bloom "github.com/OldPanda/bloomfilter"
	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/stretchr/testify/require"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/repository"
)

type conditionalBloomDAO struct {
	interfaces.DaoFactoryInterface
	data              []byte
	etag              *string
	calls             []string
	bodies, bytesRead int
	fetchErr, readErr error
	onUnchanged       func()
}

func (d *conditionalBloomDAO) GetS3Object(ctx context.Context, bucket, key string) (*s3.GetObjectOutput, error) {
	output, _, err := d.GetS3ObjectIfChanged(ctx, bucket, key, "")
	return output, err
}
func (d *conditionalBloomDAO) GetS3ObjectIfChanged(_ context.Context, bucket, key, etag string) (*s3.GetObjectOutput, bool, error) {
	d.calls = append(d.calls, bucket+"/"+key+"|"+etag)
	if d.fetchErr != nil {
		return nil, false, d.fetchErr
	}
	if etag != "" && d.etag != nil && etag == *d.etag {
		if d.onUnchanged != nil {
			d.onUnchanged()
		}
		return nil, true, nil
	}
	d.bodies++
	return &s3.GetObjectOutput{ETag: d.etag, Body: &countedBloomBody{reader: bytes.NewReader(d.data), dao: d}}, false, nil
}
func (d *conditionalBloomDAO) ReadContent(body io.ReadCloser) ([]byte, error) {
	if d.readErr != nil {
		return nil, d.readErr
	}
	data, err := io.ReadAll(body)

	return data, err
}
func bloomBytes(t *testing.T, key string) []byte {
	t.Helper()
	filter, err := bloom.NewBloomFilter(10, 0.01)
	require.NoError(t, err)
	filter.Put(key)
	return filter.ToBytes()
}
func TestConditionalBloomRefresh(t *testing.T) {
	dao := &conditionalBloomDAO{etag: aws.String(`"version-1"`), data: bloomBytes(t, "first")}
	store := NewBloomFilterStore()
	loader := NewBloomFilterLoader(store, dao, nil, nil, nil)
	model := interfaces.ModelDefinition{Identifier: "model", S3PathMode: interfaces.S3PathModeStatic}
	load := func(bucket string) error {
		return loader.loadSingleModel("ssp", repository.S3Prefix+bucket, "model", model)
	}
	require.NoError(t, load("bucket"))
	first, _ := store.Get("model")
	require.NoError(t, load("bucket"))
	current, _ := store.Get("model")
	require.Same(t, first, current)
	require.Equal(t, 1, dao.bodies, "304 must not download a body")
	require.Equal(t, len(dao.data), dao.bytesRead)
	require.Equal(t, `bucket/ssp/models/model.bloom|"version-1"`, dao.calls[1])

	dao.etag = aws.String(`"version-2"`)
	dao.data = bloomBytes(t, "second")
	require.NoError(t, load("bucket"))
	current, _ = store.Get("model")
	require.NotSame(t, first, current)
	require.True(t, current.MightContain("second"))

	// The same ETag in another bucket or key does not validate this filter.
	require.NoError(t, load("other-bucket"))
	require.Equal(t, "other-bucket/ssp/models/model.bloom|", dao.calls[len(dao.calls)-1])
	model.Identifier = "other-model"
	require.NoError(t, load("other-bucket"))
	require.Equal(t, "other-bucket/ssp/models/other-model.bloom|", dao.calls[len(dao.calls)-1])
	store.Delete("model")
	require.NoError(t, load("other-bucket"))
	require.Equal(t, "other-bucket/ssp/models/other-model.bloom|", dao.calls[len(dao.calls)-1])
	// Eviction during the conditional request also requires a full fetch.
	dao.onUnchanged = func() { store.Delete("model") }
	require.NoError(t, load("other-bucket"))
	_, present := store.Get("model")
	require.True(t, present)
}
func TestConditionalBloomFailureRetries(t *testing.T) {
	for _, failure := range []string{"fetch", "read", "decode"} {
		t.Run(failure, func(t *testing.T) {
			dao := &conditionalBloomDAO{etag: aws.String("v1"), data: bloomBytes(t, "first")}
			store := NewBloomFilterStore()
			loader := NewBloomFilterLoader(store, dao, nil, nil, nil)
			model := interfaces.ModelDefinition{Identifier: "model", S3PathMode: interfaces.S3PathModeStatic}
			load := func() error { return loader.loadSingleModel("ssp", "s3://bucket", "model", model) }
			require.NoError(t, load())
			first, _ := store.Get("model")
			dao.etag = aws.String("v2")
			switch failure {
			case "fetch":
				dao.fetchErr = fmt.Errorf("unavailable")
			case "read":
				dao.readErr = io.ErrUnexpectedEOF
			case "decode":
				dao.data = []byte("invalid")
			}
			require.Error(t, load())
			current, _ := store.Get("model")
			require.Same(t, first, current)
			dao.fetchErr = nil
			dao.readErr = nil
			dao.data = bloomBytes(t, "second")
			require.NoError(t, load())
			require.Equal(t, "bucket/ssp/models/model.bloom|v1", dao.calls[2], "failure must not commit v2")
			current, _ = store.Get("model")
			require.True(t, current.MightContain("second"))
		})
	}
}
func TestConditionalBloomWithoutETag(t *testing.T) {
	dao := &conditionalBloomDAO{data: bloomBytes(t, "key")}
	loader := NewBloomFilterLoader(NewBloomFilterStore(), dao, nil, nil, nil)
	model := interfaces.ModelDefinition{Identifier: "model", S3PathMode: interfaces.S3PathModeStatic}
	for range 2 {
		require.NoError(t, loader.loadSingleModel("ssp", "s3://bucket", "model", model))
	}
	require.Equal(t, 2, dao.bodies)
}

type countedBloomBody struct {
	reader *bytes.Reader
	dao    *conditionalBloomDAO
}

func (b *countedBloomBody) Read(p []byte) (int, error) {
	n, err := b.reader.Read(p)
	b.dao.bytesRead += n
	return n, err
}
func (b *countedBloomBody) Close() error { return nil }

// Expose only the original interface, as existing custom DAOs do.
type legacyBloomDAO struct{ interfaces.DaoFactoryInterface }

func TestLegacyBloomDAOClosesUnchangedBodyWithoutDraining(t *testing.T) {
	dao := &conditionalBloomDAO{etag: aws.String("v1"), data: bloomBytes(t, "key")}
	loader := NewBloomFilterLoader(NewBloomFilterStore(), &legacyBloomDAO{dao}, nil, nil, nil)
	model := interfaces.ModelDefinition{Identifier: "model", S3PathMode: interfaces.S3PathModeStatic}
	for range 2 {
		require.NoError(t, loader.loadSingleModel("ssp", "s3://bucket", "model", model))
	}
	require.Equal(t, 2, dao.bodies, "legacy DAO still issues unconditional GETs")
	require.Equal(t, len(dao.data), dao.bytesRead, "unchanged body must not be drained")
}
func TestConcurrentBloomLoads(t *testing.T) {
	dao := &conditionalBloomDAO{etag: aws.String("v1"), data: bloomBytes(t, "key")}
	loader := NewBloomFilterLoader(NewBloomFilterStore(), dao, nil, nil, nil)
	model := interfaces.ModelDefinition{Identifier: "model", S3PathMode: interfaces.S3PathModeStatic}
	var wg sync.WaitGroup
	errors := make(chan error, 10)
	for range 10 {
		wg.Add(1)
		go func() { defer wg.Done(); errors <- loader.loadSingleModel("ssp", "s3://bucket", "model", model) }()
	}
	wg.Wait()
	close(errors)
	for err := range errors {
		require.NoError(t, err)
	}
	require.Equal(t, 1, dao.bodies)
	require.Len(t, dao.calls, 10)
}
