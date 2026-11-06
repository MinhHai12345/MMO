package com.mmo.module.fb.ingest.application.port.out.provider;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.model.provider.ProviderSeason;
import com.mmo.module.fb.ingest.application.model.provider.SeasonQuery;
import java.util.List;

/** Application-owned outbound capability for season catalogue retrieval. */
public interface SeasonIngestionPort {
    Provider getProvider();
    List<ProviderSeason> fetchSeasons(SeasonQuery query);
}
