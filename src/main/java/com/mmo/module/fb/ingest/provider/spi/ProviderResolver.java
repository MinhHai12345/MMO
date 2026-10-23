package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.enums.Provider;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Resolves a resource-specific provider from the Spring-injected provider implementations.
 */
public final class ProviderResolver {

    private ProviderResolver() {
    }

    public static <T> T resolve(
            List<T> providers,
            Provider selectedProvider,
            Function<T, Provider> providerCode,
            String resourceName) {

        Objects.requireNonNull(selectedProvider, "selectedProvider must not be null");
        return providers.stream()
                .filter(Objects::nonNull)
                .filter(provider -> selectedProvider == providerCode.apply(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No " + resourceName + " configured for provider " + selectedProvider));
    }
}
