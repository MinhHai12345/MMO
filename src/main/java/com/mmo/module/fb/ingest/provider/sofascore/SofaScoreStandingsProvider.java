package com.mmo.module.fb.ingest.provider.sofascore;

import com.microsoft.playwright.Page;
import com.mmo.module.fb.ingest.provider.model.ProviderStanding;
import com.mmo.module.fb.ingest.provider.model.StandingsQuery;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaSeasonData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaStandingsData;
import com.mmo.module.fb.ingest.provider.service.SofaCrawlerService;
import com.mmo.module.fb.ingest.provider.spi.StandingsProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class SofaScoreStandingsProvider extends AbstractSofaScoreProvider implements StandingsProvider {

    public SofaScoreStandingsProvider(SofaCrawlerService sofaCrawlerService) {
        super(sofaCrawlerService);
    }

    @Override
    public List<ProviderStanding> fetchStandings(StandingsQuery query) {
        if (query == null || query.leagueId() == null) return List.of();
        return withPage(page -> {
            List<SofaSeasonData.SeasonDTO> seasons =
                    sofaCrawlerService.fetchSeasonByTournamentId(query.leagueId(), page);
            if (seasons == null) return List.of();
            SofaSeasonData.SeasonDTO season = seasons.stream().filter(Objects::nonNull)
                    .filter(s -> query.season() == null || query.season().equals(s.getYear())
                            || query.season().equals(s.getName()))
                    .findFirst().orElse(null);
            if (season == null || season.getId() == null) return List.of();

            List<SofaStandingsData.StandingGroupDTO> groups =
                    sofaCrawlerService.fetchStandingTotal(query.leagueId(), season.getId(), page);
            if (groups == null) return List.of();
            return groups.stream().filter(Objects::nonNull)
                    .filter(group -> group.getRows() != null)
                    .flatMap(group -> group.getRows().stream()
                            .filter(Objects::nonNull)
                            .map(row -> new ProviderStanding(
                                    row.getTeam() == null || row.getTeam().getId() == null
                                            ? null : row.getTeam().getId().toString(),
                                    row.getPosition(), row.getPoints(), parseGoalDifference(row.getScoreDiffFormatted()),
                                    null, group.getName(), row.getMatches(), row.getWins(), row.getDraws(),
                                    row.getLosses(), row.getScoresFor(), row.getScoresAgainst())))
                    .filter(row -> row.teamExternalId() != null)
                    .toList();
        });
    }

    private Integer parseGoalDifference(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.valueOf(value.replace("+", ""));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
