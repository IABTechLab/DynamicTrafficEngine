// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.evaluation.evaluator;

import static com.amazon.demanddriventrafficevaluator.evaluation.evaluator.BidRequestEvaluatorOnRuleBasedModel.DOCUMENT_CONFIGURATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OpenRtbRequestContextJsonDocumentTest {
    private OpenRtbRequestContextJsonDocument contextDocument;
    private String testString;
    private String nestedTestString;

    @BeforeEach
    void setUp() {
        contextDocument = new OpenRtbRequestContextJsonDocument();
        nestedTestString = "\"key4\": [{\"key3\": \"list1\"}, {\"key3\": \"list2\"}, {\"key3\": \"list3\"}]";
        testString = "{\"key1\": \"value1\", \"key2\": \"value2\", \"nullKey\": null," + nestedTestString + "}";
        contextDocument.setOpenRtbRequestContext(JsonPath.parse(testString, DOCUMENT_CONFIGURATION));
    }

    @Test
    void testFindPathExistingKey() {
        assertEquals(List.of("value1"), contextDocument.findPath("$.key1"));
    }

    @Test
    void testFindPathExistingKeyList() {
        assertEquals(List.of("list1", "list2", "list3"), contextDocument.findPath("$.key4[*].key3"));
    }

    @Test
    void testFindPathNonExistingKey() {
        assertEquals(List.of(""), contextDocument.findPath("$.nonExistentKey"));
    }

    @Test
    void testFindPathNullValue() {
        assertEquals(List.of("null"), contextDocument.findPath("$.nullKey"));
    }

    @Test
    void testFindPathEmptyKey() {
        assertEquals(List.of(""), contextDocument.findPath(""));
    }

    @Test
    void testFindPathNullKey() {
        assertEquals(List.of(""), contextDocument.findPath(null));
    }

    @Test
    void testFindPathWithNullContext() {
        contextDocument.setOpenRtbRequestContext(null);
        assertEquals(List.of(""), contextDocument.findPath("$.anyKey"));
    }

    @Test
    void testNoArgsConstructor() {
        OpenRtbRequestContextJsonDocument newContextDocument = new OpenRtbRequestContextJsonDocument();
        assertNotNull(newContextDocument);
    }

    @Test
    void testSetterMethod() {
        String newString = "{\"newKey\": \"newValue\"}";
        contextDocument.setOpenRtbRequestContext(JsonPath.parse(newString, DOCUMENT_CONFIGURATION));
        assertEquals(List.of("newValue"), contextDocument.findPath("$.newKey"));
    }
}
