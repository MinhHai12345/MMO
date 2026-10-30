package com.mmo.module.fb.ingest.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.client.ProviderHttpException;
import com.mmo.module.fb.ingest.provider.client.ProviderTransportException;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import com.mmo.module.fb.ingest.provider.spi.LeagueProvider;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.ingest.provider.understats.UnderStatClient;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderStatDtos;
import com.mmo.module.fb.ingest.service.LeaguePersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Synchronizes UnderStat's discovered league/season catalogue, then its team/player data. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnderStatCatalogSyncService {
    private static final TypeReference<UnderStatDtos.LeagueData> LEAGUE_DATA = new TypeReference<>() { };
    private static final Provider PROVIDER = Provider.UNDER_STATS;
    private static final int MAX_ATTEMPTS = 3;
    private static final long DEFAULT_RETRY_SECONDS = 2;
    private static final long MAX_RETRY_SECONDS = 60;

    private final AppProperties appProperties;
    private final ProviderRegistry providerRegistry;
    private final LeaguePersistenceService leaguePersistenceService;
    private final UnderStatClient understatClient;
    private final UnderStatCatalogPersistenceService persistenceService;

    public void syncCatalog() {
        AppProperties.ProviderConfig config = appProperties.getIngestion().getProviderConfig().get(PROVIDER);
        if (config == null || !config.isEnabled()) {
            log.info("Skipping UnderStat catalogue sync because provider is disabled");
            return;
        }

        LeagueProvider provider = providerRegistry.league(PROVIDER);
        List<ProviderLeague> leagues = provider.fetchLeagues(List.of());
        if (leagues == null || leagues.isEmpty()) {
            throw new IllegalStateException(
                    "UnderStat /getStatData returned no leagues; refusing to report catalogue sync as successful");
        }

        // Use the same ProviderLeague -> League/Season persistence workflow as other sources.
        leaguePersistenceService.persistLeagues(PROVIDER, leagues);

        int seasonsFetched = 0;
        int emptySeasons = 0;
        int failedRequests = 0;
        int teamsProcessed = 0;
        int playersProcessed = 0;
        boolean accessDenied = false;

        for (ProviderLeague league : leagues) {
            if (league == null || !StringUtils.hasText(league.identify())
                    || league.seasons() == null || league.seasons().isEmpty()) {
                continue;
            }

            for (ProviderSeason season : league.seasons()) {
                try {
                    UnderStatDtos.LeagueData data = fetchWithRetry(league, season);
                    if (isEmpty(data)) {
                        emptySeasons++;
                        continue;
                    }

                    UnderStatCatalogPersistenceService.CatalogSyncResult result =
                            persistenceService.persist(league, season, data);
                    seasonsFetched += result.leagues();
                    teamsProcessed += result.teams();
                    playersProcessed += result.players();
                } catch (RuntimeException ex) {
                    if (isNotFound(ex)) {
                        emptySeasons++;
                        log.debug("UnderStat has no league data: identify={}, season={}",
                                league.identify(), season.year());
                    } else {
                        failedRequests++;
                        log.error("UnderStat catalogue fetch failed: identify={}, season={}: {}",
                                league.identify(), season.year(), ex.getMessage(), ex);
                        if (isAccessDenied(ex)) {
                            accessDenied = true;
                            log.error("UnderStat denied access; stopping catalogue scan to avoid repeated blocked requests");
                            break;
                        }
                    }
                }
            }
            if (accessDenied) break;
        }

        log.info("UnderStat catalogue sync finished: discoveredLeagues={}, seasonsFetched={}, emptySeasons={}, "
                        + "teamsProcessed={}, playersProcessed={}, failedRequests={}, accessDenied={}",
                leagues.size(), seasonsFetched, emptySeasons, teamsProcessed, playersProcessed,
                failedRequests, accessDenied);

        if (accessDenied || failedRequests > 0) {
            throw new IllegalStateException("UnderStat catalogue sync incomplete: discoveredLeagues="
                    + leagues.size() + ", seasonsFetched=" + seasonsFetched + ", emptySeasons=" + emptySeasons
                    + ", failedRequests=" + failedRequests + ", accessDenied=" + accessDenied);
        }
    }

    private UnderStatDtos.LeagueData fetchWithRetry(ProviderLeague league, ProviderSeason season) {
        RuntimeException lastFailure = null;
        int year = Integer.parseInt(season.year());

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return understatClient.fetchLeagueData(league.identify(), year, LEAGUE_DATA);
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (!isRetryable(ex) || attempt == MAX_ATTEMPTS) {
                    throw ex;
                }

                long delaySeconds = retryDelaySeconds(ex, attempt);
                log.warn("Retrying UnderStat league={} season={} attempt={}/{} after {} seconds: {}",
                        league.identify(), year, attempt + 1, MAX_ATTEMPTS, delaySeconds, ex.getMessage());
                sleep(Duration.ofSeconds(delaySeconds), league.identify(), year);
            }
        }
        throw lastFailure == null
                ? new IllegalStateException("UnderStat request failed without an exception")
                : lastFailure;
    }

    private static boolean isRetryable(RuntimeException ex) {
        if (ex instanceof ProviderTransportException) return true;
        if (ex instanceof ProviderHttpException http) {
            return http.getStatusCode() == 429 || http.getStatusCode() >= 500;
        }
        return false;
    }

    private static boolean isAccessDenied(RuntimeException ex) {
        return ex instanceof ProviderHttpException http
                && (http.getStatusCode() == 401 || http.getStatusCode() == 403);
    }

    private static boolean isNotFound(RuntimeException ex) {
        return ex instanceof ProviderHttpException http && http.getStatusCode() == 404;
    }

    private static long retryDelaySeconds(RuntimeException ex, int attempt) {
        if (ex instanceof ProviderHttpException http) {
            String retryAfter = http.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
            if (StringUtils.hasText(retryAfter)) {
                try {
                    return Math.min(MAX_RETRY_SECONDS, Math.max(1, Long.parseLong(retryAfter.trim())));
                } catch (NumberFormatException ignored) {
                    try {
                        Instant retryAt = ZonedDateTime.parse(
                                retryAfter.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                        return Math.min(MAX_RETRY_SECONDS,
                                Math.max(1, Duration.between(Instant.now(), retryAt).getSeconds()));
                    } catch (DateTimeParseException ignoredDate) {
                        // Use the bounded fallback below.
                    }
                }
            }
        }
        return Math.min(MAX_RETRY_SECONDS, DEFAULT_RETRY_SECONDS * attempt);
    }

    private static void sleep(Duration delay, String league, int year) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while retrying UnderStat league=" + league + " season=" + year, ex);
        }
    }

    private static boolean isEmpty(UnderStatDtos.LeagueData data) {
        return data == null || ((data.teams() == null || data.teams().isEmpty())
                && (data.players() == null || data.players().isEmpty())
                && (data.dates() == null || data.dates().isEmpty()));
    }
}
