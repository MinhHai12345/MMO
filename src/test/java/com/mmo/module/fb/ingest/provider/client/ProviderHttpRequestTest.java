package com.mmo.module.fb.ingest.provider.client;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderHttpRequestTest {
    private static final TypeReference<Map<String, Object>> RESPONSE_TYPE = new TypeReference<>() {};

    @Test
    void appliesSafeDefaultsAndKeepsOptionalNullQueryValues() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("season", 2026);
        query.put("optional", null);

        ProviderHttpRequest<Map<String, Object>> request = new ProviderHttpRequest<>(
                HttpMethod.GET, "https://example.test/fixtures", Map.of("x-api-key", "secret"),
                query, null, RESPONSE_TYPE, null, 0);

        assertThat(request.timeout()).hasSeconds(30);
        assertThat(request.maxInMemorySize()).isPositive();
        assertThat(request.queryParams()).containsEntry("season", 2026).containsEntry("optional", null);
        assertThatThrownBy(() -> request.queryParams().put("page", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requiresMethodUriAndResponseType() {
        assertThatThrownBy(() -> new ProviderHttpRequest<>(null, "https://example.test",
                Map.of(), Map.of(), null, RESPONSE_TYPE, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderHttpRequest<>(HttpMethod.GET, " ",
                Map.of(), Map.of(), null, RESPONSE_TYPE, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderHttpRequest<>(HttpMethod.GET, "https://example.test",
                Map.of(), Map.of(), null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
