package com.mmo.module.fb.ingest.application.port.out.provider;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.model.provider.ProviderLeague;
import java.util.List;

/** Application-owned outbound capability for league catalogue retrieval. */
public interface LeagueIngestionPort {
    Provider getProvider();
    List<ProviderLeague> fetchLeagues(List<Long> externalLeagueIds);
}
