// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import java.util.List;

public interface OpenRtbRequestContext {

    /**
     * Fetches the string value(s) based on the provided OpenRTB path.
     * If the field is found, its value(s) is returned as a string.
     * If the field is not found or an exception occurs,
     * a singleton of empty string is returned.
     *
     * @param path The OpenRTB path expression to locate the desired field.
     *
     * @return The value(s) of the field as a list of string,
     * singleton list with entry "null" if the field exists but has a null value,
     * or a singleton list with entry empty string if the field is not found or an error occurs.
     */
    List<String> findPath(String path);

}
