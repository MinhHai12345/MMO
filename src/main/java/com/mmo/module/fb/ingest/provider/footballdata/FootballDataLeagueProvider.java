package com.mmo.module.fb.ingest.provider.footballdata;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.footballdata.client.FootballDataClient;
import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.CompetitionDto;
import com.mmo.module.fb.ingest.provider.footballdata.mapper.FootballDataMapper;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.spi.LeagueProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FootballDataLeagueProvider implements LeagueProvider {
    private final FootballDataClient client;
    private final FootballDataMapper mapper;
    private final AppProperties properties;

    @Override
    public Provider getProvider() { return Provider.FOOTBALL_DATA; }

    @Override
    public List<ProviderLeague> fetchLeagues(List<Long> externalLeagueIds) {
        List<Long> ids = externalLeagueIds == null || externalLeagueIds.isEmpty()
                ? properties.getFootballData().getCompetitionIds() : externalLeagueIds;
        if (ids == null || ids.isEmpty()) return List.of();
        List<ProviderLeague> result = new ArrayList<>();
        for (Long id : ids) {
            if (id == null) continue;
            CompetitionDto dto = client.get("/competitions/{id}", CompetitionDto.class, id);
            ProviderLeague league = mapper.toLeague(dto);
            if (league != null) result.add(league);
        }
        return result;
    }
}