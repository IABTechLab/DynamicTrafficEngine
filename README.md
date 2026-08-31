# [Get Started] Dynamic Traffic Engine

# 1. Introduction

## 1.1. About Dynamic Traffic Engine

Dynamic Traffic Engine (DTE) is a high-performance traffic shaping and management system designed for OpenRTB-based programmatic advertising integrations. It enables buyers and sellers to dynamically control bid request traffic flow, implementing sophisticated sampling, filtering, and routing strategies. DTE helps optimize infrastructure costs and operational efficiency by allowing precise control over which bid requests are processed, at what rate, and through which integration paths. The system supports real-time configuration updates, multiple traffic shaping strategies, and detailed monitoring capabilities, making it suitable for organizations operating at scale in the programmatic advertising ecosystem.

Dynamic Traffic Engine consists of three major components:

- **DTE Cloud:**  Hosted by the buyer. The buyer uploads signal files and configuration files containing signal schema, extraction rules, and output data. Signals are derived from OpenRTB requests and based on the buyer's internal traffic shaping rules and algorithms.
- **DTE Evaluator Library:** Contains functionality to provide filtering recommendations based on signals and traffic filtering rules. The library periodically pulls signals and configuration files from DTE Cloud to evaluate each bid request and vends filtering recommendations via API.
- **DTE Filtering**: Once the seller receives filtering recommendations, they are responsible for enforcing the decision to either filter or forward bid requests on the buyer's behalf.

## 1.2. Manual Overview

This manual provides comprehensive guidance on how Sellers can integrate Dynamic Traffic Engine into their existing systems. The table below provides a high-level view of what is needed to integrate with DTE.

| Step | Description | Section |
|------|-------------|---------|
| 1 | Set up access to DTE cloud to pull signals and configuration files | **2.1** |
| 2 | Install DTE evaluator library provided by the Buyer ***OR*** build their own custom evaluator following the specifications and requirement outlined in this manual | **2.2** |
| 3 | Meet DTE filtering feature requirements based on evaluator recommendations | **2.3** |
| 4 | Complete all integration validation steps | **3** |
| 5 | Provide automated weekly reporting as per requirements | **Appendix-2** |

## 1.3. Contact Information

For integration support, contact your buyer's integration team. If you are implementing DTE as a buyer, designate appropriate product and engineering contacts for your seller partners. 

## 1.4. Terminology Glossary

| Term | Definition |
|------|------------|
| SSP | Supply Side Platform (Seller). A programmatic software for publishers to facilitate sales of their advertising impression. |
| DSP | Demand Side Platform (Buyer). A programmatic software for advertisers to purchase advertising impressions. |
| Seller | The supply-side platform sending bid requests. Used interchangeably with SSP. |
| Buyer | The demand-side platform receiving bid requests and responding with bids. Used interchangeably with DSP. |
| openRTB | OpenRTB or Open Real Time Bidding is an industry specification for the programmatic buying and selling of advertising. |
| AWS | Amazon Web Services. Used as the reference cloud implementation. |
| Configuration | Also referred to as 'Signal Metadata'. |
| Tuple | Also referred to as 'Rule'. |

## 1.5. Version History

**2.4 [2026/06/01]**

1. Updated possible locations of model outputs. ***(2.1. DTE Cloud)***
2. Added `modelFormat` and `s3PathMode` to model configuration. ***(2.1.1.1. Model Configuration)***
3. Added `featureExtractorType` and supporting fields to experiment configuration. ***(2.1.1.2. Experiment Configuration)***
4. Added description of Bloom Filter model output. ***(2.1.2. Signal Output File)***

**2.3 [2026/02/05]**

1. Updated to generalized language. ***(All sections)***

**2.2 [2025/10/05]**

1. Added new FAQs. ***(4. FAQs)***

**2.1 [2025/09/24]**

1. Added new model file for monetization insights. ***(2.1.3. RPMA Threshold Sharing - Monetization Insights)***

**2.0 [2025/07/01]**

1. Added `modelType` to model configuration. ***(2.1.1.1. Model Configuration)***
2. Updated model configuration example to include Deals model. ***(2.1.1.1. Model Configuration)***
3. Added a new feature transformation, `IncludeDefaultValue`, which adds the `mappingDefaultValue` into the list of possible values for the feature. ***(2.1.1.1. Model Configuration)***
4. Updated experiment configuration example to include Deals model. ***(2.1.1.2. Experiment Configuration)***
5. Updated model output example to include Deals model. ***(2.1.2. Signal Output File)***
6. Added summary of options to integrate with DTE. ***(2.2.3. DTE Integration Options)***
7. Added note for logging configuration customization. ***(2.2.4. DTE Evaluator Library Installation)***
8. Added details for Golang library initialization. ***(2.2.4. DTE Evaluator Library Installation)***
9. Added details for Golang library request evaluation. ***(2.2.5. Evaluate API)***
10. Updated Map interface for DTE evaluation so value is a List of Strings. ***(2.2.5.1. Request body)***
11. Updated reporting template example. ***(Appendix-2)***
12. Added Appendix with reference Dockerfile for container-based integrations. ***(Appendix-3)***

**1.6 [2025/04/29]**

1. Added more details and definitions on reporting metrics. ***(Appendix-2)***

**1.5 [2025/03/17]**

1. Added `openRtbRequestMap` as new Map option to represent OpenRTB request for DTE evaluation. ***(2.2.5.1. Request Body)***

**1.4 [2025/02/28]**

1. Added bucket parameter to specify correct regionalized S3 bucket for library to read model information from. ***(2.2.4. DTE Evaluator Library Installation)***
2. Clarified interpretation of DTE response values, when to filter requests, and what information to forward to the Buyer. ***(2.3. DTE Filtering)***
3. Added sections on failure handling. ***(2.4. DTE Failure Handling, 3. DTE Integration Validation Requirements)***
4. Added additional FAQs. ***(4. FAQs)***

**1.3 [2025/01/29]**

1. Updated example experiment configuration so that it uses "T" instead of "T1". Added a new `learning` field to determine which treatment(s) are eligible for filtering. ***(2.1.1.2. Experiment Configuration)***
2. Added required dependencies of `log4j-api` and `log4j-core` for proper logging when onboarding with Java library. ***(2.2.1. Pre-requisites)***
3. Added Java (Corretto 17) and Golang (Go 1.21) versions used in testing. ***(2.2.1. Pre-requisites)***
4. Added a sleep to ensure initialization is completed before calling `evaluate`. ***(2.2.4. DTE evaluator library installation)***
5. Updated `filterRecommendation` response field to `filterDecision`. ***(2.2.5.2. Response Body)***

**1.2 [2025/01/22]**

1. Updated configuration file S3 paths. ***(2.1. DTE Cloud)***
2. Renamed the model identifier from `adsp_rpma-lite_v1` to `adsp_low-value_v1`. ***(2.1.1.1. Model Configuration)***
3. Introduced the TreatmentAllocatorOnRandom (by default) to randomly allocate the request to treatment groups based on the predefined weights of groups. It does not require the request ID in the input. ***(2.1.1.2. Experiment Configuration)***

**1.1 [2025/01/14]**

1. Closed beta release for Amazon Ads and directly integrated testing with Sellers.

---

# 2. How to Get Started with Dynamic Traffic Engine

## 2.1. DTE Cloud

The buyer hosts (1) Configuration files and (2) Signal Output files for seller consumption in cloud storage. The reference implementation uses AWS S3, but other cloud storage solutions can be adapted. To set up access to read and download the files from storage, see ***Appendix-1: How to establish connection with DTE cloud.***

**File Data Structure**

*Disclaimer:* Signals will evolve over time. In closed beta, low value signals are available for sharing to take advantage of QPS optimization benefits. Buyers may share high value signals with DTE-integrated sellers in the future.

The following data files will be provided in the cloud bucket:

1. Model Configuration file: `{storage_path}/{seller_name}/configuration/model/config.json`
    1. s3://experiment-traffic-shaping-us-east-1/test_seller/configuration/model/config.json
3. Experiment Configuration file: `{storage_path}/{seller_name}/configuration/experiment/config.json`
    1. s3://experiment-traffic-shaping-us-east-1/test_seller/configuration/experiment/config.json
3. Signal Output files: `{storage_path}/{seller_name}/{yyyy}-{mm}-{dd}/{hh}/{model_identifier_name}` (Dynamic output paths) OR `{storage_path}/{seller_name}/models/{model_identifier_name}` (Static output paths)
    1. s3://experiment-traffic-shaping-us-east-1/test_seller/2025-01-14/04/buyer_low-value_v1.csv
    2. s3://experiment-traffic-shaping-us-east-1/test_seller/models/buyer_low-value_v2.csv

### 2.1.1. Configuration (or Config) File

There are 2 types of configuration files (1) Model Configuration and (2) Experiment Configuration. Both these configuration files are provided in json format and this section provides information on each of these configuration files. ***Note: Fetching and applying the config files is automatically handled by DTE evaluator library (please refer section 2.2 for how to install the library).***

| # | Configuration Type | Description |
|---|-------------------|-------------|
| 1 | ModelConfiguration | This configuration provides information on how the features can be extracted from the OpenRTB request, and transformed into a string that can be matched with the signal output. |
| 2 | ExperimentConfiguration | This configuration provides information on how to split the traffic, such that a portion of the traffic can be allocated for the Buyer's learning purpose (referred to as control group) |

#### 2.1.1.1. Model Configuration

This section describes structure of the model configuration json.

| #  | Field | Description |
|---|-------|-------------|
| 1 | type | Type of the configuration. This type of configurations uses *ModelConfiguration* as the type. |
| 2 | modelDefinitionByIdentifier | Defines one or more signals that the Buyer shares with the Seller. |
| 2.1 | *[identifier-name]* | Name of the signal. Note that value of this key is different for each signal. |
| 2.1.1 | identifier | Name of the signal. This will be same as the parent key. |
| 2.1.2 | dsp | Name of the Buyer (DSP) that is sharing this signal. Ex: example_dsp. |
| 2.1.3 | name | Name of the signal. Unlike the "identifier", this field does not have any version information in the value. |
| 2.1.4 | version | Version of the signal. |
| 2.1.5 | modelType | Type of the model, which determines if the tuple outputs identify high or low value supply. One of "HighValue" or "LowValue". "HighValue" models will evaluate requests as low value if no tuple matches, while "LowValue" models will evaluate requests as high value if no tuple matches. |
| 2.1.6 | modelFormat | Format of the model, which represents how the model file should be interpreted. One of "RULE_BASED" or "BLOOM_FILTER", default is "RULE_BASED".
| 2.1.7 | s3PathMode | Where the model outputs are loacted. One of "DYNAMIC" or "STATIC", default is "DYNAMIC". "DYNAMIC" models are written to unique paths, based on the day and hour of the model (i.e. `{storage_path}/{seller_name}/{yyyy}-{mm}-{dd}/{hh}/{model_identifier_name}`), while "STATIC" models are written to the same path (i.e. `{storage_path}/{seller_name}/models/{model_identifier_name}`), overwriting the last version of the model output with the current output.
| 2.1.8 | featureExtractionType | Specifies how the feature extraction is defined. Currently we support only "JsonExtractor", so the json paths are provided in the config to specify how to extract the features from an OpenRTB request in JSON format. |
| 2.1.9 | features | Provides an ordered list of features, and information on how to extract and transform each of those features. |
| 2.1.9.1 | name | Name of the feature |
| 2.1.9.2 | fields | An ordered list of one of more json paths in the OpenRTB request from where the values have to be extracted. |
| 2.1.9.3 | transformation | An ordered list of transformations that must be applied on the values extracted. Currently we have (1) Exists, (2) ConcatenateByPair, (3) GetFirstNonEmpty, (4) IncludeDefaultValue, and (5) ApplyMappings as the named transformations, A reference implementation for each of these transformations is provided in the library. |
| 2.1.9.4 | mapping | *[Optional]* A mapping provided to help with the 'ApplyMappings' transformation. |
| 2.1.9.5 | mappingDefaultValue | *[Optional]* A mapping provided to help with the 'ApplyMappings' transformation. Use this value when the extracted value cannot be mapped using the 'mapping' field. |

*Example:*

```json
{
  "type": "ModelConfiguration",
  "modelDefinitionByIdentifier": {
    "buyer_low-value_v2": {
      "identifier": "buyer_low-value_v2",
      "dsp": "example_dsp",
      "name": "low-value",
      "version": "v2",
      "type": "LowValue",
      "featureExtractorType": "JsonExtractor",
      "features": [
        {
          "name": "isMobile",
          "fields": [
            "$.app"
          ],
          "transformation": [
            "Exists",
            "ApplyMappings"
          ],
          "mapping": {
            "0": "site",
            "1": "app"
          },
          "mappingDefaultValue": null
        },
        {
          "name": "isVideo",
          "fields": [
            "$.imp[0].video"
          ],
          "transformation": [
            "Exists",
            "ApplyMappings"
          ],
          "mapping": {
            "0": "banner",
            "1": "video"
          },
          "mappingDefaultValue": null
        },
        {
          "name": "publisherId",
          "fields": [
            "$.site.publisher.id",
            "$.app.publisher.id"
          ],
          "transformation": [
            "GetFirstNotEmpty"
          ]
        },
        {
          "name": "country",
          "fields": [
            "$.device.geo.country"
          ],
          "transformation": []
        },
        {
          "name": "slotSize",
          "fields": [
            "$.imp[0].video.w",
            "$.imp[0].video.h",
            "$.imp[0].banner.w",
            "$.imp[0].banner.h"
          ],
          "transformation": [
            "ConcatenateByPair",
            "GetFirstNotEmpty"
          ]
        },
        {
          "name": "slotPosition",
          "fields": [
            "$.imp[0].video.pos",
            "$.imp[0].banner.pos"
          ],
          "transformation": [
            "GetFirstNotEmpty",
            "ApplyMappings"
          ],
          "mapping": {
            "1": "a",
            "4": "a",
            "7": "a",
            "3": "b"
          },
          "mappingDefaultValue": "u"
        },
        {
          "name": "deviceType",
          "fields": [
            "$.device.devicetype"
          ],
          "transformation": [
            "GetFirstNotEmpty",
            "ApplyMappings"
          ],
          "mapping": {
            "1": "5",
            "2": "2",
            "3": "3",
            "4": "4",
            "5": "5",
            "6": "6",
            "7": "7",
            "8": "8"
          },
          "mappingDefaultValue": "0"
        }
      ]
    },
    "buyer_high-priority-deals_v1": {
      "identifier": "buyer_high-priority-deals_v1",
      "dsp": "example_dsp",
      "name": "high-priority-deals",
      "version": "v1",
      "type": "HighValue",
      "featureExtractorType": "JsonExtractor",
      "features": [
        {
          "name": "dealId",
          "fields": [
            "$.imp[0].pmp.deals[*].id"
          ],
          "transformation": [
            
          ],
          "mappingDefaultValue": null
        },
        {
          "name": "isVideo",
          "fields": [
            "$.imp[0].video"
          ],
          "transformation": [
            "Exists",
            "ApplyMappings"
          ],
          "mapping": {
            "0": "DISPLAY",
            "1": "VIDEO"
          },
          "mappingDefaultValue": null
        },
        {
          "name": "deviceType",
          "fields": [
            "$.device.devicetype"
          ],
          "transformation": [
            "GetFirstNotEmpty",
            "ApplyMappings"
          ],
          "mapping": {
            "1": "MOBILE",
            "2": "DESKTOP",
            "3": "CONNECTEDTV",
            "4": "MOBILE",
            "5": "MOBILE",
            "6": "CONNECTEDDEVICE",
            "7": "CONNECTEDTV",
            "8": "UNKNOWN"
          },
          "mappingDefaultValue": "UNKNOWN"
        },
        {
          "name": "country",
          "fields": [
            "$.device.geo.country"
          ],
          "transformation": [
            "IncludeDefaultValue"
          ],
          "mappingDefaultValue": "ALL"
        }
      ]
    },
    "buyer_inv_v1": {
      "identifier": "buyer_inv_v1",
      "dsp": "buyer",
      "name": "inv",
      "version": "v1",
      "modelType": "LowValue",
      "modelFormat": "BLOOM_FILTER",
      "s3PathMode": "STATIC",
      "featureExtractorType": "JsonExtractor",
      "features": [
        {
          "name": "isMobile",
          "fields": [
            "$.app"
          ],
          "transformation": [
            "Exists",
            "ApplyMappings"
          ],
          "mapping": {
            "0": "web",
            "1": "app"
          },
          "mappingDefaultValue": null
        },
        {
          "name": "isVideo",
          "fields": [
            "$.imp[0].video"
          ],
          "transformation": [
            "Exists",
            "ApplyMappings"
          ],
          "mapping": {
            "0": "banner",
            "1": "video"
          },
          "mappingDefaultValue": null
        },
        {
          "name": "publisherId",
          "fields": [
            "$.site.publisher.id",
            "$.app.publisher.id"
          ],
          "transformation": [
            "GetFirstNotEmpty"
          ]
        },
        {
          "name": "siteOrBundle",
          "fields": [
            "$.site.domain",
            "$.app.bundle"
          ],
          "transformation": [
            "GetFirstNotEmpty"
          ]
        }
      ]
    }
  }
}
```

#### 2.1.1.2. Experiment Configuration

This section describes structure of the experiment configuration json. The experiment specifies how to split the traffic into 2 or more groups such that one of the traffic group can be used by the Buyer to learn about the model performance and train new models for the Seller consumption. *A reference implementation on how to consume the experiment configuration and also split the traffic is provided in the Library.*

| #  | Field | Description |
|---|-------|-------------|
| 1 | type | Type of the configuration. This type of configurations uses *ExperimentConfiguration* as the type. |
| 2 | experimentDefinitionByName | Defines one or more experiments that the Buyer shares with the Seller. |
| 2.1 | *[identifier-name]* | Name of the experiment. Note that value of this key is different for each experiment. |
| 2.1.1 | name | Name of the experiment. This will be same as the parent key. |
| 2.1.2 | type | Type of the experiment. Currently only the type "soft-filter" is supported. |
| 2.1.3 | aggregationSchema | Aggregation of model results. Determines how the results of each model should be combined with each other to give a single filter recommendation. If not specified, defaults to the most conservative decision.
| 2.1.3.1 | operator | How to combine one or more model results. One of "AND" or "OR". "AND" specifies that all models must return a "filter" decision to resolve to an overall "filter" decision, while "OR" specifies that any model that returns a "filter" decision will resolve to an overall "filter" decision.
| 2.1.3.2 | conditions | List of recursive-supported operands. The list can be either model identifers, or a nested object of an operator and conditions.
| 2.1.3.3 | [model-identifier] | The model identifier defined in the model configuration. *See Section 2.1.1.1*
| 2.1.4 | startTimeUTC | Specifies the time on when to start using this experiment. The value is provided as UTC epoch milli-seconds. |
| 2.1.5 | endTimeUTC | Specifies the time on when to stop using this experiment. The value is provided as UTC epoch milli-seconds. |
| 2.1.6 | allocationIdStart | It is used when you use **TreatmentAllocatorOnHash** in the **provideTreatmentAllocator** and provide the request id in the input. Specifies the start and end integers for allocation ids. These allocation ids are split between the treatment groups to in turn split the requests. Sellers are expected to implement (*reference code avalable in library*) logic that randomly assigns an allocationId (integer) to each request that can be evaluated by the model. **It has to be between 0 and 4095 (inclusive).** *We use first 3 chars of the hexdecimal of the request id which is 16^3, as the allocation id.* |
| 2.1.7 | allocationIdEnd | See above |
| 2.1.8 | treatments | Provides an ordered list of treatments (traffic groups), and how to allocate traffic between these groups. |
| 2.1.8.1 | treatmentCode | Name of the treatment |
| 2.1.8.2 | idStart | These fields specifies a range of allocationIds. Requests associated with allocation Ids, that fall within this range are associated with this treatment. **It has to be between 0 and 4095 (inclusive).** *We use first 3 chars of the hexdecimal of the request id which is 16^3, as the allocation id.* |
| 2.1.8.3 | idEnd | See above |
| 2.1.8.4 | weight | [By Default] It is used when you use **TreatmentAllocatorOnRandom** in the **provideTreatmentAllocator**. Specifies the probability that one request is allocated to one group. |
| 2.1.8.5 | learning | An integer flag to determine if the given treatment (traffic group) is used for learning purposes. Traffic allocated to a learning value of 1 should not be subject to filtering, while traffic allocated to a learning value of 0 is subject to filtering. We still expect Sellers to add extension fields in the bid-requests based on the model filtering evaluation or send encoded via header. *See Section 2.2.5* |
| 3 | modelToExperiment | A map of models to experiments, to specify which experiment to use when making filtering decisions for a given model. |
| 3.1 | [model-identifier] | The key in this map is the model identifier defined in the model configuration. *See Section 2.1.1.1* |

*Example:*

```json
{
  "type": "ExperimentConfiguration",
  "experimentDefinitionByName": {
    "DemandDrivenTrafficEvaluatorSoftFilter": {
      "name": "DemandDrivenTrafficEvaluatorSoftFilter",
      "type": "soft-filter",
      "aggregationSchema": {
        "operator": "AND",
        "conditions": [
          {
            "operator": "OR",
              "conditions": [
                { "modelIdentifier": "buyer_low-value_v2" },
                { "modelIdentifier": "buyer_inv_v1" }
              ]
          },
          { "modelIdentifier": "buyer_high-priority-deals_v1" }
        ]
      },
      "treatments": [
        {
          "treatmentCode": "T",
          "idStart": "0",
          "idEnd": "3276",
          "weight": 80
        },
        {
          "treatmentCode": "C",
          "idStart": "3277",
          "idEnd": "4095",
          "weight": 20
        }
      ],
      "salt": "edf2e9cbdd2d1134",
      "startTimeUTC": 1654498800000,
      "endTimeUTC": 4102358400000,
      "allocationIdStart": "0",
      "allocationIdEnd": "4095",
      "hash": true
    }
  },
  "modelToExperiment": {
    "buyer_low-value_v2": "DemandDrivenTrafficEvaluatorSoftFilter",
    "buyer_inv_v1": "DemandDrivenTrafficEvaluatorSoftFilter",
    "buyer_high-priority-deals_v1": "DemandDrivenTrafficEvaluatorSoftFilter"
  }
}

```

### 2.1.2. Signal Output File

This is the raw signal output file. The values for each feature is derived from the OpenRTB request that a Seller sends to the Buyer. ***Note: Fetching Signal Output files are automatically handled by the evaluator library (please refer section 2.2 for how to install the DTE library or build your own).***

**Example - Low Value Model**

```
// rules are defined by:
// "isMobile|isVideo|publisherId|country|slotSize|slotPosition|deviceType" 
app|banner|537075271|MEX|320x50|a|4
app|banner|537075271|USA|300x250|a|3
app|video|540233827|CAN|640x390|b|7
app|video|540233827|USA|640x390|a|4
app|video|540233831|USA|640x390|b|6
site|banner|537154197|USA|336x280|u|2
site|banner|537154197|USA|728x90|u|2
site|banner|537154278|CAN|160x600|a|4
site|video|539957557|MEX|640x390|b|2
site|video|540393169|ALG|640x390|b|4
site|video|540269068|MEX|640x390|b|0
site|video|539188034|CAN|640x390|a|2
site|video|559785277|JPN|640x390|u|2
```

**Example - High Value Deals Model**

```
// rules are defined by:
// "dealId|isVideo|deviceType|country" 
deal123|DISPLAY|DESKTOP|USA
deal123|DISPLAY|MOBILE|USA
deal456|VIDEO|DESKTOP|ALL
deal789|VIDEO|CONNECTEDTV|CAN
```

**Example - Bloom Filter Model**

This would be a .bloom byte file, that is serialized and deserialized using the Google Guava Bloom Filter library.

### 2.1.3. RPMA Threshold Sharing - Monetization Insights

**Important Note**: This data is provided for Seller traffic optimization insights only and is **NOT** used by the DTE evaluator library for filtering recommendation. The DTE evaluator library will only use the the model files specified in Section 2.1.2 for filter/no-filter decisions.

#### 2.1.3.1. Overview

Buyers may provide additional monetization model files that give Sellers insights into request performance using RPMA (Revenue Per million Ad Requests) metrics. This data helps Sellers identify their lowest and highest performing traffic patterns and optimize their traffic shaping within QPS allocation caps.

#### 2.1.3.2. File Location

Monetization insight files are available at:

`{storage_path}/{seller_name}/insights/{yyyy}-{mm}-{dd}/{hh}/monetization_insights_v1.csv`

Example:
`s3://experiment-traffic-shaping-us-east-1/test_seller/insights/2025-01-14/04/monetization_insights_v1.csv`

#### 2.1.3.3. Data Structure

The file contains incoming requests, impressions and RPMA data for traffic tuples defined by the following dimensions:

| Dimension | oRTB | Description | Mapping |
|-----------|------|-------------|---------|
| delivery_channel | `$.site`, `$.app` | Site or App. "ctv" in delivery_channel is a special case which should be matched "app" blob and "device_type" of 3 (TV). |  |
| format | `$.imp.banner`, `$.imp.video` | Media type like banner or video |  |
| country_code | `$.device.geo.country` | Country code using ISO-3166-1-alpha-3. |  |
| publisher_id | `$.site.publisher.id`, `$.app.publisher.id` | Exchange-specific seller ID. Every ID must map to only a single entity that is paid for inventory transacted via that ID. Corresponds to a seller_id of a seller in the exchange's sellers.json file. |  |
| slot_size | `$.imp[0].video.w`, `imp[0].video.h`, `$.imp[0].banner.w`, `imp[0].banner.h` | Width/height of the video player in device independent pixels (DIPS). Exact width/height in device-independent pixels (DIPS); recommended if no format objects are specified. |  |
| slot_position | `$.imp[0].video.pos` | Ad position on screen |  |
| device_type | `$.device.devicetype` | The general type of device. Refer to [List: Device Types](https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/master/AdCOM%20v1.0%20FINAL.md#list--device-types-) in AdCOM 1.0. | 1: MOBILE, 2: DESKTOP, 3: CONNECTEDTV, 4: MOBILE, 5: MOBILE, 6: CONECTEDDEVICE, 7: CONNECTEDTV, 8: UNKNOWN |
| os_name | Derived from `$.device.sua` | Operating system name derived from the user-agent string through WURFL service |  |
| device_make | Derived from `$.device.sua` | Device make derived from the user-agent string through WURFL service |  |
| device_browser | Derived from `$.device.sua` | Device browser derived from the user-agent string through WURFL service |  |
| **Metric** |  |  |  |
| incoming |  | Weekly average incoming bid request count for a given hour |  |
| impressions |  | Weekly average impressions count for a given hour |  |
| rpma |  | Revenue per millions ad requests |  |
| should_filter |  | Filtering decision ("True" or "False") |  |

#### 2.1.3.4. Usage Guidelines

Sellers can use this data to:

1. Identify High-Value Traffic: Focus QPS allocation on tuples with higher RPMA values
2. Optimize Traffic Mix: Prioritize sending requests that match higher-performing patterns over lower-performing patterns
3. Traffic Shaping: Make informed decisions about which traffic to send within QPS caps

#### 2.1.3.5. Example Data Format

```
// tuples are defined by:
// "delivery_channel|format|country_code|publisher_id|slot_size|slot_position|device_type|os_name|device_maker|device_browser|incoming|impressions|rpma|should_filter

ap|banner|USA|pub_12345|320x50|a|4|Android|Samsung|Chrome|5000|1236|22.23|FALSE
site|banner|GBR,pub_67890|728x90|a|2| | |Chrome|10000|755|1.80|TRUE
app|video|CAN|pub_11111|640x390| | | | | |5500|400|4.0| TRUE
```

Important note:

1. The entries that have blanks indicate that all the permutations under that parent have very similar RPMAs and we are consolidated them into a single entry.
2. `ctv` in delivery_channel is a special case which should be matched `app` blob and `device_type` of 3 (TV).

## 2.2. DTE Evaluator Library

The DTE evaluator library contains functionality to provide filtering recommendations based on given traffic rules and signals. The Evaluator periodically pulls signals and configuration files from DTE cloud to evaluate Seller bid requests and provide a filtering recommendation to the Seller. The Seller then uses this information to either forward the traffic to the Buyer or filter the traffic.

**Important note:**

The evaluator library simplifies DTE integration for Sellers:

1. The library evaluates OpenRTB requests against the `Signal Output files` by constructing required tuples for matching.
2. The library reads and applies the `learning percentage` specified in the `Configuration file` to create `treatment` and `control` groups for the experiment.
3. The library automatically syncs every 5 minutes to look for new updates in the `Signal Output files` and `Configuration files` to get most updated filtering recommendation from the Buyer.
4. The DTE evaluator library will only use the the model files specified in Section 2.1.2 for filter/no-filter decisions.

*Will the Buyer provide this DTE Evaluator library to Seller or Seller has to build its own?*

The Buyer will provide the link to the source code. If the Seller decides to build their own version of the evaluator library or custom evaluation logic, we recommend to use the DTE evaluator library source code as guideline and meet specifications and integration requirements in ***Section 3. DTE integration validation requirements.***

### 2.2.1. Pre-requisites

Before starting the integration, ensure you have the following:

1. Cloud account with appropriate permissions (e.g. AWS account - refer to Section 2.1)
2. Access to the Buyer's cloud storage account (e.g. AWS S3 bucket - refer to Section 2.1)
3. Use Java Development Kit (JDK) or Golang (unless building your own custom library). The Java library was tested with [Amazon Corretto 17](https://docs.aws.amazon.com/corretto/latest/corretto-17-ug/downloads-list.html) and the Golang library was tested with [Go 1.21](https://tip.golang.org/doc/go1.21).
4. [Java only] For proper logging, `log4j-api` and `log4j-core`, which can be downloaded from [apache-log4j-2.24.3-bin.zip](https://downloads.apache.org/logging/log4j/2.24.3/apache-log4j-2.24.3-bin.zip), must be dependencies in your Java application

### 2.2.2. DTE Evaluator Library Overview

The DTE evaluator library is composed of the following components.

1. ***Evaluator (Online filtering component)***. The Seller service integrates with the library to get recommendations on whether a bid request should be forwarded to the Buyer.
2. ***Configuration (Config) Store***. This is the in-memory object that contains information on the signal schema and how to extract features from the incoming OpenRTB request.
3. ***Signal Store***. This is the in-memory object that contains the actual signal output data.
4. ***Signal/Config Refresher***. This component is responsible for connecting to the DTE Cloud and periodically fetch and refresh signals/configs into the in-memory stores.

### 2.2.3. DTE Integration Options

There are several options to integrate with DTE and utilize the shared signals for traffic shaping.

1. Natively integrate the DTE library into the Ad Serving stack with the provided Java or Golang library. The following sections provide additional details on how to initialize the library and evaluate requests.
2. Create a DTE microservice, which is called by the Ad Serving stack. The Java library is recommended for the microservice. Appendix 3 provides a reference Dockerfile for initializing DTE.
3. Custom integration implementation. Refer to section 3 for a complete set of requirements that must be implemented.

### 2.2.4. DTE Evaluator Library Installation

The `BidRequestEvaluatorFactory` class will instantiate all the objects necessary to successfully filter traffic. In the Seller service Spring/Guice config an instance of the `BidRequestEvaluatorFactory` **must be explicitly initialized** after providing all the necessary inputs:

1. ***supplierName***: This is a string that uniquely identifies the Seller within the Buyer's system. The library uses this information to appropriately consume signals and config from the Buyer's cloud account.
2. ***credentialsProvider***: This is an object of type `AWSCredentialProvider`, used for connecting to the Buyer's cloud account. Buyers host signals for Seller consumption in a storage (e.g. S3) bucket, and the onboarding process involves setting up access to read and download signal data from this bucket. See *Appendix - 1 How to establish connection with DTE cloud*, for an example on how to configure this credential provider.
3. ***region***: This is a string that identifies the region of the cloud storage (e.g. AWS S3) client to be initialized in. In most cases, this will be the cloud region the instance is initialized in.
4. ***bucket***: This is a string that identifies the regionalized cloud storage (see *Section 2.1*) from which the library fetches all (regionalized) model configuration and output files. In most cases, the bucket will be of the form `experiment-traffic-shaping-*model_aws_region*`, where `*model_aws_region*` is one of: `us-east-1`, `us-west-2`, `eu-west-1`, and `ap-southeast-1`.
5. ***executor*** *(optional)*: The Signal/Config refresher, will look up for new signals/configs in the Buyer's cloud account once every 5 minutes. The library can schedule this periodic task on the provided executor. The executor should be an instance of `ScheduledExecutorService`. If this instance is not provided, the factory will instantiate an instance of a `ScheduledExecutorService` internally, as a fallback.

```java
BidRequestEvaluatorFactory bidRequestEvaluatorFactory = 
    BidRequestEvaluatorFactory.create(
        supplierName,
        credentialsProvider,
        region,
        bucket,
        executor);

// initialize background tasks to periodically fetch new signal data to evaluate.
bidRequestEvaluatorFactory.getTaskInitializer().init();

// create an evaluator instance to receive recommendations. 
BidRequestEvaluator bidRequestEvaluator = bidRequestEvaluatorFactory.getEvaluator();

// sleep to ensure initialization is complete before evaluating requests.
Thread.sleep(10000);
```

In addition, update the logging configurations under `src/main/resources/log4j2.xml` as needed.

For the Golang library, the initialization method is slightly different.

1. ***supplier-name***: This is a string that uniquely identifies the Seller within the Buyer's system. The library uses this information to appropriately consume signals and configurations.
2. ***credentials***: This is a reference to the Cloud Credentials Provider used for connecting to the Buyer's cloud account. Buyers host signals for Seller consumption in a storage (e.g. S3) bucket, and the onboarding process involves setting up access to read and download signal data from this bucket. See *Appendix - 1 How to establish connection with DTE cloud*, for an example on how to configure this credential provider.
3. ***region***: This is a string that identifies the region of the cloud storage (e.g. AWS S3) client to be initialized in. In most cases, this will be the cloud region the instance is initialized in.
4. ***folder-prefix/bucket-name***: This is a string of the local path prefix or the bucket prefix where the configuration data and model data is stored. E.g. `local/path/to/metadata` or `s3://experiment-traffic-shaping-us-east-1`.
5. ***schedule-period***: This is an integer that represents the period (in milliseconds) of the scheduled tasks. E.g. a value of 300000 checks for new configuration and model data every 5 minutes.

```go
demanddriventrafficevaluator.NewTaskInitializer(
    supplier-name,
    credentials,
    aws-region,
    folder-prefix/bucket-name,
    schedule-period
).Init()
    
requestEvaluator := demanddriventrafficevaluator.NewRequestEvaluator(
    supplier-name,
    credentials,
    aws-region,
    folder-prefix/bucket-name
)
```

### 2.2.5. Evaluate API

Once the evaluator instance is created, it should be used by the Seller to receive bid or no-bid recommendations. Sellers should invoke the `evaluate` method on that instance, before request is forwarded to the Buyer. This call will return recommendations, on whether the request should be forwarded to the Buyer.

```java
// API call sample using Java library
BidRequestEvaluatorOutput bidRequestEvaluator.evaluate(BidRequestEvaluatorInput request);
```

For the Golang library, the following method evaluates a given bid request:

```go
requestOutput := requestEvaluator.Evaluate(&evaluation.BidRequestEvaluatorInput{
    OpenRtbRequest: open-rtb-request-string,
    OpenRtbRequestMap: map[string]string
})
```

#### 2.2.5.1. Request Body

| Request Body Field | Type | Description |
|--------------------|------|-------------|
| openRtbRequest | string | Raw OpenRTB request, in JSON format. One of openRtbRequest and openRtbRequestMap must be present. If both are present, openRtbRequest is used. Example: `"{\"cur\":[\"USD\"],\"site\":{\"domain\":\"www.domain.com\",\"publisher\":{\"id\":\"pub123\"},\"id\":\"site123\",\"page\":\"https://www.domain.com/fullurl\"},\"id\":\"request123\",\"imp\":[{\"banner\":{\"pos\":1,\"w\":970,\"h\":250,\"format\":[{\"w\":970,\"h\":250}]},\"bidfloor\":1.23,\"bidfloorcur\":\"USD\",\"id\":\"1\"}],\"user\":{\"id\":\"user123\"},\"device\":{\"geo\":{\"zip\":\"00000\",\"country\":\"USA\",\"city\":\"testcity\"},\"ua\":\"ua_string123\",\"make\":\"desktop\",\"devicetype\":2}}"` |
| openRtbRequestMap | object | Abridged OpenRTB request, as a Map of string -> List<string>. The keys are the path of the field, in [dot notation described in JsonPath](https://github.com/json-path/JsonPath?tab=readme-ov-file#getting-started), and the values are a list of string value(s) of the field. One of openRtbRequest and openRtbRequestMap must be present. If both are present, openRtbRequest is used. Example construction: `Map<String, List<String>> openRtbRequestMap = new HashMap<>(); openRtbRequestMap.put("$.app", List.of("testapp")); openRtbRequestMap.put("$.imp[0].video", List.of("testvideo")); openRtbRequestMap.put("$.app.publisher.id", List.of("pub123")); openRtbRequestMap.put("$.device.geo.country", List.of("USA")); openRtbRequestMap.put("$.imp[0].video.w", List.of("123")); openRtbRequestMap.put("$.imp[0].video.h", List.of("456")); openRtbRequestMap.put("$.imp[0].video.pos", List.of("1")); openRtbRequestMap.put("$.device.devicetype", List.of("4")); openRtbRequestMap.put("$.imp[0].pmp.deals[*].id", List.of("deal123", "deal456"));` |

#### 2.2.5.2. Response Body

| Response Body Field | Type | Description |
|--------------------|------|-------------|
| response | Object, required | Filter recommendation for the Buyer |
| **Response Object** |  |  |
| slots (imps) | array of objects, required | Evaluation of signals for each slot (imp object) of the incoming bid request |
| ext | string, required | Seller is expected to add this JSON blob to the `ext` field in the root level object of the OpenRTB request that they forward to the Buyer. This field contains information on whether the evaluator internally assigned the request to treatment (*learning*=0) or control (*learning*=1). Example value: `"amazontest": {"learning": 1}` |
| **Slots Object** |  |  |
| filterDecision | float, required | Recommended filter decision for the slot based on Buyer's signal(s). This is a value ranging from 0.0 to 1.0, where 0.0 indicates no probability of getting response from the Buyer, and 1.0 indicates highest probability to get a response from the Buyer. |
| reasonCode | string, optional | Future extension to provide more information on how we arrived at the filter recommendation. |
| signals | array of objects, optional | Future extension to provide more detailed information regarding executed signals. |
| ext | string, required | Seller is expected to add this json blob to the `ext` field in the `imp` object of the oRTB request that they forward to the Buyer. This field contains information about the decision taken by the evaluator internally. Example value: `"amazontest": {"decision": 0.0}` |
| **Signals Object (future extension)** |  |  |
| name | string | Name of the signal |
| version | int | Version of the signal that was evaluated for this slot |
| status | string | Status of signal execution, one of SUCCESS, ERROR, TIMEOUT |
| debugInfo | string | Debug logs for the signal |

## 2.3. DTE Filtering

Once the Seller receives filtering recommendations from DTE evaluator library, Sellers are responsible for enforcing the decision on behalf of the Buyer to either filter or forward the bid request based on the value of `Response.slots[*].filterDecision`. If the `filterDecision` value is 0.0, DTE recommends the Seller filter the request, and if the value is 1.0, DTE recommends the Seller to forward the request.

The Seller will also need to send the following custom extension fields either in JSON or via HTTP header:

If adding ext to bid request JSON

1. `slots[*].ext.amazontest.decision`: Recommended filter decision for the slot based on Buyer's signals.
    1. 0.0 = filter slot (low-value request)
    2. 1.0 = forward slot (high-value request)
2. `ext.amazontest.learning`:
    1. 0 if request is in treatment, Seller evaluates request and filter/forward based on filter decision;
    2. 1 if request is in control, Seller evaluates request, but ALWAYS forward the request regardless of filter decision.

These extensions can be retrieved in the DTE Response object under the `Response.getExt` and `Response.slots[*].getExt` methods and can be appended as-is to the OpenRTB request forwarded to the Buyer.

Note that if the request is in control (learning=1), the `Response.slots[*].filterDecision` value will always be 1.0, regardless of the model result. If the request is in treatment (learning=0), the `Response.slots[*].filterDecision` value can be either 0.0 or 1.0, based on the model result.

If sending via HTTP header, currently only available in Java implementation:
1. Retrieve the protobuf generated message from Response using `Response.toExtProto`
2. Generate a URI safe string using `ResponseUtil.encodedResponseMetadata`
3. Send this string as value for HTTP Header named `X-Amazon-Test: <encoded string>`

## 2.4. DTE Failure Handling

When the current hour's model output is not available to be fetched, the DTE library uses the latest successfully loaded model results to evaluate requests for up to 24 hours (the default local cache TTL). After the 24 hour window, all entries in the cache will expire, and no requests will be evaluated as low-value/to be filtered.

In the event that the DTE library evaluation throws an exception, or is unable to properly evaluate the request with the current models, a default fallback response to not filter the request with learning set to 1 (control group) will be provided. In the extraordinary scenario where no response is received, the Seller should assume the default fallback response and forward the request according to their normal flow.

```json
// default BidRequestEvaluatorOutput response for single slot (imp) request
{
    "response":
    {
        "slots":
        [
            {
                "filterDecision": 1.0,
                "ext": "{\"amazontest\":{\"decision\":1.0}}"
            }
        ],
        "ext": "{\"amazontest\":{\"learning\":1}}"
    }
}
```

---

# 3. DTE Integration Validation Requirements

This integration validation checklist ensures that DTE is correctly implemented and meets all the requirements for filtering traffic on behalf of the Buyer. The checklist is applicable whether the Seller decides to install the DTE evaluator library provided by the Buyer or builds their own evaluator logic in addition to the filtering logic. The Buyer will provide technical support as needed to ensure successful integration.

| Item | Integration Validation Checklist | Integration Validation Steps | Section Reference |
|------|----------------------------------|------------------------------|-------------------|
| **DTE Cloud** |  |  |  |
| **1** | **Seller can access the Buyer's resources using the provided credentials** | 1. Seller successfully sets up IAM roles as per documentation | Section 2.1, Appendix 1 |
|  |  | 2. Seller uses STS AssumeRole for accessing Buyer's resources | Section 2.1, Appendix 1 |
|  |  | 3. Seller is able to access all configurations and signal output files using AssumeRole | Section 2.1, Appendix 1 |
| **DTE Evaluator Library [Seller installs evaluator library provided by the Buyer]** |  |  |  |
| **2** | **Seller can evaluate bid requests using DTE evaluator library** | 1. Seller successfully integrates with DTE evaluator library | Section 2.2.4 |
|  |  | 2. Seller can initialize the `BidRequestEvaluatorFactory` with provided credentials | Section 2.2.4 |
|  |  | 3. Seller call evaluate method on all bid requests and gets back a valid response | Section 2.2.5 |
| **DTE Evaluator Library [Seller builds custom DTE evaluator]** |  |  |  |
| **3** | **Seller can evaluate bid requests using custom DTE evaluator library** | 1. Review DTE evaluator library source code as a guideline | Section 2.2 |
|  |  | 2. Implement S3 bucket access and file download mechanism | Section 2.1 |
|  |  | 3. Implement Configuration file parser | Section 2.1.1 |
|  |  | 4. Implement Signal Output file parser | Section 2.1.2 |
|  |  | 5. Implement periodic refresh mechanism (every 5 minutes, with randomized initialization to prevent throttling) | Section 2.2.4 |
|  |  | 6. Implement evaluation logic based on `Configuration files` and `Signal Output files` e.g. Seller will need to evaluate OpenRTB requests against the `signal output files` using extraction logic from `Configuration file` to build required `tuples for evaluation` | Section 2.2.5 |
|  |  | 7. Implement change detection for Signal Metadata and Rules files using S3 ETag value | Section 2.2 |
|  |  | 8. Implement logic to update extraction rules when changes are detected, **without code change** - *this is important so that Seller is always pulling the latest files within required SLA without further engineering work* | Section 2.2 |
|  |  | 9. Implement logic to apply detected changes (for e.g. add a new feature or remove a feature or change treatment/control ratio) within 5 minutes, **without code change** (assuming same transformation) | Section 2.2 |
| **DTE Filtering** |  |  |  |
| **4** | **Seller can filter traffic based on DTE recommendation on behalf of the Buyer** | **1. Validate correct handling of treatment and control groups** | Section 2.1.1 |
|  |  | a. Test that requests with ext.amazontest.learning = 0 (treatment) follow filtering recommendations |  |
|  |  | b. Test that requests with ext.amazontest.learning = 1 (control) are always forwarded |  |
|  |  | c. Verify that control group requests still evaluate signals but ignore recommendations |  |
|  |  | **2. Validate integration with BidRequestEvaluator** | Section 2.2.5 |
|  |  | a. Verify that all incoming requests are passed through the evaluator [excluding Programmatic Guaranteed Deals requests] |  |
|  |  | **3. Validate custom extension field ext.amazontest.learning is added correctly** | Section 2.3 |
|  |  | a. Validate that the field is present in all forwarded bid requests |  |
|  |  | b. Validate that the value is either 0 (treatment) or 1 (control) |  |
|  |  | **4. Validate custom extension field imp[*].ext.amazontest.decision is added correctly** | Section 2.3 |
|  |  | a. Validate that the field is present in all forwarded bid requests |  |
|  |  | b. Validate that the value is double (0.0 for filter, 1.0 for forward) - On Buyer's side, 0.0 is only expected when learning = 1 |  |
|  |  | **5. Validate filtering logic implementation** | Section 2.3 |
|  |  | a. Test that requests with learning = 0 and imp.ext.amazontest.decision = 0.0 are filtered |  |
|  |  | b. Test that requests with learning = 0 and imp.ext.amazontest.decision = 1.0 are forwarded |  |
|  |  | c. Test that requests with learning = 1 and imp.ext.amazontest.decision = 0.0 are forwarded |  |
|  |  | d. Test that requests with learning = 1 and imp.ext.amazontest.decision = 1.0 are forwarded |  |
|  |  | e. Validate that filtering is applied at the slot (imp) level, not the entire request |  |
| **DTE Failure Handling** |  |  |  |
| **5** | **Seller can handle DTE failure cases and execute fallback logic** | **1. Validate correct fallback behavior when current model hour is missing** | Section 2.4 |
|  |  | a. Test that requests are evaluated with last succesfully loaded model when current model hour model cannot be fetched |  |
|  |  | b. Test that requests are never marked to filter if no model has been loaded for 24 hours |  |
|  |  | **2. Validate correct default response for evaluation exceptions** |  |
|  |  | a. Test that if exception is thrown, requests are evaluated to default response of learning = 1 and decision/filterDecision = 1.0 (i.e. control group, do not filter/high-value request) |  |
|  |  | b. Test that if no response is provided, requests are evaluated to default response of learning = 1 and decision/filterDecision = 1.0 (i.e. control group, do not filter/high-value request) |  |
| **DTE Reporting** |  |  |  |
| **6** | **Seller can provide weekly DTE reporting via S3** | 1. Seller successfully automates weekly DTE reporting | Appendix 2 |

---

# 4. Frequently Asked Questions (FAQs)

1. **Are there any types of requests that should not be evaluated by DTE?**
    1. Currently, Sellers should not evaluate requests with Programmatic Guaranteed (PG) deals, Connected TV (CTV) requests, or Native requests. All other requests should be evaluated by DTE.

2. **Will the Buyer provide this DTE Evaluator library to Seller or Seller has to build its own?**
    1. The Seller can install and use DTE Evaluator Library provided by Buyer (or will use source code as guideline). If the Seller decides to build their own version of the evaluator library or custom evaluation logic, we recommend to use the DTE evaluator library source code as a guideline and meet specifications and integration requirements in ***Section 3. DTE Integration Validation Requirements***.

3. **How much memory does the in-memory structures consume?**
    1. This primarily depend on the signal data that is being shared. The signal output file that we are planning to share would consume anywhere between 1 and 10MB. This size also depends on the variability in supply that the Seller can forward to the Buyer.

4. **What is the in-memory structure used by Config and Signal stores?**
    1. The library uses Guava Map.

5. **What if I am not on AWS, how do I access DTE cloud for signal sharing?**
    1. If you're not on AWS, you can create an account to create an IAM role. Alternatively, we can provide you with a secured AWS access key for read access to the folders. Use the .NET AWS SDK in your application to interact with the S3 folder. When making requests to the S3 folder, ensure you sign the requests using the SigV4 authorization header. This uses the provided access key to authenticate your requests.

6. **Our back-end tech does not support Java or Golang, how do I install with DTE evaluator library?**
    1. We recommend coding the evaluator library into your language of choice or alternatively, build your own custom evaluator in addition to filtering logic, following all the validation steps outlined in ***section 3***.

7. **What level of involvement is expected from Sellers to onboard DTE?**
    1. If onboarding DTE library (Java/GoLang), we estimate 1 to 2 days for permission setup and additional 1-2 weeks for completing integration with the library, including extension requirements. If building your own custom library, level of effort of approximately 1 month.
    2. We will set up weekly product and engineering sync with email support as needed.
    3. The Seller and the Buyer should agree on timeline for testing the integration.

8. **What is the latency of the evaluator?**
    1. For the current rules-based model, the latency is <1 ms.

9. **How big are the output files for the current rules-based model?**
    1. The size will depend on the distribution of supply, but we expect the model outputs to be < 1MB.

10. **What are the possible outputs of the evaluator, and what action should Sellers take for each?**
    1. `slots[*].ext.amazontest.decision = 0.0 & ext.amazontest.learning = 0`. Then `slots[*].filterDecision = 0.0`, Seller should filter the slot.
    2. `slots[*].ext.amazontest.decision = 1.0 & ext.amazontest.learning = 0`. Then `slots[*].filterDecision = 1.0`, Seller should **NOT** filter the slot.
    3. `slots[*].ext.amazontest.decision = 0.0 & ext.amazontest.learning = 1`. Then `slots[*].filterDecision = 1.0`, Seller should **NOT** filter the slot.
    4. `slots[*].ext.amazontest.decision = 1.0 & ext.amazontest.learning = 1`. Then `slots[*].filterDecision = 1.0`, Seller should **NOT** filter the slot.

---

# Appendix-1: How to establish connection with DTE cloud - Amazon Ads example

## A. IAM Role Setup for reading signal data [[ref](https://repost.aws/knowledge-center/lambda-function-assume-iam-role)]

1. Seller, in their AWS Account, to create a role that would be used in the service, e.g. `seller-dte-execution-role`
2. Amazon Ads to create a new role in the DTE Cloud AWS Account. Amazon Ads will grant access permissions to specific AWS resources for this role, e.g. `seller-dte-cloud-access-role`.
3. Seller to create or update the policy statement of their role (ex: seller-dte-execution-role), to allow assuming the role created in DTE Cloud AWS account.

```json
{
    "Version": "2012-10-17",
    "Statement": {
        "Effect": "Allow",
        "Action": "sts:AssumeRole",
        "Resource": "arn:aws:iam::<DTE-Cloud-AWS-Account-Id>:role/seller-dte-cloud-access-role"
    }
}
```

4. Amazon Ads to add the following policy statement to trust the role created in Seller's AWS account. This allows the role created in Seller's AWS account to access AWS resources in Amazon Ads AWS account.

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "AWS": "arn:aws:iam::<Seller-AWS-Account-Id>:role/seller-dte-execution-role"
            },
            "Action": "sts:AssumeRole"
        }
    ]
}
```

5. Seller to update their service's Spring/Guice config, to create a credential provider has to first assume their own role (ex: `seller-dte-execution-role`), and then using that session assume the role provided by Amazon (ex: `seller-dte-cloud-access-role`). Example below shows how to assume roles:

```java
public StsClient awsSecurityTokenService(
        AwsCredentialsProvider defaultSellerCredentialsProvider) {
    return StsClient.builder()
            .region(Region.US_WEST_2)
            .credentialsProvider(defaultSellerCredentialsProvider)
            .build();
}

public AwsCredentialsProvider stsAssumeRoleCredentialsProvider(
        StsClient stsClient,
        String assumedRoleArn) {
    return StsAssumeRoleCredentialsProvider.builder()
            .stsClient(stsClient)
            .refreshRequest(AssumeRoleRequest.builder()
                    .roleArn(assumedRoleArn)
                    .durationSeconds(3600)
                    .roleSessionName("dte_session_example")
                    .build())
            .build();
}
```

## B. IAM Role Setup for uploading reports [[ref](https://repost.aws/knowledge-center/lambda-function-assume-iam-role)]

1. Amazon Ads to create a new role in the DTE Cloud AWS Account. Amazon Ads will grant access permissions to specific AWS resources for this role, e.g. `seller-dte-cloud-reporting-role`.
2. Seller to create or update the policy statement of their role (ex: `seller-dte-execution-role`), to allow assuming the role created in DTE Cloud AWS account.

```json
{
    "Version": "2012-10-17",
    "Statement": {
        "Effect": "Allow",
        "Action": "sts:AssumeRole",
        "Resource": [
            "arn:aws:iam::<DTE-Cloud-AWS-Account-Id>:role/seller-dte-cloud-access-role",
            "arn:aws:iam::<DTE-Cloud-AWS-Account-Id>:role/seller-dte-cloud-reporting-role"
        ]
    }
}
```

3. Amazon Ads to add the following policy statement to trust the role created in Seller's AWS account. This allows the role created in Seller's AWS account to access AWS resources in Amazon Ads AWS account.

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "AWS": "arn:aws:iam::<Seller-AWS-Account-Id>:role/seller-dte-execution-role"
            },
            "Action": "sts:AssumeRole"
        }
    ]
}
```

**To upload reports to the S3 folder provided by Amazon**, the Seller has to first assume their own role (ex: `seller-dte-execution-role`), and then using that session assume the role provided by Amazon for reporting (ex: `seller-dte-cloud-reporting-role`).

---

# Appendix-2: Reporting Requirements

Please refer to Appendix-1 [*B. IAM Role Setup for uploading reports*] for how to upload the reports. We will require one csv file per datacenter (US-IAD, US-PDX, EU, FE-SIN) broken out by overall and also individual format (Banner and Video) and delivery channel (Site, App and CTV). Treatment (T) refers to requests with learning value = 0, while Control (C) refers to requests with learning value = 1.

**Amazon Ads Example Template**

See the following section (Requested Metrics and Definitions) for the complete set of requests metrics to report.

| Date | Delivery Channel + Format | Total Bid Requests Sent to Amazon | Total Spend ($) | Total Impressions | Bid Request Volume (T) | Bid Request Volume (C) | Requests Filtered by DTE Volume | DTE Filter Rate (%) | Spend per Million Ad Requests ($) | Fill Rate (%) | Fill Rate (T) | Fill Rate (C) | Spend per Million Ad Requests (T) | Spend per Million Ad Requests (C) | Bid Rate (T) | Bid Rate (C) |
|------|---------------------------|-----------------------------------|-----------------|-------------------|------------------------|------------------------|--------------------------------|---------------------|-----------------------------------|---------------|---------------|---------------|-----------------------------------|-----------------------------------|--------------|--------------|
| 2020-03-20 | Overall |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 2020-03-20 | Site Banner |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 2020-03-20 | Site Video |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 2020-03-20 | App Banner |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 2020-03-20 | App Video |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 2020-03-21 | Overall |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 2020-03-21 | Site Banner |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 2020-03-21 | Site Video |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 2020-03-21 | App Banner |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| 2020-03-21 | App Video |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| ... |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |

**Requested Metrics and Definitions**

| Metric | Definition |
|--------|------------|
| Total Bid Requests Sent to Amazon | Total bid requests forwarded to Amazon after DTE and other filters |
| Total Spend ($) | Total spend by Amazon on impressions |
| Total Impressions | Total impressions Amazon has won |
| Bid Requests Volume (T) | Volume of bid requests routed into the DTE evaluator "Treatment" group (learning=0) |
| Bid Requests Volume (C) | Volume of bid requests routed into the DTE evaluator "Control" group (learning=1) |
| Requests Filtered by DTE Volume | Volume of bid requests filtered by DTE only |
| DTE Filter Rate (%) | Bid Requests filtered by DTE / [Bid Requests Volume (T) + Bid Requests Volume (C)] |
| Spend per Million Ad Requests ($) | Total Spend / Total Bid Requests Sent to Amazon * 1000000 |
| Fill Rate (%) | Total Impressions / Total Bid Requests Sent to Amazon |
| eCPM ($) | Total Spend / Total Impressions * 1000 |
| Fill Rate (T) (%) | Total Impressions (T) / Total Bid Requests Sent to Amazon (T) |
| Fill Rate (C) (%) | Total Impressions (C) / Total Bid Requests Sent to Amazon (C) |
| Spend per Million Ad Requests (T) | Total Spend (T) / Total Bid Requests Sent to Amazon (T) * 1000000 |
| Spend per Million Ad Requests (C) | Total Spend (C) / Total Bid Requests Sent to Amazon (C) * 1000000 |
| Bid Rate (T) (%) | Bid **Responses** Volume (T) / Total Bid Requests Sent to Amazon (T) |
| Bid Rate (C) (%) | Bid **Responses** Volume (C) / Total Bid Requests Sent to Amazon (C) |

---

# Appendix-3: Dockerfile Example

For integrating DTE via a microservice, the following Dockerfile, which is used by an integrated partner, can be referenced:

```dockerfile
FROM amazoncorretto:21

WORKDIR /app/supplier-amazon-dte

COPY src/main/docker/run.sh ./
RUN chmod +x ./run.sh

COPY target/lib/ ./lib/
COPY target/supplier-amazon-dte.jar ./

# Port 50051 is being exposed for use by GRPC
EXPOSE 50051
# Port 59220 is being used for health checks
EXPOSE 59220
# Port 9400 is used for Prometheus monitoring
EXPOSE 9400

ENTRYPOINT [ "/app/supplier-amazon-dte/run.sh" ]
```

The `run.sh` executes a Main class, which initializes a DTE instance as described in Section 2.2.4:

```bash
#!/bin/sh
exec java -cp "supplier-amazon-dte.jar:lib/*" com.supplier.amazon.dte.Main
```

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.
