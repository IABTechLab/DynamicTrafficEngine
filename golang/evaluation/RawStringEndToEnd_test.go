// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package evaluation

import (
	"testing"

	"github.com/stretchr/testify/suite"
	"golang.a2z.com/demanddriventrafficevaluator/interfaces"
	mockInterfaces "golang.a2z.com/demanddriventrafficevaluator/mocks/interfaces"
	"golang.a2z.com/demanddriventrafficevaluator/modelfeature"
)

// RawStringEndToEndSuite exercises the full evaluation pipeline starting from a raw JSON
// OpenRTB request string. Unlike the other "integration" suites, which hand-build the
// FeatureFieldValueMap, these tests feed the raw string through RequestEvaluator.Evaluate so
// the JSONPath parser (github.com/ohler55/ojg) is exercised end-to-end: raw string → parse →
// JSONPath feature extraction → transform → key building → cache lookup → aggregation →
// filter decision.
//
// Only the local cache is mocked (to control hit/miss); the RuleBasedModelEvaluator and
// ModelResultHandler are real.
type RawStringEndToEndSuite struct {
	suite.Suite
	mockLocalCacheFactory        *mockInterfaces.LocalCacheFactoryInterface
	mockDaoFactory               *mockInterfaces.DaoFactoryInterface
	mockTimeProvider             *mockInterfaces.TimeProvider
	mockModelConfigHandler       *mockInterfaces.ModelConfigurationHandlerInterface
	mockTrafficAllocator         *mockInterfaces.TrafficAllocatorInterface
	mockTrafficAllocationContext *mockInterfaces.TrafficAllocationContextInterface
	requestEvaluator             *RequestEvaluator
}

func TestRawStringEndToEndSuite(t *testing.T) {
	suite.Run(t, new(RawStringEndToEndSuite))
}

func (suite *RawStringEndToEndSuite) SetupTest() {
	suite.mockLocalCacheFactory = mockInterfaces.NewLocalCacheFactoryInterface(suite.T())
	suite.mockDaoFactory = mockInterfaces.NewDaoFactoryInterface(suite.T())
	suite.mockTimeProvider = mockInterfaces.NewTimeProvider(suite.T())
	suite.mockModelConfigHandler = mockInterfaces.NewModelConfigurationHandlerInterface(suite.T())
	suite.mockTrafficAllocator = mockInterfaces.NewTrafficAllocatorInterface(suite.T())
	suite.mockTrafficAllocationContext = mockInterfaces.NewTrafficAllocationContextInterface(suite.T())

	// Real model result handler (only the local cache is mocked) and real rule-based evaluator.
	modelResultHandler := modelfeature.NewModelResultHandler(
		"ssp",
		"./testdata",
		suite.mockDaoFactory,
		suite.mockModelConfigHandler,
		suite.mockLocalCacheFactory,
		suite.mockTimeProvider,
	)
	ruleBasedEvaluator := NewRuleBasedModelEvaluator(modelResultHandler)

	suite.requestEvaluator = NewRequestEvaluator(
		"ssp",
		suite.mockTrafficAllocator,
		ruleBasedEvaluator,
		suite.mockModelConfigHandler,
		NewConfigurableAggregator(),
	)
}

// expectPipelineWiring sets up the traffic-allocation and model-configuration mocks that the
// RequestEvaluator drives for a single-model, max-aggregation (no AggregationSchema) evaluation.
func (suite *RawStringEndToEndSuite) expectPipelineWiring(modelIdentifier string, uniqueFields []string, modelConfiguration interfaces.ModelConfiguration) {
	suite.mockTrafficAllocator.EXPECT().
		GetTrafficAllocationContext().
		Return(suite.mockTrafficAllocationContext).
		Once()
	suite.mockModelConfigHandler.EXPECT().
		GetAllUniqueFeatureFields().
		Return(uniqueFields, nil).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelIdentifiers().
		Return([]string{modelIdentifier}).
		Once()
	suite.mockModelConfigHandler.EXPECT().
		Provide().
		Return(&modelConfiguration, nil).
		Once()
	// Called twice: once in Evaluate() to determine the aggregation path (nil schema →
	// max-aggregation), once inside aggregateModelEvaluationResultsOnMax.
	suite.mockTrafficAllocationContext.EXPECT().
		GetExperimentDefinitionByType(modelfeature.ExperimentTypeSoftFilter).
		Return(&interfaces.ExperimentDefinition{
			Name:              ExperimentName,
			Type:              modelfeature.ExperimentTypeSoftFilter,
			AggregationSchema: nil,
		}, nil).
		Times(2)
	suite.mockTrafficAllocationContext.EXPECT().
		GetModelsByExperiment().
		Return(map[string][]string{ExperimentName: {modelIdentifier}}).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetTreatmentCodeInInt(ExperimentName).
		Return(TreatmentCodeInIntZero).
		Once()
	suite.mockTrafficAllocationContext.EXPECT().
		GetTreatmentCode(ExperimentName).
		Return(TreatmentT).
		Once()
}

// highValueDealModelConfiguration builds a HighValue deal model with a wildcard-extracted
// dealId feature and a scalar publisherId feature.
func highValueDealModelConfiguration(modelIdentifier string) (interfaces.ModelConfiguration, []string) {
	modelDefinition := interfaces.ModelDefinition{
		Identifier:           modelIdentifier,
		Dsp:                  "adsp",
		Name:                 "high-value-deals",
		Version:              "v1",
		Type:                 "HighValue",
		FeatureExtractorType: "JsonExtractor",
		Features: []interfaces.FeatureConfiguration{
			{
				Name:                "dealId",
				Fields:              []string{"$.imp[0].pmp.deals[*].id"},
				Transformations:     []interfaces.TransformerName{"IncludeDefaultValue"},
				MappingDefaultValue: "no_deal",
			},
			{
				Name:            "publisherId",
				Fields:          []string{"$.site.publisher.id"},
				Transformations: []interfaces.TransformerName{"GetFirstNotEmpty"},
			},
		},
	}
	config := interfaces.ModelConfiguration{
		ModelDefinitionByIdentifier: map[string]interfaces.ModelDefinition{
			modelIdentifier: modelDefinition,
		},
	}
	uniqueFields := []string{"$.imp[0].pmp.deals[*].id", "$.site.publisher.id"}
	return config, uniqueFields
}

// TestEvaluate_RawJsonString_HighValueDealWildcard_CacheHit_Filters verifies that a raw JSON
// request whose deal IDs are extracted via a [*] wildcard path produces a filter decision of
// 1.0 when one of the permutation keys is a cache hit (HighValue hit value 1.0).
func (suite *RawStringEndToEndSuite) TestEvaluate_RawJsonString_HighValueDealWildcard_CacheHit_Filters() {
	const modelIdentifier = "adsp_high-value-deals_v1"
	config, uniqueFields := highValueDealModelConfiguration(modelIdentifier)
	suite.expectPipelineWiring(modelIdentifier, uniqueFields, config)

	// Raw OpenRTB string with three deals under a wildcard path and a scalar publisher id.
	openRtbRequest := `{
		"id": "req-hv-1",
		"site": {"publisher": {"id": "pub123"}},
		"imp": [{
			"pmp": {
				"deals": [
					{"id": "deal-AAA"},
					{"id": "deal-BBB"},
					{"id": "deal-CCC"}
				]
			}
		}]
	}`

	// dealId after IncludeDefaultValue: [deal-AAA, deal-BBB, deal-CCC, no_deal]
	// publisherId after GetFirstNotEmpty: [pub123]
	// BuildKeys (Cartesian product): 4 keys. deal-BBB|pub123 is a cache hit at 1.0.
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(modelIdentifier, "deal-AAA|pub123").
		Return(nil, false).Once()
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(modelIdentifier, "deal-BBB|pub123").
		Return(float32(1.0), true).Once()
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(modelIdentifier, "deal-CCC|pub123").
		Return(nil, false).Once()
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(modelIdentifier, "no_deal|pub123").
		Return(nil, false).Once()

	output := suite.requestEvaluator.Evaluate(&BidRequestEvaluatorInput{OpenRtbRequest: openRtbRequest})

	suite.NotNil(output)
	suite.Equal(1, len(output.Response.Slots), "Slots size should be 1")
	suite.Equal(float32(1.0), output.Response.Slots[0].FilterDecision, "First cache hit (1.0) should drive the filter decision")
	suite.Equal(`{"amazontest":{"decision":1}}`, output.Response.Slots[0].Ext)
	suite.Equal(`{"amazontest":{"learning":0}}`, output.Response.Ext)
}

// TestEvaluate_RawJsonString_HighValueDealWildcard_AllMiss_ReturnsDefault verifies that when
// every permutation key misses the cache, the HighValue default (0.0) becomes the decision.
func (suite *RawStringEndToEndSuite) TestEvaluate_RawJsonString_HighValueDealWildcard_AllMiss_ReturnsDefault() {
	const modelIdentifier = "adsp_high-value-deals_v1"
	config, uniqueFields := highValueDealModelConfiguration(modelIdentifier)
	suite.expectPipelineWiring(modelIdentifier, uniqueFields, config)

	openRtbRequest := `{
		"id": "req-hv-2",
		"site": {"publisher": {"id": "pub456"}},
		"imp": [{
			"pmp": {
				"deals": [
					{"id": "deal-X"},
					{"id": "deal-Y"}
				]
			}
		}]
	}`

	// BuildKeys: deal-X|pub456, deal-Y|pub456, no_deal|pub456 — all cache misses.
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(modelIdentifier, "deal-X|pub456").
		Return(nil, false).Once()
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(modelIdentifier, "deal-Y|pub456").
		Return(nil, false).Once()
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(modelIdentifier, "no_deal|pub456").
		Return(nil, false).Once()

	output := suite.requestEvaluator.Evaluate(&BidRequestEvaluatorInput{OpenRtbRequest: openRtbRequest})

	suite.NotNil(output)
	suite.Equal(1, len(output.Response.Slots), "Slots size should be 1")
	suite.Equal(float32(0.0), output.Response.Slots[0].FilterDecision, "All-miss should yield the HighValue default (0.0)")
	suite.Equal(`{"amazontest":{"decision":0}}`, output.Response.Slots[0].Ext)
	suite.Equal(`{"amazontest":{"learning":0}}`, output.Response.Ext)
}

// lowValueScalarModelConfiguration builds a LowValue model using only scalar JSONPath fields.
func lowValueScalarModelConfiguration(modelIdentifier string) (interfaces.ModelConfiguration, []string) {
	modelDefinition := interfaces.ModelDefinition{
		Identifier:           modelIdentifier,
		Dsp:                  "adsp",
		Name:                 "low-value",
		Version:              "v2",
		Type:                 "LowValue",
		FeatureExtractorType: "JsonExtractor",
		Features: []interfaces.FeatureConfiguration{
			{
				Name:            "publisherId",
				Fields:          []string{"$.site.publisher.id", "$.app.publisher.id"},
				Transformations: []interfaces.TransformerName{"GetFirstNotEmpty"},
			},
			{
				Name:            "country",
				Fields:          []string{"$.device.geo.country"},
				Transformations: []interfaces.TransformerName{},
			},
			{
				Name:            "deviceType",
				Fields:          []string{"$.device.devicetype"},
				Transformations: []interfaces.TransformerName{"GetFirstNotEmpty"},
			},
		},
	}
	config := interfaces.ModelConfiguration{
		ModelDefinitionByIdentifier: map[string]interfaces.ModelDefinition{
			modelIdentifier: modelDefinition,
		},
	}
	uniqueFields := []string{
		"$.site.publisher.id",
		"$.app.publisher.id",
		"$.device.geo.country",
		"$.device.devicetype",
	}
	return config, uniqueFields
}

// TestEvaluate_RawJsonString_LowValueScalar_CacheHit_Filters verifies a raw JSON request with
// scalar JSONPath fields (including a numeric devicetype) produces a filter decision of 0.0 on
// a cache hit (LowValue hit value 0.0).
func (suite *RawStringEndToEndSuite) TestEvaluate_RawJsonString_LowValueScalar_CacheHit_Filters() {
	const modelIdentifier = "adsp_low-value_v2"
	config, uniqueFields := lowValueScalarModelConfiguration(modelIdentifier)
	suite.expectPipelineWiring(modelIdentifier, uniqueFields, config)

	// devicetype is a JSON number (2); UseNumber preserves it as the string "2" in the key.
	openRtbRequest := `{
		"id": "req-lv-1",
		"site": {"publisher": {"id": "539014228"}},
		"device": {"geo": {"country": "USA"}, "devicetype": 2}
	}`

	// GetFirstNotEmpty(publisherId) → 539014228, country → USA, GetFirstNotEmpty(deviceType) → 2
	// Single permutation key: "539014228|USA|2" — cache hit at 0.0 (LowValue filter).
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(modelIdentifier, "539014228|USA|2").
		Return(float32(0.0), true).Once()

	output := suite.requestEvaluator.Evaluate(&BidRequestEvaluatorInput{OpenRtbRequest: openRtbRequest})

	suite.NotNil(output)
	suite.Equal(1, len(output.Response.Slots), "Slots size should be 1")
	suite.Equal(float32(0.0), output.Response.Slots[0].FilterDecision, "LowValue cache hit should filter (0.0)")
	suite.Equal(`{"amazontest":{"decision":0}}`, output.Response.Slots[0].Ext)
	suite.Equal(`{"amazontest":{"learning":0}}`, output.Response.Ext)
}

// TestEvaluate_RawJsonString_LowValueScalar_CacheMiss_Forwards verifies a raw JSON request with
// scalar fields produces a forward decision of 1.0 when the key misses the cache (LowValue
// default value 1.0).
func (suite *RawStringEndToEndSuite) TestEvaluate_RawJsonString_LowValueScalar_CacheMiss_Forwards() {
	const modelIdentifier = "adsp_low-value_v2"
	config, uniqueFields := lowValueScalarModelConfiguration(modelIdentifier)
	suite.expectPipelineWiring(modelIdentifier, uniqueFields, config)

	openRtbRequest := `{
		"id": "req-lv-2",
		"site": {"publisher": {"id": "pub999"}},
		"device": {"geo": {"country": "CAN"}, "devicetype": 4}
	}`

	// Single permutation key "pub999|CAN|4" — cache miss → LowValue default (forward) 1.0.
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(modelIdentifier, "pub999|CAN|4").
		Return(nil, false).Once()

	output := suite.requestEvaluator.Evaluate(&BidRequestEvaluatorInput{OpenRtbRequest: openRtbRequest})

	suite.NotNil(output)
	suite.Equal(1, len(output.Response.Slots), "Slots size should be 1")
	suite.Equal(float32(1.0), output.Response.Slots[0].FilterDecision, "LowValue cache miss should forward (1.0)")
	suite.Equal(`{"amazontest":{"decision":1}}`, output.Response.Slots[0].Ext)
	suite.Equal(`{"amazontest":{"learning":0}}`, output.Response.Ext)
}

// isMobileModelConfiguration builds a LowValue model with a single "isMobile" feature that
// derives site/app from the presence of $.app via the Exists → ApplyMappings chain.
func isMobileModelConfiguration(modelIdentifier string) (interfaces.ModelConfiguration, []string) {
	modelDefinition := interfaces.ModelDefinition{
		Identifier:           modelIdentifier,
		Dsp:                  "adsp",
		Name:                 "is-mobile",
		Version:              "v1",
		Type:                 "LowValue",
		FeatureExtractorType: "JsonExtractor",
		Features: []interfaces.FeatureConfiguration{
			{
				Name:            "isMobile",
				Fields:          []string{"$.app"},
				Transformations: []interfaces.TransformerName{"Exists", "ApplyMappings"},
				Mapping:         map[string]string{"0": "site", "1": "app"},
			},
		},
	}
	config := interfaces.ModelConfiguration{
		ModelDefinitionByIdentifier: map[string]interfaces.ModelDefinition{
			modelIdentifier: modelDefinition,
		},
	}
	uniqueFields := []string{"$.app"}
	return config, uniqueFields
}

// TestEvaluate_RawJsonString_IsMobile_AppAbsent_MapsToSite verifies the end-to-end fix: a raw
// request WITHOUT $.app extracts [""] for the definite path, Exists turns it into "0", and
// ApplyMappings resolves it to the "site" lookup key — matching the Java implementation. This
// previously collapsed to no keys (default score) because the missing field yielded [].
func (suite *RawStringEndToEndSuite) TestEvaluate_RawJsonString_IsMobile_AppAbsent_MapsToSite() {
	const modelIdentifier = "adsp_is-mobile_v1"
	config, uniqueFields := isMobileModelConfiguration(modelIdentifier)
	suite.expectPipelineWiring(modelIdentifier, uniqueFields, config)

	// No $.app in the request → definite path $.app resolves to [""] → Exists "0" → "site".
	openRtbRequest := `{
		"id": "req-mobile-1",
		"site": {"publisher": {"id": "pub123"}}
	}`

	// The single lookup key is "site". Cache hit at 0.0 (LowValue filter) proves the key was
	// actually built (not collapsed to the empty-key default path).
	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(modelIdentifier, "site").
		Return(float32(0.0), true).Once()

	output := suite.requestEvaluator.Evaluate(&BidRequestEvaluatorInput{OpenRtbRequest: openRtbRequest})

	suite.NotNil(output)
	suite.Equal(1, len(output.Response.Slots), "Slots size should be 1")
	suite.Equal(float32(0.0), output.Response.Slots[0].FilterDecision, "app-absent should build the 'site' key and filter on hit")
	suite.Equal(`{"amazontest":{"decision":0}}`, output.Response.Slots[0].Ext)
	suite.Equal(`{"amazontest":{"learning":0}}`, output.Response.Ext)
}

// TestEvaluate_RawJsonString_IsMobile_AppPresent_MapsToApp verifies that a raw request WITH an
// $.app object extracts a non-empty value, Exists turns it into "1", and ApplyMappings resolves
// it to the "app" lookup key.
func (suite *RawStringEndToEndSuite) TestEvaluate_RawJsonString_IsMobile_AppPresent_MapsToApp() {
	const modelIdentifier = "adsp_is-mobile_v1"
	config, uniqueFields := isMobileModelConfiguration(modelIdentifier)
	suite.expectPipelineWiring(modelIdentifier, uniqueFields, config)

	// $.app present as an object → non-empty extracted value → Exists "1" → "app".
	openRtbRequest := `{
		"id": "req-mobile-2",
		"app": {"bundle": "com.example.app"},
		"site": {"publisher": {"id": "pub123"}}
	}`

	suite.mockLocalCacheFactory.EXPECT().
		GetFromLocalCache(modelIdentifier, "app").
		Return(nil, false).Once()

	output := suite.requestEvaluator.Evaluate(&BidRequestEvaluatorInput{OpenRtbRequest: openRtbRequest})

	suite.NotNil(output)
	suite.Equal(1, len(output.Response.Slots), "Slots size should be 1")
	// LowValue cache miss on the "app" key → forward (1.0).
	suite.Equal(float32(1.0), output.Response.Slots[0].FilterDecision, "app-present should build the 'app' key and forward on miss")
	suite.Equal(`{"amazontest":{"decision":1}}`, output.Response.Slots[0].Ext)
	suite.Equal(`{"amazontest":{"learning":0}}`, output.Response.Ext)
}
