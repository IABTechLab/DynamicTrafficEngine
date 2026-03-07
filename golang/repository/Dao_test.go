// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package repository

import (
	"bytes"
	"context"
	"errors"
	"io"
	"os"
	"testing"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
	"github.com/stretchr/testify/suite"
)

type DaoFactoryTestSuite struct {
	suite.Suite
	daoFactory *DaoFactory
}

func (suite *DaoFactoryTestSuite) SetupTest() {
	cfg, _ := config.LoadDefaultConfig(context.TODO())
	suite.daoFactory = &DaoFactory{
		s3Client: s3.NewFromConfig(cfg),
	}
}

func (suite *DaoFactoryTestSuite) TestNewDaoFactory() {
	// empty constructor
	factory := NewDaoFactory("us-east-1", nil)

	assert.Nil(suite.T(), factory)

	// with credentials provider
	credProvider := aws.CredentialsProvider(mockCredentialsProvider{})

	factory = NewDaoFactory("us-east-1", credProvider)

	assert.NotNil(suite.T(), factory)
	assert.NotNil(suite.T(), factory.s3Client)
}

func (suite *DaoFactoryTestSuite) TestGetDataFromLocal() {
	// Create a temporary file
	content := []byte("hello world")
	tmpfile, err := os.CreateTemp("", "example")
	assert.NoError(suite.T(), err)
	defer os.Remove(tmpfile.Name())

	_, err = tmpfile.Write(content)
	assert.NoError(suite.T(), err)
	err = tmpfile.Close()
	assert.NoError(suite.T(), err)

	// Reopen the file for reading
	file, err := os.Open(tmpfile.Name())
	assert.NoError(suite.T(), err)
	defer file.Close()

	data, err := suite.daoFactory.GetDataFromLocal(file)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), content, data)

	file.Close()
	data, err = suite.daoFactory.GetDataFromLocal(file)
	assert.Error(suite.T(), err)
	assert.Nil(suite.T(), data)
}

func (suite *DaoFactoryTestSuite) TestReadContent_Success() {
	testContent := []byte("test content")
	reader := io.NopCloser(bytes.NewReader(testContent))

	content, err := suite.daoFactory.ReadContent(reader)

	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), testContent, content)
}

func (suite *DaoFactoryTestSuite) TestReadContent_Error() {
	errReader := &ErrorReader{Err: errors.New("read error")}
	reader := io.NopCloser(errReader)

	content, err := suite.daoFactory.ReadContent(reader)

	assert.Error(suite.T(), err)
	assert.Equal(suite.T(), content, make([]byte, 0, 512))
}

func (suite *DaoFactoryTestSuite) TestGetS3Object_Success() {
	mockS3Client := new(MockS3Client)
	factory := &DaoFactory{s3Client: mockS3Client}

	expectedError := errors.New("S3 error")
	mockS3Client.On("GetObject", mock.Anything, mock.Anything).Return(nil, expectedError)

	result, err := factory.GetS3Object(context.Background(), "test-bucket", "test-key")

	assert.Error(suite.T(), err)
	assert.Nil(suite.T(), result)
	mockS3Client.AssertExpectations(suite.T())
}

func (suite *DaoFactoryTestSuite) TestGetS3Object_Error() {
	mockS3Client := new(MockS3Client)
	factory := &DaoFactory{s3Client: mockS3Client}

	expectedOutput := &s3.GetObjectOutput{}
	mockS3Client.On("GetObject", mock.Anything, mock.Anything).Return(expectedOutput, nil)

	result, err := factory.GetS3Object(context.Background(), "test-bucket", "test-key")

	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), expectedOutput, result)
	mockS3Client.AssertExpectations(suite.T())
}

func TestDaoFactorySuite(t *testing.T) {
	suite.Run(t, new(DaoFactoryTestSuite))
}

// Helper types for testing
type mockCredentialsProvider struct{}

func (m mockCredentialsProvider) Retrieve(context.Context) (aws.Credentials, error) {
	return aws.Credentials{}, nil
}

type ErrorReader struct {
	Err error
}

func (r *ErrorReader) Read(p []byte) (n int, err error) {
	return 0, r.Err
}

// MockS3Client is a mock of S3 client
type MockS3Client struct {
	mock.Mock
}

// Implement the GetObject method for MockS3Client
func (m *MockS3Client) GetObject(ctx context.Context, params *s3.GetObjectInput, optFns ...func(*s3.Options)) (*s3.GetObjectOutput, error) {
	args := m.Called(ctx, params)
	if args.Get(0) == nil {
		return nil, args.Error(1)
	}
	return args.Get(0).(*s3.GetObjectOutput), args.Error(1)
}
