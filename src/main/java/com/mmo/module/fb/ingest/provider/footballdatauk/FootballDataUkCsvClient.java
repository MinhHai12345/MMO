package com.mmo.module.fb.ingest.provider.footballdatauk;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;

/**
 * Downloads and validates Football-Data.co.uk CSV files.
 *
 * <p>Legacy CSV files use Windows-1252 encoding. Downloads use the canonical non-www
 * host to avoid an unnecessary redirect.</p>
 */
@Component
@RequiredArgsConstructor
public class FootballDataUkCsvClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(45);
    private static final Charset SOURCE_CHARSET = Charset.forName("windows-1252");
    private static final String CANONICAL_HOST = "https://football-data.co.uk/";

    private final WebClient.Builder webClientBuilder;

    public String getCsv(String url) {
        if (!isSupportedUrl(url)) {
            throw new IllegalArgumentException("Unsupported Football-Data.co.uk CSV URL: " + url);
        }

        DownloadResponse response;
        try {
            response = webClientBuilder.clone().build().get().uri(url)
                    .header(HttpHeaders.ACCEPT, "text/csv, text/plain, */*")
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; MMO-Football-Data-Importer/1.0)")
                    .exchangeToMono(clientResponse -> clientResponse.bodyToMono(byte[].class)
                            .defaultIfEmpty(new byte[0])
                            .map(body -> new DownloadResponse(
                                    clientResponse.statusCode().value(),
                                    clientResponse.headers().contentType()
                                            .map(Object::toString).orElse("unknown"),
                                    body)))
                    .timeout(TIMEOUT)
                    .block();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to download Football-Data.co.uk CSV: " + url, ex);
        }

        if (response == null) {
            throw new IllegalStateException("Football-Data.co.uk returned no HTTP response: " + url);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Football-Data.co.uk HTTP " + response.statusCode()
                    + " for " + url + "; contentType=" + response.contentType()
                    + "; body=" + preview(response.body()));
        }
        if (response.body().length == 0) {
            throw new IllegalStateException("Football-Data.co.uk returned an empty response: "
                    + url + "; status=" + response.statusCode()
                    + "; contentType=" + response.contentType());
        }

        String csv = new String(response.body(), SOURCE_CHARSET).replace("\uFEFF", "");
        String header = Arrays.stream(csv.split("\\R", 2))
                .findFirst().orElse("").replace("\"", "").toUpperCase(Locale.ROOT);

        if (!header.contains("DATE") || !header.contains("HOMETEAM")
                || !header.contains("AWAYTEAM") || !header.contains(",")) {
            throw new IllegalStateException("Football-Data.co.uk response is not a match CSV: "
                    + url + "; status=" + response.statusCode()
                    + "; contentType=" + response.contentType()
                    + "; body=" + preview(response.body()));
        }
        return csv;
    }

    private boolean isSupportedUrl(String url) {
        return StringUtils.hasText(url)
                && (url.startsWith(CANONICAL_HOST)
                || url.startsWith("https://www.football-data.co.uk/"));
    }

    private String preview(byte[] body) {
        String value = new String(body, SOURCE_CHARSET).replaceAll("\\s+", " ").trim();
        return value.length() <= 160 ? value : value.substring(0, 160);
    }

    private record DownloadResponse(int statusCode, String contentType, byte[] body) {
    }
}
