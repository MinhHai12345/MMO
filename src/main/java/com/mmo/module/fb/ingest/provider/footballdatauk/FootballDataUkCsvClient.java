package com.mmo.module.fb.ingest.provider.footballdatauk;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/** Downloads CSV content directly from the configured Football-Data.co.uk URL. */
@Component
@RequiredArgsConstructor
public class FootballDataUkCsvClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(45);
    private final WebClient.Builder webClientBuilder;

    public String getCsv(String url) {
        if (!StringUtils.hasText(url) || !url.startsWith("https://www.football-data.co.uk/")) {
            throw new IllegalArgumentException("Unsupported Football-Data.co.uk CSV URL: " + url);
        }
        try {
            String csv = webClientBuilder.clone().build().get().uri(url)
                    .header(HttpHeaders.ACCEPT, "text/csv, text/plain, */*")
                    .header(HttpHeaders.USER_AGENT, "MMO-Football-Data-Importer/1.0")
                    .retrieve().bodyToMono(String.class).timeout(TIMEOUT).block();
            if (!StringUtils.hasText(csv) || !csv.contains(",")) {
                throw new IllegalStateException("Football-Data.co.uk returned an empty or invalid CSV: " + url);
            }
            return csv.replace("\uFEFF", "");
        } catch (WebClientResponseException ex) {
            throw new IllegalStateException("Football-Data.co.uk HTTP " + ex.getStatusCode().value()
                    + " for " + url, ex);
        }
    }
}
