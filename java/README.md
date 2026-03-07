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

# Demand Driven Traffic Evaluator
 
This project is a Java library for evaluating demand-driven traffic based on rule-based models and experiments.
 
## Building the Package
 
To build the package, no need to have Gradle installed on your system. Follow these steps:
 
1. Get the repository:
  1. The repository is provided as the zip file through the S3 bucket, please downloaded and unzip it.
 
2. Navigate to the project directory:
 
   ```
   cd DemandDrivenTrafficEvaluator
   ```
 
3. Build the package using Gradle:
 
   ```
   ./gradlew clean build
   ```
 
Please look for the `Fat JAR created at: <the location to the Fat JAR>` in the console, and get the FAT JAR by following the provided location.
