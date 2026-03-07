// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OpenRtbRequestContextMapTest {

    private OpenRtbRequestContextMap contextMap;
    private Map<String, List<String>> testMap;

    @BeforeEach
    void setUp() {
        contextMap = new OpenRtbRequestContextMap();
        testMap = new HashMap<>();
        testMap.put("key1", Collections.singletonList("value1"));
        testMap.put("key2", Collections.singletonList("value2"));
        testMap.put("key3", Arrays.asList("value3", "value4", "value5"));
        testMap.put("nullKey", null);
        testMap.put("nullKeyList", Collections.singletonList(null));
        contextMap.setOpenRtbRequestContext(testMap);
    }

    @Test
    void testFindPathExistingKey() {
        assertEquals(Collections.singletonList("value1"), contextMap.findPath("key1"));
    }

    @Test
    void testFindPathExistingKeyList() {
        assertEquals(Arrays.asList("value3", "value4", "value5"), contextMap.findPath("key3"));
    }

    @Test
    void testFindPathNonExistingKey() {
        assertEquals(Collections.singletonList(""), contextMap.findPath("nonExistentKey"));
    }

    @Test
    void testFindPathNullValue() {
        assertEquals(Collections.singletonList("null"), contextMap.findPath("nullKey"));
    }

    @Test
    void testFindPathNullValueList() {
        assertEquals(Collections.singletonList("null"), contextMap.findPath("nullKeyList"));
    }

    @Test
    void testFindPathEmptyKey() {
        assertEquals(Collections.singletonList(""), contextMap.findPath(""));
    }

    @Test
    void testFindPathNullKey() {
        assertEquals(Collections.singletonList(""), contextMap.findPath(null));
    }

    @Test
    void testFindPathWithNullMap() {
        contextMap.setOpenRtbRequestContext(null);
        assertEquals(Collections.singletonList(""), contextMap.findPath("anyKey"));
    }

    @Test
    void testNoArgsConstructor() {
        OpenRtbRequestContextMap newContextMap = new OpenRtbRequestContextMap();
        assertNotNull(newContextMap);
    }

    @Test
    void testSetterMethod() {
        Map<String, List<String>> newMap = new HashMap<>();
        newMap.put("newKey", Collections.singletonList("newValue"));
        contextMap.setOpenRtbRequestContext(newMap);
        assertEquals(Collections.singletonList("newValue"), contextMap.findPath("newKey"));
    }
}
