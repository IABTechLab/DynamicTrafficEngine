// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature.transformer;

import com.amazon.demanddriventrafficevaluator.modelfeature.ModelFeature;
import com.amazon.demanddriventrafficevaluator.util.DomainNameUtil;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * A transformer that resolves the single lookup key for a request, choosing between the app
 * bundle and the web registered domain.
 * <p>
 * This transformer owns the full selection-and-normalization chain, so it should be the only
 * transformer on its feature (do not pair it with {@code GetFirstNotEmpty}). It expects the
 * feature's extracted values to arrive positionally, in the order the feature's {@code fields}
 * are configured:
 * <pre>
 *   "fields": ["$.app.bundle", "$.site.page", "$.site.domain"]
 *   "transformation": ["DomainOrBundleKey"]
 * </pre>
 * so {@code values = [bundle, page, domain]}.
 * </p>
 * <p>
 * Resolution priority:
 * <ol>
 *   <li>If {@code app.bundle} is non-empty, use it as-is (app traffic).</li>
 *   <li>Else if {@code site.page} yields a registered domain (Public Suffix List), use that.</li>
 *   <li>Else if {@code site.domain} yields a registered domain, use that.</li>
 *   <li>Else fall back to the raw {@code site.domain} if non-empty, otherwise the raw
 *       {@code site.page} (or empty string if neither is present).</li>
 * </ol>
 * </p>
 */
public class DomainOrBundleKey implements Transformer {

    public DomainOrBundleKey() {
    }

    @Override
    public ModelFeature transform(ModelFeature modelFeature) {
        List<String> values = modelFeature.getValues();
        String bundle = valueAt(values, 0);
        String page = valueAt(values, 1);
        String domain = valueAt(values, 2);

        String key = resolveKey(bundle, page, domain);

        return ModelFeature.builder()
                .configuration(modelFeature.getConfiguration())
                .values(List.of(key))
                .build();
    }

    private String resolveKey(String bundle, String page, String domain) {
        // 1. App bundle wins, used verbatim.
        if (StringUtils.isNotEmpty(bundle)) {
            return bundle;
        }

        // 2 & 3. Registered domain from site.page, falling back to site.domain (page-first).
        String registeredDomain = DomainNameUtil.resolveRegisteredDomain(page, domain);
        if (registeredDomain != null) {
            return registeredDomain;
        }

        // 4. Nothing extractable: raw site.domain, else raw site.page, else empty.
        if (StringUtils.isNotEmpty(domain)) {
            return domain;
        }
        if (StringUtils.isNotEmpty(page)) {
            return page;
        }
        return StringUtils.EMPTY;
    }

    /**
     * Returns the value at {@code index}, or empty string if the list is too short or the entry
     * is null. {@code JsonExtractor} may emit "null" for a present-but-null field; treat that as
     * empty too so it does not become a lookup key.
     */
    private String valueAt(List<String> values, int index) {
        if (values == null || index >= values.size()) {
            return StringUtils.EMPTY;
        }
        String value = values.get(index);
        if (value == null || "null".equals(value)) {
            return StringUtils.EMPTY;
        }
        return value;
    }
}
