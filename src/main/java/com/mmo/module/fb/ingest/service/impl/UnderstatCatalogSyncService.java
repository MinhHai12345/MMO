package com.mmo.module.fb.ingest.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.understats.UnderstatClient;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderstatDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Discovers the six leagues exposed by Understat and persists each available season's
 * league, team and player catalogue. The league endpoint returns all three collections
 * in one response, so no per-team/per-player catalogue request is necessary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnderstatCatalogSyncService {
    private static final TypeReference<UnderstatDtos.LeagueData> LEAGUE_DATA = new TypeReference<>() {};
    private static final int FIRST_SEASON = 2014;
    private static final List<LeagueDefinition> LEAGUES = List.of(
            new LeagueDefinition("EPL", "Premier League", "England"),
            new LeagueDefinition("La_Liga", "LaLiga", "Spain"),
            new LeagueDefinition("Bundesliga", "Bundesliga", "Germany"),
            new LeagueDefinition("Serie_A", "Serie A", "Italy"),
            new LeagueDefinition("Ligue_1", "Ligue 1", "France"),
            new LeagueDefinition("RFPL", "Russian Premier League", "Russia")
    );

    private final AppProperties appProperties;
    private final UnderstatClient understatClient;
    private final UnderstatCatalogPersistenceService persistenceService;

    public void syncCatalog() {
        AppProperties.ProviderConfig config = appProperties.getIngestion().getProviderConfig().get(Provider.UNDER_STATS);
        if (config == null || !config.isEnabled()) {
            log.info("Skipping Understat catalogue sync because provider is disabled");
            return;
        }

        int lastSeason = Year.now(ZoneOffset.UTC).getValue();
        int fetched = 0, empty = 0, failed = 0, teams = 0, players = 0;
        for (LeagueDefinition league : LEAGUES) {
            for (int year = FIRST_SEASON; year <= lastSeason; year++) {
                try {
                    UnderstatDtos.LeagueData data = understatClient.fetchLeagueData(league.key(), year, LEAGUE_DATA);
                    if (data == null || ((data.teams() == null || data.teams().isEmpty())
                            && (data.players() == null || data.players().isEmpty())
                            && (data.dates() == null || data.dates().isEmpty()))) {
                        empty++;
                        continue;
                    }
                    var result = persistenceService.persist(league.key(), league.name(), league.country(), year, data);
                    fetched += result.leagues();
                    teams += result.teams();
                    players += result.players();
                } catch (RuntimeException ex) {
                    failed++;
                    log.error("Understat catalogue fetch failed: league={}, season={}: {}",
                            league.key(), year, ex.getMessage(), ex);
                }
            }
        }
        log.info("Understat catalogue sync finished: leagues={}, seasonsFetched={}, emptySeasons={}, "
                        + "teamsProcessed={}, playersProcessed={}, failedRequests={}",
                LEAGUES.size(), fetched, empty, teams, players, failed);
        if (failed > 0) {
            throw new IllegalStateException("Understat catalogue sync completed with " + failed
                    + " failed league-season requests. Review provider logs and rerun the job.");
        }
    }

    private record LeagueDefinition(String key, String name, String country) {}
}
