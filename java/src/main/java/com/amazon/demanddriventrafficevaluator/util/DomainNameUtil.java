// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.util;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.InternetDomainName;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Utility for deriving the model lookup key (a "registered domain") from an
 * OpenRTB site URL, using Guava's {@link InternetDomainName} (Public Suffix List).
 */
public final class DomainNameUtil {

    // Matches www-like prefixes on a hostname: www., ww., www2., w3., etc.
    private static final Pattern REGEX_WWW_PREFIX = Pattern.compile("^w+\\d*\\.");

    // Matches www-like subdomain labels: www, ww, www2, w3, etc.
    private static final Pattern REGEX_WWW09 = Pattern.compile("^w+\\d*$");

    // Matches IP-like strings (IPv4 dotted notation and IPv6 colon notation).
    private static final Pattern REGEX_IPADDR = Pattern.compile("^[0-9:\\.]+$");

    // Domain-specific overrides for how many subdomain levels to include in the registered
    // domain. e.g. "wordpress.com" -> 2 keeps up to 2 levels beyond the registered domain
    // (myblog.wordpress.com instead of just wordpress.com).
    private static final Map<String, Integer> MAX_SITENAME_PARTS = ImmutableMap.<String, Integer>builder()
            .put("go.com", 2)
            .put("wordpress.com", 2)
            .put("blogspot.com", 2)
            .put("yahoo.com", 1)
            .put("msn.com", 1)
            .put("aol.com", 1)
            .put("google.com", 1)
            .put("cnn.com", 1)
            .build();
    private static final int DEFAULT_MAX_SITENAME_PARTS = 0;

    private DomainNameUtil() {
        // Utility class — prevent instantiation
    }

    /**
     * Derives the registered domain (model lookup key) from a site URL or bare domain.
     * <p>
     * Tries the supplied {@code page} first (the full page URL), falling back to {@code domain}.
     * Normalizes the hostname, decomposes it against the Public Suffix List, and builds the
     * registered domain with any well-known multi-level overrides applied.
     * </p>
     *
     * @param page   value of OpenRTB {@code site.page} (may be null/blank/invalid)
     * @param domain value of OpenRTB {@code site.domain} (fallback; may be null/blank/invalid)
     * @return the registered domain (e.g. "example.com", "myblog.wordpress.com"), or null if
     *         neither input yields a valid domain under a recognized public suffix
     */
    public static String resolveRegisteredDomain(String page, String domain) {
        // Priority 1: site.page
        String hostname = extractHostname(page);
        String[] parts = hostname != null ? getDomainNameParts(hostname) : null;

        // Priority 2: site.domain (fallback if page absent or invalid)
        if (parts == null) {
            hostname = extractHostname(domain);
            parts = hostname != null ? getDomainNameParts(hostname) : null;
        }

        if (parts == null || parts.length < 2) {
            return null;
        }
        return buildRegisteredDomain(parts);
    }

    /**
     * Extracts and normalizes a hostname from a domain, URL, or URL-like string.
     * Strips scheme, lowercases, rejects IP addresses, and removes www-like prefixes.
     *
     * @param input a domain name, URL, or URL-like string; may be null
     * @return normalized hostname, or null for blank input, IPs, or unparseable strings
     */
    static String extractHostname(String input) {
        if (StringUtils.isBlank(input)) {
            return null;
        }

        // java.net.URL requires a scheme
        String urlStr = input.trim();
        if (!urlStr.startsWith("http")) {
            if (urlStr.startsWith("//")) {
                urlStr = "http://" + urlStr.substring(2);
            } else {
                urlStr = "http://" + urlStr;
            }
        }

        try {
            URL url = new URL(urlStr);
            String host = url.getHost();
            if (StringUtils.isBlank(host)) {
                return null;
            }
            host = host.toLowerCase();
            if (REGEX_IPADDR.matcher(host).matches()) {
                return null;
            }
            return stripWwwPrefix(host);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private static String stripWwwPrefix(String host) {
        Matcher wwwMatcher = REGEX_WWW_PREFIX.matcher(host);
        if (wwwMatcher.find()) {
            return host.substring(wwwMatcher.end());
        }
        return host;
    }

    /**
     * Decomposes a domain name relative to its public suffix.
     * Returns [suffix, registeredLabel, subdomain1, subdomain2, ...] (subdomains outermost-first),
     * e.g. "news.example.co.uk" -> ["co.uk", "example", "news"]; or null if the domain is invalid
     * or not under a recognized public suffix.
     */
    static String[] getDomainNameParts(String domainName) {
        if (domainName == null || !InternetDomainName.isValid(domainName)) {
            return null;
        }

        InternetDomainName domain = InternetDomainName.from(domainName);
        if (!domain.isUnderPublicSuffix()) {
            return null;
        }

        List<String> domainParts = new ArrayList<>();
        InternetDomainName publicSuffix = domain.publicSuffix();
        domainParts.add(publicSuffix.toString());

        int totalDomainPartsLen = domain.parts().size();
        domainParts.addAll(domain.parts().subList(0, totalDomainPartsLen - publicSuffix.parts().size()).reverse());

        return domainParts.toArray(new String[0]);
    }

    /**
     * Builds the registered domain from PSL parts, applying MAX_SITENAME_PARTS overrides for
     * well-known multi-level domains and skipping www-like outermost labels.
     */
    private static String buildRegisteredDomain(String[] parts) {
        String registeredDomain = parts[1] + "." + parts[0];

        Integer nPartOverride = MAX_SITENAME_PARTS.get(registeredDomain);
        int nParts = nPartOverride != null ? nPartOverride : DEFAULT_MAX_SITENAME_PARTS;
        if (nParts > 0) {
            StringBuilder extendedDomain = new StringBuilder();
            for (int i = Math.min(nParts + 1, parts.length - 1); i > 1; i--) {
                // Skip www-like labels at the outermost position
                if (i == parts.length - 1 && REGEX_WWW09.matcher(parts[i]).matches()) {
                    continue;
                }
                extendedDomain.append(parts[i]).append(".");
            }
            extendedDomain.append(registeredDomain);
            registeredDomain = extendedDomain.toString();
        }

        return registeredDomain;
    }
}
