package com.mmo.module.fb.ingest.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaStandingsData;
import org.springframework.stereotype.Component;

@Component
public class SofaScoreTeamMapper {

    public ProviderTeam toProviderTeam(SofaStandingsData.TeamDTO team) {
        if (team == null || team.getId() == null || team.getName() == null) return null;
        return new ProviderTeam(team.getId().toString(), team.getName(),
                team.getShortName(), null, null, null, null);
    }
}
