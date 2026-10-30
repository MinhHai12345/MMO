package com.mmo.module.fb.ingest.provider.apifootball.mapper;

import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.TeamItem;
import com.mmo.module.fb.ingest.provider.model.MmoProviderDataStandardizer;
import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import org.springframework.stereotype.Component;

@Component
public class ApiFootballTeamMapper {

    public ProviderTeam toModel(TeamItem item) {
        if (item == null || item.team() == null || item.team().id() == null) return null;
        var team = item.team();
        String name = MmoProviderDataStandardizer.standardTeamName(team.name());
        return new ProviderTeam(
                team.id().toString(),
                name,
                MmoProviderDataStandardizer.teamShortName(team.code(), name),
                team.logo(),
                team.founded(),
                item.venue() == null ? null : MmoProviderDataStandardizer.venueName(item.venue().name()),
                item.venue() == null ? null : item.venue().capacity()
        );
    }
}
