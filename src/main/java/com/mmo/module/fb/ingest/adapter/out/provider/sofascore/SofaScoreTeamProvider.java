package com.mmo.module.fb.ingest.adapter.out.provider.sofascore;

import com.microsoft.playwright.Page;
import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.model.provider.ProviderTeam;
import com.mmo.module.fb.ingest.application.model.provider.TeamQuery;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client.AbstractSofaScoreProvider;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client.SofaScoreClient;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.EventDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.MatchesResponseDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.SeasonDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.StandingGroupDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.StandingRowDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.mapper.SofaScoreTeamMapper;
import com.mmo.module.fb.ingest.application.port.out.provider.TeamIngestionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class SofaScoreTeamProvider extends AbstractSofaScoreProvider implements TeamIngestionPort {

    private static final List<String> EVENT_DIRECTIONS = List.of("last", "next");

    private final SofaScoreTeamMapper mapper;
    private final SofaScoreClient sofaScoreClient;
    private final AppProperties appProperties;

    @Override
    public Provider getProvider() {
        return Provider.SOFA_SCORE;
    }

    @Override
    public List<ProviderTeam> fetchTeams(TeamQuery query) {
        if (query == null || query.leagueId() == null || query.leagueId() <= 0) {
            return List.of();
        }
        return withPage(page -> {
            SeasonDTO season = findSeason(query.leagueId(), query.season(), page);
            if (season == null || season.id() == null) {
                throw new IllegalStateException("SofaScore season '" + query.season()
                        + "' was not found for tournament " + query.leagueId());
            }

            Map<String, ProviderTeam> teamsByExternalId = new LinkedHashMap<>();
            List<StandingGroupDTO> groups =
                    sofaScoreClient.fetchStandingTotal(query.leagueId(), season.id(), page);
            if (groups != null) {
                groups.stream()
                        .filter(Objects::nonNull)
                        .filter(group -> group.rows() != null)
                        .flatMap(group -> group.rows().stream())
                        .filter(Objects::nonNull)
                        .map(StandingRowDTO::team)
                        .map(mapper::toProviderTeam)
                        .filter(Objects::nonNull)
                        .filter(team -> hasText(team.externalId()))
                        .forEach(team -> teamsByExternalId.putIfAbsent(team.externalId(), team));
            }

            // Knockout competitions and some newly-created seasons have no standings.
            // Build the team catalogue from both sides of season events in that case.
            if (teamsByExternalId.isEmpty()) {
                fetchTeamsFromSeasonEvents(query.leagueId(), season.id(), page, teamsByExternalId);
            }

            log.info("SofaScore teams fetched tournament={} season={} count={} source={}",
                    query.leagueId(), season.name(), teamsByExternalId.size(),
                    groups == null || groups.isEmpty() ? "season-events" : "standings");
            return List.copyOf(teamsByExternalId.values());
        });
    }

    private void fetchTeamsFromSeasonEvents(
            Long tournamentId, Long seasonId, Page page, Map<String, ProviderTeam> teamsByExternalId) {
        int configuredMaxPages = appProperties.getSync().getMaxEventPages();
        int maxPages = configuredMaxPages > 0 ? configuredMaxPages : 20;

        for (String direction : EVENT_DIRECTIONS) {
            for (int pageIndex = 0; pageIndex < maxPages; pageIndex++) {
                MatchesResponseDTO response = sofaScoreClient.fetchSeasonMatchesPage(
                        tournamentId, seasonId, direction, pageIndex, page);
                if (response == null) {
                    throw new IllegalStateException("SofaScore returned a null " + direction
                            + " events response for tournament " + tournamentId + ", season " + seasonId);
                }
                if (response.events() != null) {
                    response.events().stream()
                            .filter(Objects::nonNull)
                            .flatMap(event -> java.util.stream.Stream.of(event.homeTeam(), event.awayTeam()))
                            .map(mapper::toProviderTeam)
                            .filter(Objects::nonNull)
                            .filter(team -> hasText(team.externalId()))
                            .forEach(team -> teamsByExternalId.putIfAbsent(team.externalId(), team));
                }
                if (!response.hasNextPage()) {
                    break;
                }
            }
        }
        if (teamsByExternalId.isEmpty()) {
            log.warn("SofaScore season contains no teams in standings or events: tournament={}, season={}",
                    tournamentId, seasonId);
        }
    }

    private SeasonDTO findSeason(Long tournamentId, String year, Page page) {
        List<SeasonDTO> seasons = sofaScoreClient.fetchSeasonByTournamentId(tournamentId, page);
        if (seasons == null) {
            throw new IllegalStateException("SofaScore season request failed for tournament " + tournamentId);
        }
        return SofaScoreSeasonMatcher.findMatchingSeason(seasons, year).orElse(null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
