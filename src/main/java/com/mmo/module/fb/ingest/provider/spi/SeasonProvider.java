package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import com.mmo.module.fb.ingest.provider.model.SeasonQuery;

import java.util.List;

/** Source-neutral contract for discovering seasons independently from league metadata. */
public interface SeasonProvider {
    Provider getProvider();
    List<ProviderSeason> fetchSeasons(SeasonQuery query);
}
