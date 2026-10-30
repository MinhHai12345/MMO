package com.mmo.module.fb.ingest.provider.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Shared HTTP transport for REST providers. It owns request execution and JSON decoding only;
 * authentication, provider error semantics, retry and quota policies remain with each provider.
 */
@Component
@RequiredArgsConstructor
public class ProviderHttpClient {
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public <T> T execute(ProviderHttpRequest<T> request) {
        URI uri = buildUri(request);
        try {
            WebClient.RequestBodySpec requestSpec = webClientBuilder.clone().build()
                    .method(request.method()).uri(uri)
                    .headers(headers -> request.headers().forEach(headers::set));
            WebClient.RequestHeadersSpec<?> responseSpec = request.body() == null
                    ? requestSpec : requestSpec.bodyValue(request.body());
            String payload = responseSpec.retrieve().bodyToMono(String.class)
                    .block(request.timeout());
            if (!StringUtils.hasText(payload)) {
                throw new IllegalStateException("Provider returned an empty response: " + uri);
            }
            return objectMapper.readValue(payload, request.responseType());
        } catch (WebClientResponseException ex) {
            throw new ProviderHttpException(ex.getStatusCode().value(), ex.getHeaders(),
                    ex.getResponseBodyAsString(),
                    "HTTP " + ex.getStatusCode().value() + " calling " + uri, ex);
        } catch (WebClientRequestException ex) {
            throw new ProviderTransportException("Network failure calling " + uri, ex);
        } catch (ProviderHttpException | ProviderTransportException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not execute provider HTTP request " + uri, ex);
        }
    }

    private URI buildUri(ProviderHttpRequest<?> request) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(request.uri());
        request.queryParams().forEach((key, value) -> {
            if (value == null) return;
            if (value instanceof Iterable<?> values) {
                values.forEach(item -> { if (item != null) builder.queryParam(key, item); });
            } else if (value.getClass().isArray()) {
                Object[] values = (Object[]) value;
                for (Object item : values) if (item != null) builder.queryParam(key, item);
            } else {
                builder.queryParam(key, value);
            }
        });
        return builder.build().encode().toUri();
    }
}
