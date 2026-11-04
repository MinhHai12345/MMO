package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderMatchAdvancedStatistics;

import java.util.Optional;

/** Fetches source-neutral advanced statistics for one provider match ID. */
public interface MatchStatisticsProvider {
    Provider getProvider();
    Optional<ProviderMatchAdvancedStatistics> fetchMatchStatistics(String externalMatchId);
}
