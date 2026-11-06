package com.mmo.module.fb.ingest.adapter.out.provider.apifootball;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.adapter.out.provider.apifootball.client.ApiFootballClient;
import com.mmo.module.fb.ingest.adapter.out.provider.apifootball.dto.ApiFootballDtos.StandingsItem;
import com.mmo.module.fb.ingest.adapter.out.provider.apifootball.dto.ApiFootballResponse;
import com.mmo.module.fb.ingest.adapter.out.provider.apifootball.mapper.ApiFootballStandingsMapper;
import com.mmo.module.fb.ingest.application.model.provider.ProviderStanding;
import com.mmo.module.fb.ingest.application.model.provider.StandingsQuery;
import com.mmo.module.fb.ingest.application.port.out.provider.StandingsIngestionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ApiFootballStandingsProvider implements StandingsIngestionPort {

    private static final TypeReference<ApiFootballResponse<StandingsItem>> RESPONSE_TYPE =
            new TypeReference<>() {
            };

    private final ApiFootballClient client;
    private final ApiFootballStandingsMapper mapper;

    @Override
    public Provider getProvider() {
        return Provider.API_FOOTBALL;
    }

    @Override
    public List<ProviderStanding> fetchStandings(StandingsQuery query) {
        ApiFootballResponse<StandingsItem> response = client.get("/standings", Map.of(
                "league", query.leagueId(),
                "season", query.season()
        ), RESPONSE_TYPE);
        return mapper.toModels(response.response());
    }
}
