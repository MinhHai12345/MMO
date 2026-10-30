package com.mmo.module.fb.ingest.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.understats.UnderStatClient;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderStatDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;

/** Discovers and synchronizes Understat's complete league/team/player catalogue. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnderStatCatalogSyncService {
    private static final TypeReference<UnderStatDtos.LeagueData> LEAGUE_DATA = new TypeReference<>() {};
    private static final int FIRST_SEASON = 2014;
    private static final int MAX_ATTEMPTS = 3;
    private static final List<LeagueDefinition> LEAGUES = List.of(
            new LeagueDefinition("EPL", "Premier League", "England"),
            new LeagueDefinition("La_Liga", "LaLiga", "Spain"),
            new LeagueDefinition("Bundesliga", "Bundesliga", "Germany"),
            new LeagueDefinition("Serie_A", "Serie A", "Italy"),
            new LeagueDefinition("Ligue_1", "Ligue 1", "France"),
            new LeagueDefinition("RFPL", "Russian Premier League", "Russia")
    );

    private final AppProperties appProperties;
    private final UnderStatClient understatClient;
    private final UnderStatCatalogPersistenceService persistenceService;

    public void syncCatalog() {
        AppProperties.ProviderConfig config = appProperties.getIngestion().getProviderConfig().get(Provider.UNDER_STATS);
        if (config == null || !config.isEnabled()) {
            log.info("Skipping Understat catalogue sync because provider is disabled");
            return;
        }
        int lastSeason = Year.now(ZoneOffset.UTC).getValue();
        int fetched = 0, empty = 0, failed = 0, teams = 0, players = 0;
        catalogue:
        for (LeagueDefinition league : LEAGUES) {
            for (int year = FIRST_SEASON; year <= lastSeason; year++) {
                try {
                    UnderStatDtos.LeagueData data = fetchWithRetry(league, year);
                    if (data == null || isEmpty(data)) {
                        empty++;
                        continue;
                    }
                    var result = persistenceService.persist(league.key(), league.name(), league.country(), year, data);
                    fetched += result.leagues();
                    teams += result.teams();
                    players += result.players();
                } catch (RuntimeException ex) {
                    if (isNotFound(ex)) {
                        empty++;
                        log.debug("Understat has no catalogue for league={} season={}", league.key(), year);
                    } else {
                        failed++;
                        log.error("Understat catalogue fetch failed: league={}, season={}: {}",
                                league.key(), year, ex.getMessage(), ex);
                        if (isAccessDenied(ex)) {
                            log.error("Understat denied access; stopping the catalogue scan to avoid repeated blocked requests");
                            break catalogue;
                        }
                    }
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

    private UnderStatDtos.LeagueData fetchWithRetry(LeagueDefinition league, int year) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return understatClient.fetchLeagueData(league.key(), year, LEAGUE_DATA);
            } catch (RuntimeException ex) {
                last = ex;
                if (!isRetryable(ex) || attempt == MAX_ATTEMPTS) throw ex;
                long delay = 1_000L * attempt;
                log.warn("Retrying Understat league={} season={} attempt={}/{} after {} ms: {}",
                        league.key(), year, attempt + 1, MAX_ATTEMPTS, delay, ex.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while retrying Understat request", interrupted);
                }
            }
        }
        throw last == null ? new IllegalStateException("Understat request failed") : last;
    }

    private static boolean isRetryable(RuntimeException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        return message.contains("HTTP 429") || message.matches("(?s).*HTTP 5[0-9]{2}.*")
                || message.startsWith("Failed to fetch Understat JSON endpoint");
    }

    private static boolean isAccessDenied(RuntimeException ex) {
        return ex.getMessage() != null && (ex.getMessage().contains("Understat HTTP 401")
                || ex.getMessage().contains("Understat HTTP 403"));
    }

    private static boolean isNotFound(RuntimeException ex) {
        return ex.getMessage() != null && ex.getMessage().contains("Understat HTTP 404");
    }

    private static boolean isEmpty(UnderStatDtos.LeagueData data) {
        return (data.teams() == null || data.teams().isEmpty())
                && (data.players() == null || data.players().isEmpty())
                && (data.dates() == null || data.dates().isEmpty());
    }

    private record LeagueDefinition(String key, String name, String country) {}
}
