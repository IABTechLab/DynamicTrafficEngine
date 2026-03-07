// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor
@Log4j2
public class OpenRtbRequestContextMap implements OpenRtbRequestContext {

    @Setter
    private Map<String, List<String>> openRtbRequestContext;

    /**
     * Attempts to find and extract a value(s) from the MapContext using the provided path.
     * <p>
     * This method uses the path as the key to look up in the map. If the field is found,
     * its value(s) is returned as a list of string. If the field is not found or an exception occurs,
     * an singleton list with empty string is returned.
     * </p>
     *
     * @param path The OpenRTB path expression to locate the desired field.
     * @return The value(s) of the field as a list of string,
     * singleton list of "null" if the field exists but has a null value,
     * or a singleton list of empty string if the field is not found or an error occurs.
     */
    @Override
    public List<String> findPath(String path) {
        try {
            List<String> value = openRtbRequestContext.getOrDefault(path, Collections.singletonList(StringUtils.EMPTY));

            if (value == null) {
                return Collections.singletonList("null");
            }

            List<String> result = new ArrayList<>(value.size());
            for (Object item : value) {
                result.add(String.valueOf(item));
            }

            return result;
        } catch (Exception e) {
            log.info("Exception while fetching OpenRTB path {}", path, e);
            return Collections.singletonList(StringUtils.EMPTY);
        }
    }

}
