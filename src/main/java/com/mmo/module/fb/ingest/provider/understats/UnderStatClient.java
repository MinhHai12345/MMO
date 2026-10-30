package com.mmo.module.fb.ingest.provider.understats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.client.AbstractProviderClient;
import com.mmo.module.fb.ingest.provider.client.ProviderHttpClient;
import com.mmo.module.fb.ingest.provider.client.ProviderHttpException;
import com.mmo.module.fb.ingest.provider.client.ProviderResponseException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * UnderStat endpoint definitions and request headers. Shared HTTP execution and JSON decoding
 * are delegated to ProviderHttpClient; no challenge or access-control bypass is attempted.
 */
@Component
public class UnderStatClient extends AbstractProviderClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_IN_MEMORY_SIZE_BYTES = 10 * 1024 * 1024;
    private static final String ACCEPT_HEADER = "application/json, text/javascript, */*; q=0.01";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    public UnderStatClient(ProviderHttpClient httpClient, AppProperties appProperties) {
        super(appProperties, httpClient);
    }

    public <T> T fetchLeagueData(String leagueSlug, int season, TypeReference<T> responseType) {
        validateSlug(leagueSlug, "league");
        validateSeason(season);
        String path = UriComponentsBuilder.fromPath("/getLeagueData/{league}/{season}")
                .buildAndExpand(leagueSlug, season).encode().toUriString();
        String referer = UriComponentsBuilder.fromUriString("https://understat.com/league/{league}/{season}")
                .buildAndExpand(leagueSlug, season).encode().toUriString();
        return fetchJson(path, referer, responseType);
    }

    public <T> T fetchTeamData(String teamSlug, int season, TypeReference<T> responseType) {
        validateSlug(teamSlug, "team");
        validateSeason(season);
        String path = UriComponentsBuilder.fromPath("/getTeamData/{team}/{season}")
                .buildAndExpand(teamSlug, season).encode().toUriString();
        String referer = UriComponentsBuilder.fromUriString("https://understat.com/team/{team}/{season}")
                .buildAndExpand(teamSlug, season).encode().toUriString();
        return fetchJson(path, referer, responseType);
    }

    private <T> T fetchJson(String path, String referer, TypeReference<T> responseType) {
        providerConfig(Provider.UNDER_STATS);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.ACCEPT, ACCEPT_HEADER);
        headers.put(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9");
        headers.put(HttpHeaders.REFERER, referer);
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put(HttpHeaders.USER_AGENT, USER_AGENT);
        try {
            return exchange(Provider.UNDER_STATS, HttpMethod.GET, path, headers, Map.of(),
                    null, responseType, REQUEST_TIMEOUT, MAX_IN_MEMORY_SIZE_BYTES);
        } catch (ProviderHttpException ex) {
            throw new IllegalStateException("UnderStat HTTP " + ex.getStatusCode() + " for " + path
                    + ". Check endpoint availability and access permissions.", ex);
        } catch (ProviderResponseException ex) {
            throw new IllegalStateException("UnderStat did not return valid JSON for " + path
                    + ". The response may be an access challenge or an unavailable page.", ex);
        }
    }

    private static void validateSeason(int season) {
        if (season < 2014) throw new IllegalArgumentException("UnderStat season must be 2014 or later");
    }

    private static void validateSlug(String slug, String resource) {
        if (!StringUtils.hasText(slug) || slug.contains("/") || slug.contains("..")) {
            throw new IllegalArgumentException("A valid UnderStat " + resource + " slug is required");
        }
    }
}
