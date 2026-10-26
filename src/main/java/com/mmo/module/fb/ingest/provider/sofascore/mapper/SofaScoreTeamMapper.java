package com.mmo.module.fb.ingest.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.TeamDTO;

import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import org.springframework.stereotype.Component;

@Component
public class SofaScoreTeamMapper {

    public ProviderTeam toProviderTeam(TeamDTO team) {
        if (team == null || team.id() == null || team.name() == null) return null;
        return new ProviderTeam(team.id().toString(), team.name(),
                team.shortName(), null, null, null, null);
    }
}
