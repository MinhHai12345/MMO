package com.mmo.module.fb.ingest.provider.footballdatauk;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Locale;

/**
 * Downloads and validates Football-Data.co.uk CSV files.
 * Missing historical files are a normal coverage outcome; transient transport
 * failures are retried and invalid payloads are surfaced as non-retryable errors.
 */
@Component
@RequiredArgsConstructor
public class FootballDataUkCsvClient {
    private static final int MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(45);
    private static final Charset SOURCE_CHARSET = Charset.forName("windows-1252");
    private static final String CANONICAL_HOST = "https://football-data.co.uk/";

    private final WebClient.Builder webClientBuilder;

    public String getCsv(String url) {
        if (!isSupportedUrl(url)) {
            throw new FootballDataUkCsvException(
                    FootballDataUkCsvException.Reason.UNSUPPORTED_URL,
                    "Unsupported Football-Data.co.uk CSV URL: " + url);
        }

        DownloadResponse response;
        try {
            response = webClientBuilder.clone()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                    .build()
                    .get()
                    .uri(url)
                    .header(HttpHeaders.ACCEPT, "text/csv, text/plain, */*")
                    .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; MMO-Football-Data-Importer/1.0)")
                    .exchangeToMono(clientResponse -> clientResponse.bodyToMono(byte[].class)
                            .defaultIfEmpty(new byte[0])
                            .flatMap(body -> {
                                int status = clientResponse.statusCode().value();
                                String contentType = clientResponse.headers().contentType()
                                        .map(Object::toString).orElse("unknown");
                                if (status == 404 || status == 410) {
                                    return Mono.error(new FootballDataUkCsvException(
                                            FootballDataUkCsvException.Reason.NOT_AVAILABLE,
                                            "Football-Data.co.uk CSV is not published: HTTP " + status + " for " + url));
                                }
                                if (status == 429) {
                                    return Mono.error(new FootballDataUkCsvException(
                                            FootballDataUkCsvException.Reason.RATE_LIMITED,
                                            "Football-Data.co.uk rate limited request: " + url, null, true));
                                }
                                if (status >= 500) {
                                    return Mono.error(new FootballDataUkCsvException(
                                            FootballDataUkCsvException.Reason.SERVER_ERROR,
                                            "Football-Data.co.uk server returned HTTP " + status + " for " + url,
                                            null, true));
                                }
                                if (status < 200 || status >= 300) {
                                    return Mono.error(new FootballDataUkCsvException(
                                            FootballDataUkCsvException.Reason.HTTP_ERROR,
                                            "Football-Data.co.uk returned HTTP " + status + " for " + url
                                                    + "; contentType=" + contentType + "; body=" + preview(body)));
                                }
                                return Mono.just(new DownloadResponse(status, contentType, body));
                            }))
                    .timeout(TIMEOUT)
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(750))
                            .filter(this::isRetryable))
                    .block();
        } catch (FootballDataUkCsvException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new FootballDataUkCsvException(
                    FootballDataUkCsvException.Reason.NETWORK_ERROR,
                    "Failed to download Football-Data.co.uk CSV after retries: " + url,
                    exception, true);
        }

        if (response == null || response.body().length == 0) {
            throw new FootballDataUkCsvException(
                    FootballDataUkCsvException.Reason.NOT_AVAILABLE,
                    "Football-Data.co.uk returned an empty CSV response: " + url);
        }

        String csv = new String(response.body(), SOURCE_CHARSET).replace("\uFEFF", "");
        String header = csv.split("\\R", 2)[0].replace("\"", "").toUpperCase(Locale.ROOT);
        if (!header.contains("DATE") || !(header.contains("HOMETEAM") || header.contains("HOME TEAM"))
                || !(header.contains("AWAYTEAM") || header.contains("AWAY TEAM")) || !header.contains(",")) {
            throw new FootballDataUkCsvException(
                    FootballDataUkCsvException.Reason.INVALID_CSV,
                    "Football-Data.co.uk returned an invalid match CSV: " + url
                            + "; status=" + response.statusCode()
                            + "; contentType=" + response.contentType()
                            + "; body=" + preview(response.body()));
        }
        return csv;
    }

    private boolean isRetryable(Throwable exception) {
        return !(exception instanceof FootballDataUkCsvException csvException)
                || csvException.isRetryable();
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
