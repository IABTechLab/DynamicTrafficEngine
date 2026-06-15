// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DomainNameUtilTest {

    @ParameterizedTest
    @CsvSource({
            // page input, expected registered domain
            "https://www.example.com/page,         example.com",
            "http://example.com,                   example.com",
            "example.com,                          example.com",
            "//www.example.com,                    example.com",
            "https://news.example.co.uk/article,   example.co.uk",
            "https://www2.example.com,             example.com",
            "https://myblog.wordpress.com/post,    myblog.wordpress.com",
            "EXAMPLE.COM,                          example.com",
            "example.com/path?q=1&x=2,             example.com",
    })
    void resolveRegisteredDomain_fromPage(String page, String expected) {
        assertEquals(expected, DomainNameUtil.resolveRegisteredDomain(page, null));
    }

    @Test
    void resolveRegisteredDomain_fallsBackToDomainWhenPageInvalid() {
        assertEquals("example.com", DomainNameUtil.resolveRegisteredDomain("", "www.example.com"));
        assertEquals("example.com", DomainNameUtil.resolveRegisteredDomain(null, "example.com"));
    }

    @Test
    void resolveRegisteredDomain_pageWins_overDomain() {
        assertEquals("page-domain.com",
                DomainNameUtil.resolveRegisteredDomain("https://page-domain.com/x", "fallback.com"));
    }

    @Test
    void resolveRegisteredDomain_rejectsIpAddresses() {
        assertNull(DomainNameUtil.resolveRegisteredDomain("http://192.168.1.1/x", null));
    }

    @Test
    void resolveRegisteredDomain_rejectsGarbageAndBlanks() {
        assertNull(DomainNameUtil.resolveRegisteredDomain(null, null));
        assertNull(DomainNameUtil.resolveRegisteredDomain("", ""));
        assertNull(DomainNameUtil.resolveRegisteredDomain("android-app", null));
        assertNull(DomainNameUtil.resolveRegisteredDomain("no-public-suffix-here", null));
    }
}
