// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.repository.localcache.removalListener;

import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GuavaLocalCacheRemovalListenerOnLogTest {

    @Mock
    private RemovalNotification<String, Integer> mockRemovalNotification;

    private GuavaLocalCacheRemovalListenerOnLog<String, Integer> listener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new GuavaLocalCacheRemovalListenerOnLog<>();
    }

    @Test
    void testConstructor() {
        assertNotNull(listener);
    }

    @Test
    void testRemovalCauseToString_Collected() {
        assertEquals("collected", listener.removalCauseToString(RemovalCause.COLLECTED));
    }

    @Test
    void testRemovalCauseToString_Expired() {
        assertEquals("expired", listener.removalCauseToString(RemovalCause.EXPIRED));
    }

    @Test
    void testRemovalCauseToString_Size() {
        assertEquals("size", listener.removalCauseToString(RemovalCause.SIZE));
    }

    @Test
    void testRemovalCauseToString_Replaced() {
        assertEquals("replaced", listener.removalCauseToString(RemovalCause.REPLACED));
    }

    @Test
    void testRemovalCauseToString_Explicit() {
        assertEquals("explicit", listener.removalCauseToString(RemovalCause.EXPLICIT));
    }

    @Test
    void testRemovalCauseToString_AllCasesCovered() {
        // This test ensures that all RemovalCause enum values are handled
        for (RemovalCause cause : RemovalCause.values()) {
            assertDoesNotThrow(() -> listener.removalCauseToString(cause));
        }
    }
}
