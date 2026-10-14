package com.mmo.module.fb.ingest.provider.apifootball;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.TeamItem;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballResponse;
import com.mmo.module.fb.ingest.provider.apifootball.mapper.ApiFootballTeamMapper;
import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import com.mmo.module.fb.ingest.provider.model.TeamQuery;
import com.mmo.module.fb.ingest.provider.spi.TeamProvider;
import com.mmo.module.fb.domain.enums.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ApiFootballTeamProvider implements TeamProvider {

    private static final TypeReference<ApiFootballResponse<TeamItem>> RESPONSE_TYPE =
            new TypeReference<>() {};

    private final ApiFootballClient client;
    private final ApiFootballTeamMapper mapper;

    @Override
    public Provider getProvider() {
        return Provider.API_FOOTBALL;
    }

    @Override
    public List<ProviderTeam> fetchTeams(TeamQuery query) {
        ApiFootballResponse<TeamItem> response = client.get("/teams", Map.of(
                "league", query.leagueId(),
                "season", query.season()
        ), RESPONSE_TYPE);
        if (response.response() == null) return List.of();
        return response.response().stream()
                .map(mapper::toModel)
                .filter(Objects::nonNull)
                .toList();
    }
}
