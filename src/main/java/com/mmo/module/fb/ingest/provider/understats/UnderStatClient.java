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

/** UnderStat AJAX endpoints and browser-compatible request headers. */
@Component
public class UnderStatClient extends AbstractProviderClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_IN_MEMORY_SIZE_BYTES = 10 * 1024 * 1024;
    private static final String ACCEPT_HEADER = "application/json, text/javascript, */*; q=0.01";
    private static final String ACCEPT_LANGUAGE =
            "vi,fr;q=0.9,vi-VN;q=0.8,fr-FR;q=0.7,en-US;q=0.6,en;q=0.5,en-AU;q=0.4";
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36";
    private static final String SEC_CH_UA =
            "\"Not:A-Brand\";v=\"8\", \"Chromium\";v=\"156\", \"Google Chrome\";v=\"156\"";

    public UnderStatClient(ProviderHttpClient httpClient, AppProperties appProperties) {
        super(appProperties, httpClient);
    }

    /** Fetches UnderStat's league/month/year catalogue from GET /getStatData. */
    public <T> T fetchStatData(TypeReference<T> responseType) {
        return fetchJson("/getStatData", "https://understat.com/", responseType);
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
        AppProperties.ProviderConfig config = providerConfig(Provider.UNDER_STATS);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.ACCEPT, ACCEPT_HEADER);
        headers.put(HttpHeaders.ACCEPT_LANGUAGE, ACCEPT_LANGUAGE);
        headers.put(HttpHeaders.CONNECTION, "keep-alive");
        headers.put(HttpHeaders.REFERER, referer);
        headers.put(HttpHeaders.USER_AGENT, configuredUserAgent(config));
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put("Sec-Fetch-Dest", "empty");
        headers.put("Sec-Fetch-Mode", "cors");
        headers.put("Sec-Fetch-Site", "same-origin");
        headers.put("sec-ch-ua", SEC_CH_UA);
        headers.put("sec-ch-ua-mobile", "?0");
        headers.put("sec-ch-ua-platform", "\"Windows\"");

        // UnderStat sessions are short-lived and user-specific. Never commit a captured cookie;
        // operators may supply it through UNDER_STAT_COOKIE when their network requires it.
        String cookie = config.getOption("cookie");
        if (StringUtils.hasText(cookie)) {
            headers.put(HttpHeaders.COOKIE, cookie.trim());
        }

        try {
            return exchange(Provider.UNDER_STATS, HttpMethod.GET, path, headers, Map.of(),
                    null, responseType, REQUEST_TIMEOUT, MAX_IN_MEMORY_SIZE_BYTES);
        } catch (ProviderHttpException ex) {
            throw new ProviderHttpException(ex.getStatusCode(), ex.getResponseHeaders(),
                    ex.getResponseBody(), "UnderStat HTTP " + ex.getStatusCode() + " for " + path
                    + ". Check endpoint availability and session/access permissions.", ex);
        } catch (ProviderResponseException ex) {
            throw new IllegalStateException("UnderStat did not return valid JSON for " + path
                    + ". The response may be an access challenge or an unavailable page.", ex);
        }
    }

    private static String configuredUserAgent(AppProperties.ProviderConfig config) {
        String configured = config.getOption("user-agent");
        return StringUtils.hasText(configured) ? configured.trim() : DEFAULT_USER_AGENT;
    }

    private static void validateSeason(int season) {
        if (season < 2014) {
            throw new IllegalArgumentException("UnderStat season must be 2014 or later");
        }
    }

    private static void validateSlug(String slug, String resource) {
        if (!StringUtils.hasText(slug) || slug.contains("/") || slug.contains("..")) {
            throw new IllegalArgumentException("A valid UnderStat " + resource + " slug is required");
        }
    }
}
