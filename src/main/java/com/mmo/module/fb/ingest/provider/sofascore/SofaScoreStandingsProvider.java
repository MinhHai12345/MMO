package com.mmo.module.fb.ingest.provider.sofascore;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderStanding;
import com.mmo.module.fb.ingest.provider.model.StandingsQuery;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaSeasonData;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaStandingsData;
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
            List<SofaSeasonData.SeasonDTO> seasons =
                    sofaScoreClient.fetchSeasonByTournamentId(query.leagueId(), page);
            if (seasons == null) return List.of();
            SofaSeasonData.SeasonDTO season = seasons.stream()
                    .filter(Objects::nonNull)
                    .filter(item -> query.season() == null || query.season().equals(item.getYear())
                                    || query.season().equals(item.getName()))
                    .findFirst().orElse(null);
            if (season == null || season.getId() == null) return List.of();

            List<SofaStandingsData.StandingGroupDTO> groups =
                    sofaScoreClient.fetchStandingTotal(query.leagueId(), season.getId(), page);
            return mapper.toProviderStandings(groups);
        });
    }
}
