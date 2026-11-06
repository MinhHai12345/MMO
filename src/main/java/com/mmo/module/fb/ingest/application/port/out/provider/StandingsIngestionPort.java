package com.mmo.module.fb.ingest.application.port.out.provider;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.model.provider.ProviderStanding;
import com.mmo.module.fb.ingest.application.model.provider.StandingsQuery;
import java.util.List;

/** Application-owned outbound capability for standings retrieval. */
public interface StandingsIngestionPort {
    Provider getProvider();
    List<ProviderStanding> fetchStandings(StandingsQuery query);
}
