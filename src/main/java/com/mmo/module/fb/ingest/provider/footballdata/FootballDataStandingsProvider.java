package com.mmo.module.fb.ingest.provider.footballdata;

import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.ingest.provider.footballdata.client.FootballDataClient;
import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.StandingsDto;
import com.mmo.module.fb.ingest.provider.footballdata.mapper.FootballDataMapper;
import com.mmo.module.fb.ingest.provider.model.ProviderStanding;
import com.mmo.module.fb.ingest.provider.model.StandingsQuery;
import com.mmo.module.fb.ingest.provider.spi.StandingsProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FootballDataStandingsProvider implements StandingsProvider {
    private final FootballDataClient client;
    private final FootballDataMapper mapper;

    @Override
    public Provider getProvider() { return Provider.FOOTBALL_DATA; }

    @Override
    public List<ProviderStanding> fetchStandings(StandingsQuery query) {
        if (query == null || query.leagueId() == null) return List.of();
        StandingsDto response = StringUtils.hasText(query.season())
                ? client.get("/competitions/{id}/standings?season={season}", StandingsDto.class,
                    query.leagueId(), query.season())
                : client.get("/competitions/{id}/standings", StandingsDto.class, query.leagueId());
        return mapper.toStandings(response);
    }
}