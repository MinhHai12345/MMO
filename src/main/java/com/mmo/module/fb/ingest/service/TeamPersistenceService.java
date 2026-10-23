package com.mmo.module.fb.ingest.service;

import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import java.util.List;

public interface TeamPersistenceService {
    void persistTeamsForSeason(Provider provider, Season season, List<ProviderTeam> teams);
}
