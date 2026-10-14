package com.mmo.module.fb.ingest.provider.apifootball;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.ingest.provider.apifootball.client.ApiFootballClient;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.LeagueItem;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballResponse;
import com.mmo.module.fb.ingest.provider.apifootball.mapper.ApiFootballLeagueMapper;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.spi.LeagueProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ApiFootballLeagueProvider implements LeagueProvider {

    private static final TypeReference<ApiFootballResponse<LeagueItem>> RESPONSE_TYPE =
            new TypeReference<>() {};

    private final ApiFootballClient client;
    private final ApiFootballLeagueMapper mapper;

    @Override
    public Provider getProvider() {
        return Provider.API_FOOTBALL;
    }

    @Override
    public List<ProviderLeague> fetchLeagues(List<Long> externalLeagueIds) {
        if (externalLeagueIds == null || externalLeagueIds.isEmpty()) return List.of();
        List<ProviderLeague> result = new ArrayList<>();
        for (Long externalLeagueId : externalLeagueIds) {
            if (externalLeagueId == null) continue;
            ApiFootballResponse<LeagueItem> response = client.get(
                    "/leagues", Map.of("id", externalLeagueId), RESPONSE_TYPE);
            if (response.response() == null) continue;
            response.response().stream()
                    .map(mapper::toModel)
                    .filter(Objects::nonNull)
                    .forEach(result::add);
        }
        return result;
    }
}
