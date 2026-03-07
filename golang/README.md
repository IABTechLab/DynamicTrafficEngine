Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License").
You may not use this file except in compliance with the License.
You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

# Dynamic Traffic Engine (Demand-Driven Traffic Evaluator)
Dynamic Traffic Engine (Evaluator) is a Golang library that contains functionality to provide filtering recommendations based on a given model. The features in the model are expected to be derived from the OpenRTB request that SSPs send to DSPs. The Evaluator periodically pulls model and config files from a cloud or local location to evaluate SSP bid requests and provide a filtering recommendation to the SSP. The SSP then uses this information to decide which traffic to send to the DSPs. This document contains information on how the Evaluator library is structured, and also provides instructions on how an SSP can integrate with the library to receive the filter recommendations.

## Getting Started
### Task Initialization
Initializing three periodic tasks in the goroutines to load the model configurations, experiment configurations, and model results into the local cache every `<schedule-period>`.
```go
demanddriventrafficevaluator.NewTaskInitializer("<supplier-name>", &<credentials>, ,"<aws-region>". "<folder-prefix/bucket-name>", <schedule-period>).Init()
```
1. `<supplier-name>` is the SSP name. E.g. `ssp`.
2. `<credentials>` is a reference to the AWS Credentials Provider for a role that has permissions to access the S3 bucket, if needed. 
3. `<aws-region>` is the region the S3 region of the S3 bucket. E.g. `us-east-1`.
4. `<folder-prefix/bucket-name>` is the local path prefix or the bucket prefix where the configuration data and model data is stored. E.g. `local/path/to/metadata` or `s3://experiment-traffic-shaping-us-east-1`.
5. `<schedule-period>` is the period (in milliseconds) of the scheduled tasks. e.g. 300000 checks for new configuration and model data every 5 minutes.

Make sure to initialize the tasks before the service is up.

### OpenRTBRequest Evaluation
This is used by the SSP service to receive bid/no-bid recommendations. We can then invoke the `Evaluate` method on that instance, before request is forwarded to Amazon DSP. This call will provide recommendations, on whether the request should be forwarded to the Amazon DSP, as well as extensions to append to requests forwarded to Amazon DSP.
```go
requestEvaluator := demanddriventrafficevaluator.NewRequestEvaluator("<supplier-name>", &<credentials>, "aws-region", "<folder-prefix/bucket-name>")

requestOutput := requestEvaluator.Evaluate(&evaluation.BidRequestEvaluatorInput{
    OpenRtbRequest: "<open-rtb-request-string>",
    OpenRtbRequestMap: <map[string]string>
})
```

1. `<supplier-name>` is the SSP name. E.g. `ssp`.
2. `<credentials>` is a reference to the AWS Credentials Provider for a role that has permissions to access the S3 bucket, if needed. 
3. `<aws-region>` is the region the S3 region of the S3 bucket. E.g. `us-east-1`.
4. `<folder-prefix/bucket-name>` is the local path prefix or the bucket prefix where the configuration data and model data is stored. E.g. `local/path/to/metadata` or `s3://experiment-traffic-shaping-us-east-1`.

The Evaluate function takes a reference to a `BidRequestEvaluatorInput` object, which requires at least 1 of `OpenRtbRequest` or `OpenRtbRequestMap` to be populated.

1. `OpenRtbRequest` is a string of the raw OpenRTB request, in JSON format. 
2. `OpenRtbRequestMap` is an abridged OpenRTB request, as a Map of string -> string. The keys are the path of the field, in dot notation described in JsonPath, and the values are the the string value of the field.

## API Specifications
| Response Body Field | Type                       | Description<br>                                                                                                                                                                                                                                                                                                                                                           |
| ------------------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| response            | array of objects, required | Filter recommendation for the Amazon DSP.                                                                                                                                                                                                                                                                                                                                 |
| Responses Object    |
| slots (imps)        | array of objects, required | Evaluation of models for each slot (imp object) of the incoming bid request                                                                                                                                                                                                                                                                                               |
| ext                 | string, optional           | This is a json blob that we expect SSP partners to add to the `ext` field in the root level object of the oRTB request that they forward to Amazon DSP. This optional field contains information on whether the evaluator internally assigned the request to treatment (_learning=0_) or control (_learning=1_).<br><br>Example value:<br>`"amazontest": {"learning": 1}` |
| Slots Object        |
| filterDecision      | float, required            | Recommended filter decision for the slot based on DSP's model(s). This is a value ranging from 0.0 to 1.0, where 0.0 indicates no probability of getting response from DSP, and 1.0 indicates highest probability to get a response from DSP.                                                                                                                             |
| reasonCode          | string, optional           | Future extension to provide more information on how we arrived at the filter recommendation.                                                                                                                                                                                                                                                                              |
| signals             | array of objects, optional | More detailed information regarding executed signals                                                                                                                                                                                                                                                                                                                      |
| ext                 | string, optional           | This is a json blob that we expect SSP partners to add to the `ext` field in the `imp` object of the oRTB request that they forward to Amazon DSP. This optional field contains information about the decision taken by the evaluator internally.<br><br>Example value:<br>`"amazontest": {"decision": 0.0}`                                                              |
| Signals Object      |
| name                | string                     | Name of the signal                                                                                                                                                                                                                                                                                                                                                        |
| version             | int                        | Version of the signal that was evaluated for this slot                                                                                                                                                                                                                                                                                                                    |
| status              | string                     | Status of signal execution, one of SUCCESS, ERROR, TIMEOUT                                                                                                                                                                                                                                                                                                                |
| debugInfo           | string                     | Debug logs for the signal                                                                                                                                                                                                                                                                                                                                                 |
|                     |                            |                                                                                                                                                                                                                                                                                                                                                                           |