package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.TeamDTO;

import com.mmo.module.fb.ingest.adapter.out.provider.standardization.MmoProviderDataStandardizer;
import com.mmo.module.fb.ingest.application.model.provider.ProviderTeam;
import org.springframework.stereotype.Component;

@Component
public class SofaScoreTeamMapper {

    public ProviderTeam toProviderTeam(TeamDTO team) {
        if (team == null || team.id() == null || team.name() == null) return null;
        String name = MmoProviderDataStandardizer.standardTeamName(team.name());
        return new ProviderTeam(team.id().toString(), name,
                MmoProviderDataStandardizer.teamShortName(team.shortName(), name),
                null, null, null, null);
    }
}
