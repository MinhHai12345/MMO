package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Describes a fixed or dynamic API response expected from a SofaScore page navigation. */
public record SofaScoreEndpointSpec(
        String endpoint,
        Class<?> responseType,
        boolean required,
        Pattern urlPattern) {

    public SofaScoreEndpointSpec(String endpoint, Class<?> responseType, boolean required) {
        this(endpoint, responseType, required, null);
    }

    public SofaScoreEndpointSpec {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("SofaScore endpoint must not be blank");
        }
        Objects.requireNonNull(responseType, "SofaScore response type is required");
        endpoint = endpoint.trim();
    }

    public static SofaScoreEndpointSpec required(String endpoint, Class<?> responseType) {
        return new SofaScoreEndpointSpec(endpoint, responseType, true);
    }

    public static SofaScoreEndpointSpec optional(String endpoint, Class<?> responseType) {
        return new SofaScoreEndpointSpec(endpoint, responseType, false);
    }

    /** Dynamic endpoint: zero or more response URLs may match this pattern. */
    public static SofaScoreEndpointSpec pattern(String regex, Class<?> responseType) {
        return new SofaScoreEndpointSpec(regex, responseType, false,
                Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    public boolean dynamic() {
        return urlPattern != null;
    }

    public boolean matchesUrl(String responseUrl) {
        String responsePath = responsePath(responseUrl);
        if (responsePath == null) {
            return false;
        }
        return dynamic()
                ? urlPattern.matcher(responsePath).matches()
                : responsePath.endsWith(normalizePath(endpoint));
    }

    public Long captureLong(String responseUrl, int group) {
        if (!dynamic()) {
            return null;
        }
        try {
            Matcher matcher = urlPattern.matcher(responsePath(responseUrl));
            if (!matcher.matches() || group < 1 || group > matcher.groupCount()) {
                return null;
            }
            return Long.valueOf(matcher.group(group));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String responsePath(String responseUrl) {
        if (responseUrl == null || responseUrl.isBlank()) {
            return null;
        }
        try {
            return normalizePath(URI.create(responseUrl).getRawPath());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return "/";
        }
        String path = value.trim();
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path.toLowerCase(Locale.ROOT);
    }
}
