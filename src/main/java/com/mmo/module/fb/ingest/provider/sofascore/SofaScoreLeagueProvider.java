package com.mmo.module.fb.ingest.provider.sofascore;

import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaSeasonData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaUniqueTournamentsData;
import com.mmo.module.fb.ingest.provider.service.SofaCrawlerService;
import com.mmo.module.fb.ingest.provider.spi.LeagueProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class SofaScoreLeagueProvider extends AbstractSofaScoreProvider implements LeagueProvider {

    public SofaScoreLeagueProvider(SofaCrawlerService sofaCrawlerService) {
        super(sofaCrawlerService);
    }

    @Override
    public List<ProviderLeague> fetchLeagues(List<Long> externalLeagueIds) {
        return withPage(page -> {
            List<SofaUniqueTournamentsData.UniqueTournamentDTO> tournaments =
                    sofaCrawlerService.fetchLeagues(page);
            if (tournaments == null) return List.of();

            return tournaments.stream()
                    .filter(Objects::nonNull)
                    .filter(t -> t.getId() != null)
                    .filter(t -> externalLeagueIds == null || externalLeagueIds.isEmpty()
                            || externalLeagueIds.contains(t.getId()))
                    .map(tournament -> {
                        List<SofaSeasonData.SeasonDTO> seasonDtos =
                                sofaCrawlerService.fetchSeasonByTournamentId(tournament.getId(), page);
                        List<ProviderSeason> seasons = seasonDtos == null ? List.of() : seasonDtos.stream()
                                .filter(Objects::nonNull)
                                .filter(s -> s.getId() != null)
                                .map(this::toSeason)
                                .toList();
                        String country = tournament.getCategory() == null
                                ? null : tournament.getCategory().getName();
                        return new ProviderLeague(tournament.getId().toString(), tournament.getName(),
                                country, null, seasons);
                    })
                    .toList();
        });
    }

    private ProviderSeason toSeason(SofaSeasonData.SeasonDTO season) {
        String year = season.getYear() != null ? season.getYear() : season.getName();
        return new ProviderSeason(season.getId().toString(), year, season.isCurrent(), null, null);
    }
}
