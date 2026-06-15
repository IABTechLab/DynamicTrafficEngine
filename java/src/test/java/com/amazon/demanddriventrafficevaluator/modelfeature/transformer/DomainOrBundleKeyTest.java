// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature.transformer;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;
import com.amazon.demanddriventrafficevaluator.repository.entity.FeatureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainOrBundleKeyTest {

    private DomainOrBundleKey transformer;
    private FeatureConfiguration configuration;

    @BeforeEach
    void setUp() {
        transformer = new DomainOrBundleKey();
        configuration = new FeatureConfiguration();
    }

    /** values are positional: [bundle, page, domain]. */
    private ModelFeature feature(String bundle, String page, String domain) {
        return ModelFeature.builder()
                .configuration(configuration)
                .values(Arrays.asList(bundle, page, domain))
                .build();
    }

    private String key(String bundle, String page, String domain) {
        return transformer.transform(feature(bundle, page, domain)).getValues().get(0);
    }

    @Test
    void step1_appBundleWins_evenWhenSitePresent() {
        assertEquals("com.fitnow.loseit",
                key("com.fitnow.loseit", "https://www.accuweather.com/x", "www.accuweather.com"));
    }

    @Test
    void step2_registeredDomainFromPage() {
        assertEquals("accuweather.com",
                key("", "https://www.accuweather.com/en/us/x", "ignored-because-page-extracts.com"));
    }

    @Test
    void step3_fallsBackToDomainWhenPageNotExtractable() {
        // page is non-empty but not a real domain -> must fall through to site.domain
        assertEquals("accuweather.com",
                key("", "android-app://com.foo.bar", "www.accuweather.com"));
    }

    @Test
    void step4_rawDomainWhenNeitherExtracts() {
        // neither page nor domain is a valid registered domain -> raw site.domain
        assertEquals("not-a-real-tld-xyz",
                key("", "also-bad", "not-a-real-tld-xyz"));
    }

    @Test
    void step4_rawPageWhenDomainEmptyAndNeitherExtracts() {
        assertEquals("bad-page-value",
                key("", "bad-page-value", ""));
    }

    @Test
    void step4_emptyWhenNothingPresent() {
        assertEquals("", key("", "", ""));
    }

    @Test
    void nullBundleTreatedAsAbsent() {
        // JsonExtractor emits "null" for present-but-null fields
        assertEquals("accuweather.com",
                key("null", "https://www.accuweather.com/x", ""));
    }

    @Test
    void handlesShortValuesList() {
        // Only bundle provided (list length 1) — should not throw, returns bundle.
        ModelFeature f = ModelFeature.builder()
                .configuration(configuration)
                .values(List.of("com.example.app"))
                .build();
        assertEquals("com.example.app", transformer.transform(f).getValues().get(0));
    }

    @Test
    void handlesEmptyValuesList() {
        ModelFeature f = ModelFeature.builder()
                .configuration(configuration)
                .values(Collections.emptyList())
                .build();
        assertEquals("", transformer.transform(f).getValues().get(0));
    }

    @Test
    void preservesConfiguration() {
        ModelFeature result = transformer.transform(feature("", "https://www.example.com", ""));
        assertEquals(configuration, result.getConfiguration());
    }
}
