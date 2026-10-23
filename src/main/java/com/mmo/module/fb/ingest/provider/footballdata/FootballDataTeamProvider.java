package com.mmo.module.fb.ingest.provider.footballdata;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.footballdata.client.FootballDataClient;
import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.TeamListDto;
import com.mmo.module.fb.ingest.provider.footballdata.mapper.FootballDataMapper;
import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import com.mmo.module.fb.ingest.provider.model.TeamQuery;
import com.mmo.module.fb.ingest.provider.spi.TeamProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class FootballDataTeamProvider implements TeamProvider {
    private final FootballDataClient client;
    private final FootballDataMapper mapper;

    @Override
    public Provider getProvider() { return Provider.FOOTBALL_DATA; }

    @Override
    public List<ProviderTeam> fetchTeams(TeamQuery query) {
        if (query == null || query.leagueId() == null) return List.of();
        TeamListDto response = StringUtils.hasText(query.season())
                ? client.get("/competitions/{id}/teams?season={season}", TeamListDto.class,
                    query.leagueId(), query.season())
                : client.get("/competitions/{id}/teams", TeamListDto.class, query.leagueId());
        if (response == null || response.teams() == null) return List.of();
        return response.teams().stream().map(mapper::toTeam).filter(Objects::nonNull).toList();
    }
}