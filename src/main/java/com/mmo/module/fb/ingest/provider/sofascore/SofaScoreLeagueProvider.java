package com.mmo.module.fb.ingest.provider.sofascore;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaSeasonData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaUniqueTournamentsData;
import com.mmo.module.fb.ingest.provider.service.SofaCrawlerService;
import com.mmo.module.fb.ingest.provider.sofascore.mapper.SofaScoreLeagueMapper;
import com.mmo.module.fb.ingest.provider.spi.LeagueProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class SofaScoreLeagueProvider extends AbstractSofaScoreProvider implements LeagueProvider {

    private final SofaScoreLeagueMapper mapper;
    private final SofaCrawlerService sofaCrawlerService;

    @Override
    public Provider getProvider() {
        return Provider.SOFA_SCORE;
    }

    @Override
    public List<ProviderLeague> fetchLeagues(List<Long> externalLeagueIds) {
        return withPage(page -> {
            List<SofaUniqueTournamentsData.UniqueTournamentDTO> tournaments =
                    sofaCrawlerService.fetchLeagues(page);
            if (tournaments == null) return List.of();

            return tournaments.stream()
                    .filter(Objects::nonNull)
                    .filter(tournament -> tournament.getId() != null)
                    .filter(tournament -> externalLeagueIds == null || externalLeagueIds.isEmpty()
                                          || externalLeagueIds.contains(tournament.getId()))
                    .map(tournament -> {
                        List<SofaSeasonData.SeasonDTO> seasons =
                                sofaCrawlerService.fetchSeasonByTournamentId(tournament.getId(), page);
                        return mapper.toProviderLeague(tournament, seasons);
                    })
                    .filter(Objects::nonNull)
                    .toList();
        });
    }
}
