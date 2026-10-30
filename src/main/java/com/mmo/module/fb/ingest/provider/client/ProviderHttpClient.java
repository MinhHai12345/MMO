package com.mmo.module.fb.ingest.provider.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import reactor.util.retry.Retry;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Shared HTTP transport for REST providers. It owns request execution and JSON decoding only;
 * authentication, provider error semantics and quota policies remain with each provider.
 */
@Component
@RequiredArgsConstructor
public class ProviderHttpClient {
    private static final int MAX_TRANSPORT_RETRIES = 2;
    private static final Duration RETRY_INITIAL_DELAY = Duration.ofMillis(500);
    private static final Duration RETRY_MAX_DELAY = Duration.ofSeconds(3);

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public <T> T execute(ProviderHttpRequest<T> request) {
        URI uri = buildUri(request);
        try {
            WebClient.RequestBodySpec requestSpec = webClientBuilder.clone()
                    .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(request.maxInMemorySize()))
                    .build()
                    .method(request.method())
                    .uri(uri)
                    .headers(headers -> request.headers().forEach(headers::set));

            WebClient.RequestHeadersSpec<?> responseSpec = request.body() == null
                    ? requestSpec
                    : requestSpec.bodyValue(request.body());

            String payload = responseSpec.retrieve()
                    .bodyToMono(String.class)
                    .timeout(request.timeout())
                    .retryWhen(Retry.backoff(MAX_TRANSPORT_RETRIES, RETRY_INITIAL_DELAY)
                            .maxBackoff(RETRY_MAX_DELAY)
                            .jitter(0.5)
                            .filter(failure -> isRetryableTransportFailure(request.method(), failure))
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    .block();

            if (!StringUtils.hasText(payload)) {
                throw new ProviderResponseException(
                        uri.toString(), payload, "Provider returned an empty response: " + uri, null);
            }

            try {
                return objectMapper.readValue(payload, request.responseType());
            } catch (Exception ex) {
                throw new ProviderResponseException(
                        uri.toString(), payload, "Provider returned an invalid JSON response: " + uri, ex);
            }
        } catch (WebClientResponseException ex) {
            throw new ProviderHttpException(ex.getStatusCode().value(), ex.getHeaders(),
                    ex.getResponseBodyAsString(),
                    "HTTP " + ex.getStatusCode().value() + " calling " + uri, ex);
        } catch (WebClientRequestException ex) {
            throw new ProviderTransportException("Network failure calling " + uri, ex);
        } catch (ProviderHttpException | ProviderTransportException | ProviderResponseException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (isTimeout(ex)) {
                throw new ProviderTransportException("Request timed out calling " + uri, ex);
            }
            throw new IllegalStateException("Could not execute provider HTTP request " + uri, ex);
        }
    }

    /**
     * Retry only safe reads and only failures that indicate a transient transport problem.
     * HTTP responses (including 4xx/5xx) are deliberately handled by provider policy instead.
     */
    static boolean isRetryableTransportFailure(HttpMethod method, Throwable failure) {
        if (method != HttpMethod.GET && method != HttpMethod.HEAD && method != HttpMethod.OPTIONS) {
            return false;
        }
        Throwable current = failure;
        while (current != null) {
            if (current instanceof WebClientRequestException
                    || current instanceof TimeoutException
                    || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private URI buildUri(ProviderHttpRequest<?> request) {
        URI originalUri = URI.create(request.uri());
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(originalUri);
        request.queryParams().forEach((key, value) -> {
            if (value == null) return;
            if (value instanceof Iterable<?> values) {
                values.forEach(item -> addEncodedQueryParam(builder, key, item));
            } else if (value.getClass().isArray()) {
                int length = java.lang.reflect.Array.getLength(value);
                for (int index = 0; index < length; index++) {
                    addEncodedQueryParam(builder, key, java.lang.reflect.Array.get(value, index));
                }
            } else {
                addEncodedQueryParam(builder, key, value);
            }
        });
        // The original URI may already contain encoded path/query components. fromUri() preserves
        // those components; encode only the newly appended query values to avoid %25 double encoding.
        return builder.build(true).toUri();
    }

    private static void addEncodedQueryParam(UriComponentsBuilder builder, String key, Object value) {
        if (value != null) {
            builder.queryParam(UriUtils.encodeQueryParam(key, StandardCharsets.UTF_8),
                    UriUtils.encodeQueryParam(String.valueOf(value), StandardCharsets.UTF_8));
        }
    }

    private static boolean isTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TimeoutException || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
