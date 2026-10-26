package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.ingest.provider.footballdata.client.FootballDataRateLimitException;
import com.mmo.module.fb.ingest.provider.footballdata.client.FootballDataRestrictedResourceException;
import com.mmo.module.fb.team.entity.TeamExternalId;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.league.repository.SeasonRepository;
import com.mmo.module.fb.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.ingest.provider.model.ProviderStanding;
import com.mmo.module.fb.ingest.provider.model.StandingsQuery;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.ingest.provider.spi.StandingsProvider;
import com.mmo.module.fb.ingest.service.StandingsIngestionService;
import com.mmo.module.fb.ingest.service.StandingsPersistenceService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class StandingsIngestionServiceImpl implements StandingsIngestionService {

    private final ProviderRegistry providerRegistry;
    private final AppProperties appProperties;
    private final TeamIngestionService teamIngestionService;
    private final SeasonRepository seasonRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final StandingsPersistenceService standingsPersistenceService;

    @Override
    public void syncStandings() {
        syncStandingsForProvider(appProperties.getIngestion().getProviders().getStandings());
    }

    /**
     * Fetch provider data outside a database transaction; each season's rows are persisted
     * in a separate short transaction by StandingsPersistenceService.
     */
    @Override
    public void syncStandingsForProvider(Provider selectedProvider) {
        if (selectedProvider == Provider.FOOTBALL_DATA
                && (appProperties.getFootballData() == null || !appProperties.getFootballData().isEnabled())) {
            throw new IllegalStateException("football-data.org is disabled but was selected for standings ingestion");
        }
        if (selectedProvider == Provider.API_FOOTBALL
                && (appProperties.getApiFootball() == null || !appProperties.getApiFootball().isEnabled())) {
            throw new IllegalStateException("API-Football is disabled but was selected for standings ingestion");
        }

        // Allow standalone scheduled runs to bootstrap missing team mappings without repeating
        // the full teams endpoint on every standings refresh.
        if (teamExternalIdRepository.findAllByProvider(selectedProvider).isEmpty()) {
            teamIngestionService.syncTeamsForProvider(selectedProvider);
        }
        StandingsProvider provider = providerRegistry.standings(selectedProvider);

        Map<Long, String> leagueExternalIdsByLeagueId = new HashMap<>();
        leagueExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping ->
                leagueExternalIdsByLeagueId.put(mapping.getLeague().getId(), mapping.getExternalId()));

        Map<String, Long> teamIdsByExternalId = new HashMap<>();
        for (TeamExternalId mapping : teamExternalIdRepository.findAllByProvider(selectedProvider)) {
            teamIdsByExternalId.put(mapping.getExternalId(), mapping.getTeam().getId());
        }

        int saved = 0;
        int skipped = 0;
        boolean rateLimitInterrupted = false;
        // For football-data.org, an explicit app.football-data.seasons list controls
        // both current and historical standings. Other providers retain current-season behavior.
        List<String> configuredSeasons = selectedProvider == Provider.FOOTBALL_DATA
                && appProperties.getFootballData() != null
                && appProperties.getFootballData().getSeasons() != null
                ? appProperties.getFootballData().getSeasons().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(year -> !year.isEmpty())
                    .distinct()
                    .toList()
                : List.of();

        List<Season> seasons;
        if (!configuredSeasons.isEmpty()) {
            seasons = seasonRepository.findByYearInAndLeagueIsNotNullAndYearIsNotNull(configuredSeasons);
            log.info("Fetching standings for configured seasons {} ({} season records found)",
                    configuredSeasons, seasons.size());
        } else {
            seasons = seasonRepository.findByIsCurrentTrueAndLeagueIsNotNullAndYearIsNotNull();
            log.info("No historical seasons configured for provider {}; fetching current seasons only ({} season records found)",
                    selectedProvider, seasons.size());
        }
        for (Season season : seasons) {
            if (season.getLeague() == null || season.getYear() == null) continue;
            Long externalLeagueId = parseLong(leagueExternalIdsByLeagueId.get(season.getLeague().getId()));
            if (externalLeagueId == null) {
                skipped++;
                continue;
            }

            // This network call happens without a transaction. If football-data.org keeps
            // returning 429 after its retry/cooldown, stop this resource's sync but preserve
            // already persisted core data and allow the bootstrap job to finish.
            List<ProviderStanding> rows;
            try {
                rows = provider.fetchStandings(new StandingsQuery(externalLeagueId, season.getYear()));
            } catch (FootballDataRestrictedResourceException ex) {
                if (selectedProvider != Provider.FOOTBALL_DATA) throw ex;
                skipped++;
                log.warn("Skipping standings for league {} season {} because football-data.org restricts this resource: {}",
                        externalLeagueId, season.getYear(), ex.getMessage());
                continue;
            } catch (FootballDataRateLimitException ex) {
                if (selectedProvider != Provider.FOOTBALL_DATA) {
                    throw ex;
                }
                rateLimitInterrupted = true;
                log.warn("Stopping standings ingestion via {} after repeated rate limiting. "
                        + "Already persisted {} rows; remaining standings will be retried by a later sync. {}",
                        selectedProvider, saved, ex.getMessage());
                break;
            }
            if (rows == null || rows.isEmpty()) {
                skipped++;
                log.warn("Provider {} returned no standings for league {} season {}; existing standings are preserved",
                        selectedProvider, externalLeagueId, season.getYear());
                continue;
            }
            for (ProviderStanding row : rows) {
                Long teamId = teamIdsByExternalId.get(row.teamExternalId());
                if (teamId == null) {
                    skipped++;
                    continue;
                }
                standingsPersistenceService.upsertStanding(season.getId(), teamId, row);
                saved++;
            }
        }
        if (rateLimitInterrupted) {
            log.warn("Standings ingestion via {} partially completed. {} rows upserted, {} rows skipped.",
                    selectedProvider, saved, skipped);
        } else {
            log.info("Standings ingestion via {} completed. {} rows upserted, {} rows skipped.",
                    selectedProvider, saved, skipped);
        }
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
