package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import com.mmo.module.fb.ingest.provider.model.TeamQuery;

import java.util.List;

public interface TeamProvider {
    Provider getProvider();
    List<ProviderTeam> fetchTeams(TeamQuery query);
}
