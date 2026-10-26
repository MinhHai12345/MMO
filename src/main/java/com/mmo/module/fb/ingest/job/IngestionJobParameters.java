package com.mmo.module.fb.ingest.job;

import com.mmo.module.fb.ingest.enums.Provider;
import org.quartz.JobExecutionContext;
import org.quartz.JobDataMap;

import java.util.Locale;

/**
 * Reads optional provider overrides supplied for a single Quartz execution.
 *
 * <p>Supported keys are {@code provider} and {@code provider.<resource>}, for example
 * {@code provider.fixture=SOFA_SCORE}. Resource-specific values take precedence.
 * Returning {@code null} means that the ingestion service should use its configured default.</p>
 */
public final class IngestionJobParameters {

    private IngestionJobParameters() {
    }

    public static Provider provider(JobExecutionContext context, String resource) {
        JobDataMap data = context.getMergedJobDataMap();
        Object configured = data.get("provider." + resource);
        if (configured == null || configured.toString().isBlank()) {
            configured = data.get("provider");
        }
        if (configured == null || configured.toString().isBlank()) {
            return null;
        }

        String normalized = normalize(configured.toString());
        for (Provider provider : Provider.values()) {
            if (normalize(provider.name()).equals(normalized)) {
                return provider;
            }
        }

        throw new IllegalArgumentException("Unsupported provider '" + configured
                + "' for ingestion resource '" + resource + "'. Supported providers: "
                + String.join(", ", java.util.Arrays.stream(Provider.values())
                    .map(Enum::name).toList()));
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
