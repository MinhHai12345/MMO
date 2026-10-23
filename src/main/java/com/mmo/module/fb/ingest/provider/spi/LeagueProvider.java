package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;

import java.util.List;

public interface LeagueProvider {
    Provider getProvider();
    List<ProviderLeague> fetchLeagues(List<Long> externalLeagueIds);
}
