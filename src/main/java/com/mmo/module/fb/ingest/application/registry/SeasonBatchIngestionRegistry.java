package com.mmo.module.fb.ingest.application.registry;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.application.port.out.provider.SeasonBatchIngestionPort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Resolves optional season-batch capabilities without coupling orchestration to a provider. */
@Component
public class SeasonBatchIngestionRegistry {
    private final Map<Provider, SeasonBatchIngestionPort> providers;

    public SeasonBatchIngestionPortRegistry(List<SeasonBatchIngestionPort> providers) {
        Map<Provider, SeasonBatchIngestionPort> indexed = new EnumMap<>(Provider.class);
        if (providers != null) {
            for (SeasonBatchIngestionPort provider : providers) {
                if (provider == null || provider.getProvider() == null) {
                    throw new IllegalStateException("Season batch provider and provider identity must not be null");
                }
                SeasonBatchIngestionPort previous = indexed.putIfAbsent(provider.getProvider(), provider);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate season batch provider: " + provider.getProvider());
                }
            }
        }
        this.providers = Collections.unmodifiableMap(indexed);
    }

    public boolean supports(Provider provider, Dataset dataset) {
        SeasonBatchIngestionPort batchProvider = providers.get(provider);
        return batchProvider != null && batchProvider.supportedDatasets() != null
                && batchProvider.supportedDatasets().contains(dataset);
    }

    public SeasonBatchIngestionPort require(Provider provider) {
        SeasonBatchIngestionPort result = providers.get(provider);
        if (result == null) throw new IllegalStateException("No season batch provider registered for " + provider);
        return result;
    }

    public Collection<SeasonBatchIngestionPort> all() {
        return providers.values();
    }
}
