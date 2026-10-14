package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import com.mmo.module.fb.ingest.provider.model.TeamQuery;

import java.util.List;

public interface TeamProvider {
    List<ProviderTeam> fetchTeams(TeamQuery query);
}
