// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import com.jayway.jsonpath.DocumentContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor
@Log4j2
public class OpenRtbRequestContextJsonDocument implements OpenRtbRequestContext {

    @Setter
    private DocumentContext openRtbRequestContext;

    /**
     * Attempts to find and extract a value(s) from the DocumentContext using the provided path.
     * Multiple values can be returned for a single path as since we may want to look up all values
     * in the cache (e.g. check for a high priority dealID across all dealIDs in a given request.
     * <p>
     * This method uses JsonPath to navigate the JSON structure. If the field is found,
     * its value(s) is returned as a list of string. If the field is not found or an exception occurs,
     * a singleton list with empty string is returned.
     * </p>
     *
     * @param path The OpenRTB path expression to locate the desired field.
     * @return The value of the field(s) as a list of string,
     * singleton list with entry "null" if the field exists but has a null value,
     * or a singleton list with entry empty string if the field is not found or an error occurs.
     */
    @Override
    public List<String> findPath(String path) {
        try {
            List<?> value = openRtbRequestContext.read(path);

            if (value == null) {
                return Collections.singletonList("null");
            }

            List<String> result = new ArrayList<>(value.size());
            for (Object item : value) {
                result.add(Objects.toString(item));
            }

            return result;
        } catch (Exception e) {
            log.info("Exception while fetching OpenRTB path {}", path, e);
            return Collections.singletonList(StringUtils.EMPTY);
        }
    }

}
