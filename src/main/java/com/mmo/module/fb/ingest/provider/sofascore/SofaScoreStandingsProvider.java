package com.mmo.module.fb.ingest.provider.sofascore;

import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderStanding;
import com.mmo.module.fb.ingest.provider.model.StandingsQuery;
import com.mmo.module.fb.ingest.provider.sofascore.client.SofaScoreClient;
import com.mmo.module.fb.ingest.provider.sofascore.mapper.SofaScoreStandingsMapper;
import com.mmo.module.fb.ingest.provider.spi.StandingsProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class SofaScoreStandingsProvider extends AbstractSofaScoreProvider implements StandingsProvider {

    private final SofaScoreStandingsMapper mapper;
    private final SofaScoreClient sofaScoreClient;

    @Override
    public Provider getProvider() {
        return Provider.SOFA_SCORE;
    }

    @Override
    public List<ProviderStanding> fetchStandings(StandingsQuery query) {
        if (query == null || query.leagueId() == null) return List.of();
        return withPage(page -> {
            List<SofaScoreResponseDTO.SeasonDTO> seasons =
                    sofaScoreClient.fetchSeasonByTournamentId(query.leagueId(), page);
            if (seasons == null) return List.of();
            SofaScoreResponseDTO.SeasonDTO season = seasons.stream()
                    .filter(Objects::nonNull)
                    .filter(item -> query.season() == null || query.season().equals(item.year())
                                    || query.season().equals(item.name()))
                    .findFirst().orElse(null);
            if (season == null || season.id() == null) return List.of();

            List<SofaScoreResponseDTO.StandingGroupDTO> groups =
                    sofaScoreClient.fetchStandingTotal(query.leagueId(), season.id(), page);
            return mapper.toProviderStandings(groups);
        });
    }
}
