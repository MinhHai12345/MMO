package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One SofaScore football page that emits a tournament catalogue and one seasons
 * response per tournament. The season endpoint is dynamic because its ID comes
 * from the catalogue response.
 */
public record SofaScoreLeaguePageSpec(
        String loadPageUrl,
        String catalogueEndpoint,
        Pattern seasonEndpointPattern) {

    public SofaScoreLeaguePageSpec {
        if (loadPageUrl == null || loadPageUrl.isBlank()) {
            throw new IllegalArgumentException("SofaScore league page URL must not be blank");
        }
        if (catalogueEndpoint == null || catalogueEndpoint.isBlank()) {
            throw new IllegalArgumentException("SofaScore catalogue endpoint must not be blank");
        }
        Objects.requireNonNull(seasonEndpointPattern, "SofaScore season endpoint pattern is required");
    }

    public Long tournamentIdFromSeasonUrl(String responseUrl) {
        if (responseUrl == null || responseUrl.isBlank()) {
            return null;
        }
        try {
            String path = URI.create(responseUrl).getRawPath();
            Matcher matcher = seasonEndpointPattern.matcher(path == null ? "" : path);
            if (!matcher.matches()) {
                return null;
            }
            return Long.valueOf(matcher.group(1));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public boolean isCatalogueUrl(String responseUrl) {
        if (responseUrl == null || responseUrl.isBlank()) {
            return false;
        }
        try {
            String path = URI.create(responseUrl).getRawPath();
            String expected = catalogueEndpoint.toLowerCase(Locale.ROOT);
            return path != null && path.toLowerCase(Locale.ROOT).equals(expected);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
