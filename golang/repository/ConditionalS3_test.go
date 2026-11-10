// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package repository

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/stretchr/testify/require"
)

func TestConditionalS3GetOverHTTP(t *testing.T) {
	requests := make(chan string, 4)
	status := http.StatusOK
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests <- r.Header.Get("If-None-Match")
		if status != http.StatusOK {
			w.WriteHeader(status)
			return
		}
		if r.Header.Get("If-None-Match") == `"etag-1"` {
			w.WriteHeader(http.StatusNotModified)
			return
		}
		w.Header().Set("ETag", `"etag-1"`)
		_, _ = io.WriteString(w, "bloom bytes")
	}))
	defer server.Close()
	factory := &DaoFactory{s3Client: s3.NewFromConfig(aws.Config{Region: "us-east-1", Credentials: aws.AnonymousCredentials{}}, func(o *s3.Options) { o.BaseEndpoint = aws.String(server.URL); o.UsePathStyle = true })}
	output, unchanged, err := factory.GetS3ObjectIfChanged(context.Background(), "bucket", "model.bloom", "")
	require.NoError(t, err)
	require.False(t, unchanged)
	body, err := io.ReadAll(output.Body)
	require.NoError(t, err)
	require.NoError(t, output.Body.Close())
	require.Equal(t, "bloom bytes", string(body))
	require.Equal(t, "", <-requests)
	output, unchanged, err = factory.GetS3ObjectIfChanged(context.Background(), "bucket", "model.bloom", `"etag-1"`)
	require.NoError(t, err)
	require.True(t, unchanged)
	require.Nil(t, output)
	require.Equal(t, `"etag-1"`, <-requests)
	status = http.StatusForbidden
	_, unchanged, err = factory.GetS3ObjectIfChanged(context.Background(), "bucket", "model.bloom", `"etag-1"`)
	require.Error(t, err)
	require.False(t, unchanged)
	<-requests
}
