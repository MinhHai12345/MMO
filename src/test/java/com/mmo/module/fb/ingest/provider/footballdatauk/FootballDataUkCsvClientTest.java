package com.mmo.module.fb.ingest.provider.footballdatauk;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FootballDataUkCsvClientTest {

    @Test
    void classifiesMissingHistoricalFileAsNotAvailable() {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).body("not found").build()));
        FootballDataUkCsvClient client = new FootballDataUkCsvClient(builder);

        FootballDataUkCsvException exception = assertThrows(FootballDataUkCsvException.class,
                () -> client.getCsv("https://football-data.co.uk/mmz4281/1415/E0.csv"));

        assertEquals(FootballDataUkCsvException.Reason.NOT_AVAILABLE, exception.getReason());
        assertFalse(exception.isRetryable());
    }

    @Test
    void retriesRateLimitThenReturnsValidCsv() {
        AtomicInteger requests = new AtomicInteger();
        String csv = "Date,HomeTeam,AwayTeam,HS\n01/08/14,Arsenal,Chelsea,2\n";
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            if (requests.getAndIncrement() == 0) {
                return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).body("slow down").build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "text/csv")
                    .body(csv).build());
        });
        FootballDataUkCsvClient client = new FootballDataUkCsvClient(builder);

        assertEquals(csv, client.getCsv("https://football-data.co.uk/mmz4281/1415/E0.csv"));
        assertEquals(2, requests.get());
    }

    @Test
    void classifiesHtmlChallengePageAsInvalidCsv() {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "text/html")
                        .body("<html>Access denied</html>").build()));
        FootballDataUkCsvClient client = new FootballDataUkCsvClient(builder);

        FootballDataUkCsvException exception = assertThrows(FootballDataUkCsvException.class,
                () -> client.getCsv("https://football-data.co.uk/mmz4281/1415/E0.csv"));

        assertEquals(FootballDataUkCsvException.Reason.INVALID_CSV, exception.getReason());
    }
}
