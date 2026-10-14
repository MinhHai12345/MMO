package com.mmo.module.fb.ingest.provider.sofascore;

import com.microsoft.playwright.Page;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import com.mmo.module.fb.ingest.provider.model.TeamQuery;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaSeasonData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaStandingsData;
import com.mmo.module.fb.ingest.provider.service.SofaCrawlerService;
import com.mmo.module.fb.ingest.provider.sofascore.mapper.SofaScoreTeamMapper;
import com.mmo.module.fb.ingest.provider.spi.TeamProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class SofaScoreTeamProvider extends AbstractSofaScoreProvider implements TeamProvider {

    private final SofaScoreTeamMapper mapper;

    public SofaScoreTeamProvider(SofaCrawlerService sofaCrawlerService, SofaScoreTeamMapper mapper) {
        super(sofaCrawlerService);
        this.mapper = mapper;
    }

    @Override
    public List<ProviderTeam> fetchTeams(TeamQuery query) {
        if (query == null || query.leagueId() == null) return List.of();
        return withPage(page -> {
            SofaSeasonData.SeasonDTO season = findSeason(query.leagueId(), query.season(), page);
            if (season == null || season.getId() == null) return List.of();

            List<SofaStandingsData.StandingGroupDTO> groups =
                    sofaCrawlerService.fetchStandingTotal(query.leagueId(), season.getId(), page);
            if (groups == null) return List.of();

            return groups.stream()
                    .filter(Objects::nonNull)
                    .filter(group -> group.getRows() != null)
                    .flatMap(group -> group.getRows().stream())
                    .filter(Objects::nonNull)
                    .map(SofaStandingsData.StandingRowDTO::getTeam)
                    .map(mapper::toProviderTeam)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(ProviderTeam::externalId, team -> team,
                            (first, ignored) -> first))
                    .values().stream().toList();
        });
    }

    private SofaSeasonData.SeasonDTO findSeason(Long tournamentId, String year, Page page) {
        List<SofaSeasonData.SeasonDTO> seasons =
                sofaCrawlerService.fetchSeasonByTournamentId(tournamentId, page);
        if (seasons == null) return null;
        return seasons.stream()
                .filter(Objects::nonNull)
                .filter(season -> year == null || year.equals(season.getYear()) || year.equals(season.getName()))
                .findFirst().orElse(null);
    }
}
