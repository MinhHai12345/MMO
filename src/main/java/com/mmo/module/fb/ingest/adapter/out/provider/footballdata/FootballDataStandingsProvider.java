package com.mmo.module.fb.ingest.adapter.out.provider.footballdata;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.adapter.out.provider.footballdata.client.FootballDataClient;
import com.mmo.module.fb.ingest.adapter.out.provider.footballdata.dto.FootballDataDtos.StandingsDto;
import com.mmo.module.fb.ingest.adapter.out.provider.footballdata.mapper.FootballDataResponseMapper;
import com.mmo.module.fb.ingest.application.model.provider.ProviderStanding;
import com.mmo.module.fb.ingest.application.model.provider.StandingsQuery;
import com.mmo.module.fb.ingest.application.port.out.provider.StandingsIngestionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FootballDataStandingsProvider implements StandingsIngestionPort {
    private final FootballDataClient client;
    private final FootballDataResponseMapper mapper;

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