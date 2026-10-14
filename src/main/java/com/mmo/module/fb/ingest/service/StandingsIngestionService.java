package com.mmo.module.fb.ingest.service;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.entity.LeagueExternalId;
import com.mmo.module.fb.domain.entity.LeagueStanding;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.TeamExternalId;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.LeagueStandingRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.domain.repository.TeamExternalIdRepository;
import com.mmo.module.fb.ingest.provider.model.ProviderStanding;
import com.mmo.module.fb.ingest.provider.model.StandingsQuery;
import com.mmo.module.fb.ingest.provider.spi.StandingsProvider;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StandingsIngestionService {

    private final ProviderRegistry providerRegistry;
    private final AppProperties appProperties;
    private final TeamIngestionService teamIngestionService;
    private final SeasonRepository seasonRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final LeagueStandingRepository standingRepository;

    @Transactional
    public void syncStandings() {
        Provider selectedProvider = appProperties.getIngestion().getProviders().getStandings();
        syncStandingsForProvider(selectedProvider);
    }

    @Transactional
    public void syncStandingsForProvider(Provider selectedProvider) {
        if (selectedProvider == Provider.API_FOOTBALL
                && (appProperties.getApiFootball() == null || !appProperties.getApiFootball().isEnabled())) {
            return;
        }
        teamIngestionService.syncTeamsForProvider(selectedProvider);
        StandingsProvider provider = providerRegistry.standings(selectedProvider);
        Map<Long, String> leagueExternalIdsByLeagueId = new HashMap<>();
        leagueExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping ->
                leagueExternalIdsByLeagueId.put(mapping.getLeague().getId(), mapping.getExternalId()));
        int saved = 0;
        for (Season season : seasonRepository.findAll()) {
            if (season.getLeague() == null || season.getYear() == null) continue;
            Long externalLeagueId = parseLong(leagueExternalIdsByLeagueId.get(season.getLeague().getId()));
            if (externalLeagueId == null) continue;

            List<ProviderStanding> rows = provider.fetchStandings(
                    new StandingsQuery(externalLeagueId, season.getYear()));
            for (ProviderStanding row : rows) {
                var team = teamExternalIdRepository
                        .findByProviderAndExternalId(selectedProvider, row.teamExternalId())
                        .map(TeamExternalId::getTeam)
                        .orElse(null);
                if (team == null) continue;

                LeagueStanding standing = standingRepository
                        .findBySeasonIdAndTeamId(season.getId(), team.getId())
                        .orElseGet(() -> LeagueStanding.builder()
                                .league(season.getLeague()).season(season).team(team).build());
                standing.setPosition(row.position());
                standing.setPoints(row.points());
                standing.setGoalDifference(row.goalDifference());
                standing.setForm(row.form());
                standing.setGroupName(row.groupName());
                standing.setPlayed(row.played());
                standing.setWon(row.won());
                standing.setDrawn(row.drawn());
                standing.setLost(row.lost());
                standing.setGoalsFor(row.goalsFor());
                standing.setGoalsAgainst(row.goalsAgainst());
                standing.setLastSyncedAt(Instant.now());
                standingRepository.save(standing);
                saved++;
            }
        }
        log.info("Standings ingestion completed. {} rows upserted.", saved);
    }



    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
