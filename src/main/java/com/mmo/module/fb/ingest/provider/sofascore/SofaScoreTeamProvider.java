package com.mmo.module.fb.ingest.provider.sofascore;

import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO;

import com.microsoft.playwright.Page;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import com.mmo.module.fb.ingest.provider.model.TeamQuery;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SeasonsResponseDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.StandingsResponseDTO;
import com.mmo.module.fb.ingest.provider.sofascore.client.SofaScoreClient;
import com.mmo.module.fb.ingest.provider.sofascore.mapper.SofaScoreTeamMapper;
import com.mmo.module.fb.ingest.provider.spi.TeamProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SofaScoreTeamProvider extends AbstractSofaScoreProvider implements TeamProvider {

    private final SofaScoreTeamMapper mapper;
    private final SofaScoreClient sofaScoreClient;

    @Override
    public Provider getProvider() {
        return Provider.SOFA_SCORE;
    }

    @Override
    public List<ProviderTeam> fetchTeams(TeamQuery query) {
        if (query == null || query.leagueId() == null) return List.of();
        return withPage(page -> {
            SofaScoreResponseDTO.SeasonDTO season = findSeason(query.leagueId(), query.season(), page);
            if (season == null || season.getId() == null) return List.of();

            List<SofaScoreResponseDTO.StandingGroupDTO> groups =
                    sofaScoreClient.fetchStandingTotal(query.leagueId(), season.getId(), page);
            if (groups == null) return List.of();

            return groups.stream()
                    .filter(Objects::nonNull)
                    .filter(group -> group.getRows() != null)
                    .flatMap(group -> group.getRows().stream())
                    .filter(Objects::nonNull)
                    .map(SofaScoreResponseDTO.StandingRowDTO::getTeam)
                    .map(mapper::toProviderTeam)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(ProviderTeam::externalId, team -> team,
                            (first, ignored) -> first))
                    .values().stream().toList();
        });
    }

    private SofaScoreResponseDTO.SeasonDTO findSeason(Long tournamentId, String year, Page page) {
        List<SofaScoreResponseDTO.SeasonDTO> seasons =
                sofaScoreClient.fetchSeasonByTournamentId(tournamentId, page);
        if (seasons == null) return null;
        return seasons.stream()
                .filter(Objects::nonNull)
                .filter(season -> year == null || year.equals(season.getYear()) || year.equals(season.getName()))
                .findFirst().orElse(null);
    }
}
