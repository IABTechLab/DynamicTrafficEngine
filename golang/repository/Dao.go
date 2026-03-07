// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package repository

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/aws/smithy-go"
)

const S3Prefix = "s3://"

// Truncated S3 API for fetching S3 objects.
type S3ClientAPI interface {
	GetObject(ctx context.Context, params *s3.GetObjectInput, optFns ...func(*s3.Options)) (*s3.GetObjectOutput, error)
}

// Implements DaoFactoryInterface.
type DaoFactory struct {
	s3Client S3ClientAPI
}

func NewDaoFactory(region string, credentialProvider aws.CredentialsProvider) *DaoFactory {
	// Create a new AWS config
	if credentialProvider == nil {
		Logger.Error().Msgf("Credential provider is nil")
		return nil
	}
	cfg, err := config.LoadDefaultConfig(context.TODO(), config.WithRegion(region), config.WithCredentialsProvider(credentialProvider), config.WithS3UseARNRegion(true))
	if err != nil {
		Logger.Error().Msgf("Error loading credentials config: %v", err)
		return nil
	}
	return &DaoFactory{
		s3Client: s3.NewFromConfig(cfg, func(o *s3.Options) {
			o.UseARNRegion = true
		}),
	}
}

// GetDataFromLocal reads local file data
func (t *DaoFactory) GetDataFromLocal(filePointer *os.File) ([]byte, error) {
	fileContent, err := io.ReadAll(filePointer)
	if err != nil {
		Logger.Error().Msgf("Error reading file %v", err)
		return nil, err
	}
	return fileContent, nil
}

// ReadContent reads data from S3 object output
func (t *DaoFactory) ReadContent(data io.ReadCloser) ([]byte, error) {
	defer func(Body io.ReadCloser) {
		err := Body.Close()
		if err != nil {
			Logger.Error().Msgf("Failed to close reader: %v", err)
		}
	}(data)

	return io.ReadAll(data)
}

// GetS3Object retrieves data from an S3 bucket
func (t *DaoFactory) GetS3Object(ctx context.Context, bucket string, key string) (*s3.GetObjectOutput, error) {
	Logger.Info().Msgf("bucket [%s], key [%s]", bucket, key)
	input := &s3.GetObjectInput{
		Bucket: aws.String(bucket),
		Key:    aws.String(key),
	}
	result, err := t.s3Client.GetObject(ctx, input)
	if err != nil {
		handleS3Error(err)
		return nil, fmt.Errorf("failed to get object from S3: %w", err)

	}
	return result, nil
}

func handleS3Error(err error) error {
	var ae smithy.APIError
	if errors.As(err, &ae) {
		switch ae.ErrorCode() {
		case "PermanentRedirect":
			Logger.Error().Msgf("Permanent Redirect Error: %v", ae.Error())
			// Handle redirect - the correct endpoint should be in the error message
			if strings.Contains(ae.Error(), "endpoint") {
				// Extract and use the correct endpoint
				Logger.Error().Msgf("Please use the correct endpoint specified in the error message")
			}
		case "NoSuchBucket":
			Logger.Error().Msgf("Bucket does not exist: %v", ae.Error())
		default:
			Logger.Error().Msgf("AWS API Error: %v", ae.Error())
		}
	}
	return err
}
