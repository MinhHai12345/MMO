package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.provider.spi.SeasonBatchProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Resolves optional season-batch capabilities without coupling orchestration to a provider. */
@Component
public class SeasonBatchProviderRegistry {
    private final Map<Provider, SeasonBatchProvider> providers;

    public SeasonBatchProviderRegistry(List<SeasonBatchProvider> providers) {
        Map<Provider, SeasonBatchProvider> indexed = new EnumMap<>(Provider.class);
        if (providers != null) {
            for (SeasonBatchProvider provider : providers) {
                if (provider == null || provider.getProvider() == null) {
                    throw new IllegalStateException("Season batch provider and provider identity must not be null");
                }
                SeasonBatchProvider previous = indexed.putIfAbsent(provider.getProvider(), provider);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate season batch provider: " + provider.getProvider());
                }
            }
        }
        this.providers = Collections.unmodifiableMap(indexed);
    }

    public boolean supports(Provider provider, Dataset dataset) {
        SeasonBatchProvider batchProvider = providers.get(provider);
        return batchProvider != null && batchProvider.supportedDatasets() != null
                && batchProvider.supportedDatasets().contains(dataset);
    }

    public SeasonBatchProvider require(Provider provider) {
        SeasonBatchProvider result = providers.get(provider);
        if (result == null) throw new IllegalStateException("No season batch provider registered for " + provider);
        return result;
    }

    public Collection<SeasonBatchProvider> all() {
        return providers.values();
    }
}
