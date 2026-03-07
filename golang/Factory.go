// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package demanddriventrafficevaluator

import (
	"sync"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/rs/zerolog"
	"golang.a2z.com/demanddriventrafficevaluator/evaluation"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/modelfeature"
	"golang.a2z.com/demanddriventrafficevaluator/repository"
	"golang.a2z.com/demanddriventrafficevaluator/task"
	"golang.a2z.com/demanddriventrafficevaluator/util"
)

var (
	modelConfigurationHandler      interfaces.ModelConfigurationHandlerInterface
	experimentConfigurationHandler interfaces.ExperimentConfigurationHandlerInterface
	modelResultHandler             interfaces.ModelResultHandlerInterface
	trafficAllocator               interfaces.TrafficAllocatorInterface
	daoFactory                     interfaces.DaoFactoryInterface
	localCacheFactory              interfaces.LocalCacheFactoryInterface
	timeProvider                   interfaces.TimeProvider
	modelConfigOnce                sync.Once
	experimentConfigOnce           sync.Once
	modelResultOnce                sync.Once
	trafficAllocatorOnce           sync.Once
	daoFactoryOnce                 sync.Once
	localCacheFactoryOnce          sync.Once
	timeProviderOnce               sync.Once
)

var Logger zerolog.Logger

func init() {
	Logger = util.GetLogger()
	util.WithComponent("demanddriventrafficevaluator")
}

func NewTaskInitializer(supplierName string, credentialProvider aws.CredentialsProvider, region string, folderPrefix string, schedulePeriod int) *task.Initializer {
	modelConfigurationPeriodicLoadingTask := task.NewModelConfigurationPeriodicLoadingTask(supplierName, folderPrefix, getModelConfigurationHandler(supplierName, folderPrefix, region, credentialProvider), schedulePeriod)
	trafficAllocator = getTrafficAllocator()
	experimentConfigurationPeriodicLoadingTask := task.NewExperimentConfigurationPeriodicLoadingTask(supplierName, folderPrefix, getExperimentConfigurationHandler(supplierName, folderPrefix, region, credentialProvider, trafficAllocator), schedulePeriod)
	stageOneTasks := []task.InitializerTask{
		{
			Name:                    "ModelConfigurationPeriodicLoadingTask",
			Task:                    modelConfigurationPeriodicLoadingTask,
			MaximumAttempts:         5,
			MinDelayBeforeAttemptMs: int64(1000),
			MaxDelayBeforeAttemptMs: int64(10000),
		},
		{
			Name:                    "ExperimentConfigurationPeriodicLoadingTask",
			Task:                    experimentConfigurationPeriodicLoadingTask,
			MaximumAttempts:         5,
			MinDelayBeforeAttemptMs: int64(1000),
			MaxDelayBeforeAttemptMs: int64(10000),
		},
	}
	modelResultPeriodicLoadingTask := task.NewModelResultPeriodicLoadingTask(supplierName, folderPrefix, getModelResultHandler(supplierName, folderPrefix, region, credentialProvider, modelConfigurationHandler), schedulePeriod)
	stageTwoTasks := []task.InitializerTask{
		{
			Name:                    "ModelResultPeriodicLoadingTask",
			Task:                    modelResultPeriodicLoadingTask,
			MaximumAttempts:         5,
			MinDelayBeforeAttemptMs: int64(1000),
			MaxDelayBeforeAttemptMs: int64(10000),
		},
	}
	taskInitializer := task.NewInitializer(
		stageOneTasks,
		stageTwoTasks,
		int64(600000),
	)
	return taskInitializer
}

func NewRequestEvaluator(supplierName string, credentialProvider aws.CredentialsProvider, region string, folderPrefix string) *evaluation.RequestEvaluator {
	modelResultHandler = getModelResultHandler(supplierName, folderPrefix, region, credentialProvider, getModelConfigurationHandler(supplierName, folderPrefix, region, credentialProvider))
	trafficAllocator = getTrafficAllocator()
	ruleBasedModelEvaluator := evaluation.NewRuleBasedModelEvaluator(getModelResultHandler(supplierName, folderPrefix, region, credentialProvider, modelConfigurationHandler))
	requestEvaluator := evaluation.NewRequestEvaluator(supplierName, trafficAllocator, ruleBasedModelEvaluator, getModelConfigurationHandler(supplierName, folderPrefix, region, credentialProvider))
	return requestEvaluator
}

func getModelConfigurationHandler(supplierName string, folderPrefix string, region string, credentialProvider aws.CredentialsProvider) interfaces.ModelConfigurationHandlerInterface {
	Logger.Debug().Msgf("getModelConfigurationHandler")
	modelConfigOnce.Do(func() {
		configurationHandler := modelfeature.NewConfigurationHandler[interfaces.ModelConfiguration](folderPrefix, supplierName, getDaoFactory(region, credentialProvider), repository.CacheKeyModelConfigurationFileIdentifier, repository.CacheKeyModel, "model", getLocalCacheFactory())
		modelConfigurationHandler = modelfeature.NewModelConfigurationHandler(configurationHandler)
	})
	return modelConfigurationHandler
}

func getExperimentConfigurationHandler(supplierName string, folderPrefix string, region string, credentialProvider aws.CredentialsProvider, trafficAllocator interfaces.TrafficAllocatorInterface) interfaces.ExperimentConfigurationHandlerInterface {
	Logger.Debug().Msgf("getExperimentConfigurationHandler")
	experimentConfigOnce.Do(func() {
		Logger.Debug().Msgf("getExperimentConfigurationHandler Once")
		configurationHandler := modelfeature.NewConfigurationHandler[interfaces.ExperimentConfiguration](folderPrefix, supplierName, getDaoFactory(region, credentialProvider), repository.CacheKeyExperimentConfigurationFileIdentifier, repository.CacheKeyExperiment, "experiment", getLocalCacheFactory())
		experimentConfigurationHandler = modelfeature.NewExperimentConfigurationHandler(configurationHandler, trafficAllocator)
	})
	return experimentConfigurationHandler
}

func getModelResultHandler(supplierName string, folderPrefix string, region string, credentialProvider aws.CredentialsProvider, modelConfigurationHandler interfaces.ModelConfigurationHandlerInterface) interfaces.ModelResultHandlerInterface {
	Logger.Debug().Msgf("getModelResultHandler")
	modelResultOnce.Do(func() {
		modelResultHandler = modelfeature.NewModelResultHandler(supplierName, folderPrefix, getDaoFactory(region, credentialProvider), modelConfigurationHandler, getLocalCacheFactory(), getTimeProvider())
	})
	return modelResultHandler
}

func getTrafficAllocator() interfaces.TrafficAllocatorInterface {
	Logger.Debug().Msgf("getTrafficAllocator")
	trafficAllocatorOnce.Do(func() {
		trafficAllocator = modelfeature.NewTrafficAllocator()
	})
	return trafficAllocator
}

func getDaoFactory(region string, credentialProvider aws.CredentialsProvider) interfaces.DaoFactoryInterface {
	Logger.Debug().Msgf("getDaoFactory")
	daoFactoryOnce.Do(func() {
		daoFactory = repository.NewDaoFactory(region, credentialProvider)
		if daoFactory == nil {
			panic("Fail to init DaoFactory instance due to null or improper credentials provider")
			// consider not panicking since technically we can read from local
		}
	})
	return daoFactory
}

func getLocalCacheFactory() interfaces.LocalCacheFactoryInterface {
	Logger.Debug().Msgf("getLocalCacheFactory")
	localCacheFactoryOnce.Do(func() {
		localCacheFactory = repository.NewLocalCacheFactory()
	})
	return localCacheFactory
}

func getTimeProvider() interfaces.TimeProvider {
	Logger.Debug().Msgf("getTimeProvider")
	timeProviderOnce.Do(func() {
		timeProvider = util.NewRealTimeProvider()
	})
	return timeProvider
}
