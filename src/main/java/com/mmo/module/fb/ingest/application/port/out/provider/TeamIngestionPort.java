package com.mmo.module.fb.ingest.application.port.out.provider;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.model.provider.ProviderTeam;
import com.mmo.module.fb.ingest.provider.model.TeamQuery;
import java.util.List;

/** Application-owned outbound capability for team catalogue retrieval. */
public interface TeamIngestionPort {
    Provider getProvider();
    List<ProviderTeam> fetchTeams(TeamQuery query);
}
