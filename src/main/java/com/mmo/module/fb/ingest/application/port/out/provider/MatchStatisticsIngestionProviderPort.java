package com.mmo.module.fb.ingest.application.port.out.provider;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.model.provider.ProviderMatchAdvancedStatistics;
import java.util.Optional;

/** Application-owned outbound capability for match-level statistics retrieval. */
public interface MatchStatisticsIngestionProviderPort {
    Provider getProvider();
    Optional<ProviderMatchAdvancedStatistics> fetchMatchStatistics(String externalMatchId);
}
