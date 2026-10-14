package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.provider.model.ProviderStanding;
import com.mmo.module.fb.ingest.provider.model.StandingsQuery;

import java.util.List;

public interface StandingsProvider {
    List<ProviderStanding> fetchStandings(StandingsQuery query);
}
