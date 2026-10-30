package com.mmo.module.fb.ingest.provider.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderHttpClientRetryPolicyTest {

    @Test
    void retriesTransientConnectionFailuresForSafeReadRequests() {
        WebClientRequestException failure = new WebClientRequestException(
                new RuntimeException("connection prematurely closed"),
                HttpMethod.GET,
                URI.create("https://understat.com/getLeagueData/Bundesliga/2024"),
                HttpHeaders.EMPTY
        );

        assertThat(ProviderHttpClient.isRetryableTransportFailure(HttpMethod.GET, failure)).isTrue();
        assertThat(ProviderHttpClient.isRetryableTransportFailure(HttpMethod.HEAD, failure)).isTrue();
        assertThat(ProviderHttpClient.isRetryableTransportFailure(HttpMethod.OPTIONS, failure)).isTrue();
    }

    @Test
    void retriesTimeoutsButDoesNotRetryUnsafeMethodsOrApplicationFailures() {
        assertThat(ProviderHttpClient.isRetryableTransportFailure(
                HttpMethod.GET, new TimeoutException("request timed out"))).isTrue();
        assertThat(ProviderHttpClient.isRetryableTransportFailure(
                HttpMethod.POST, new TimeoutException("request timed out"))).isFalse();
        assertThat(ProviderHttpClient.isRetryableTransportFailure(
                HttpMethod.GET, new IllegalArgumentException("invalid request"))).isFalse();
    }
}
