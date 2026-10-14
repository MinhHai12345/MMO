package com.mmo.module.fb.ingest.provider.apifootball.mapper;

import com.mmo.core.converter.AbstractMapper;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.TeamItem;
import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import org.springframework.stereotype.Component;

@Component
public class ApiFootballTeamMapper extends AbstractMapper<TeamItem, ProviderTeam> {

    @Override
    public ProviderTeam map(TeamItem item, ProviderTeam target) {
        if (item == null || item.team() == null || item.team().id() == null) return null;
        var team = item.team();
        return new ProviderTeam(
                team.id().toString(),
                team.name(),
                team.code(),
                team.logo(),
                team.founded(),
                item.venue() == null ? null : item.venue().name(),
                item.venue() == null ? null : item.venue().capacity()
        );
    }
}
