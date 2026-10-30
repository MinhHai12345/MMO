package com.mmo.module.fb.ingest.provider.client;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderHttpRequestTest {
    private static final TypeReference<Map<String, Object>> RESPONSE_TYPE = new TypeReference<>() { };

    @Test
    void appliesDefaultsAndKeepsOptionalNullQueryValuesImmutable() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("season", 2026);
        query.put("optional", null);

        ProviderHttpRequest<Map<String, Object>> request = new ProviderHttpRequest<>(
                HttpMethod.GET, "https://example.test/fixtures", Map.of("Accept", "application/json"),
                query, null, RESPONSE_TYPE, null, 0);

        assertThat(request.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(request.maxInMemorySize()).isPositive();
        assertThat(request.queryParams()).containsEntry("season", 2026).containsEntry("optional", null);
        assertThatThrownBy(() -> ((Map) request.queryParams()).put("page", 1))
                .isInstanceOf(UnsupportedOperationException.class);
        query.put("page", 1);
        assertThat(request.queryParams()).doesNotContainKey("page");
    }

    @Test
    void rejectsInvalidUrisAndNonPositiveTimeouts() {
        assertThatThrownBy(() -> request("relative/path", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request("ftp://example.test/resource", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request("https://example.test/resource", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidHeaders() {
        assertThatThrownBy(() -> new ProviderHttpRequest<>(HttpMethod.GET,
                "https://example.test", Map.of(" ", "value"), Map.of(), null, RESPONSE_TYPE, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ProviderHttpRequest<Map<String, Object>> request(String uri, Duration timeout) {
        return new ProviderHttpRequest<>(HttpMethod.GET, uri, Map.of(), Map.of(),
                null, RESPONSE_TYPE, timeout, 1024);
    }
}
