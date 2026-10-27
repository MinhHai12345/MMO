package com.mmo.module.fb.ingest.provider.footballdata;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.footballdata.client.FootballDataClient;
import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.CompetitionDto;
import com.mmo.module.fb.ingest.provider.footballdata.dto.FootballDataDtos.CompetitionListDto;
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

    @Override
    public Provider getProvider() { return Provider.FOOTBALL_DATA; }

    @Override
    public List<ProviderLeague> fetchLeagues(List<Long> externalLeagueIds) {
        List<ProviderLeague> result = new ArrayList<>();
        if (externalLeagueIds == null || externalLeagueIds.isEmpty()) {
            CompetitionListDto catalogue = client.get("/competitions", CompetitionListDto.class);
            if (catalogue == null || catalogue.competitions() == null) return List.of();
            for (CompetitionDto dto : catalogue.competitions()) {
                ProviderLeague league = mapper.toLeague(dto);
                if (league != null) result.add(league);
            }
            return result;
        }
        for (Long id : externalLeagueIds) {
            if (id == null) continue;
            CompetitionDto dto = client.get("/competitions/{id}", CompetitionDto.class, id);
            ProviderLeague league = mapper.toLeague(dto);
            if (league != null) result.add(league);
        }
        return result;
    }
}