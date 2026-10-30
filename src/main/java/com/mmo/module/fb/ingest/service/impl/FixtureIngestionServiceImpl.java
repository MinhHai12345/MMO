package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.ingest.provider.apifootball.client.ApiFootballPlanRestrictionException;
import com.mmo.module.fb.ingest.provider.client.ProviderTransportException;
import com.mmo.module.fb.ingest.provider.footballdata.client.FootballDataRestrictedResourceException;
import com.mmo.module.fb.ingest.provider.model.FixtureQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.FixturePersistenceService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixtureIngestionServiceImpl implements FixtureIngestionService {

    private final ProviderRegistry providerRegistry;
    private final AppProperties appProperties;
    private final IngestionServiceSupport ingestionSupport;
    private final TeamIngestionService teamIngestionService;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final FixturePersistenceService fixturePersistenceService;

    @Override
    public void syncCurrentSeasonFixtures(LocalDate from, LocalDate to) {
        syncFixturesForProvider(appProperties.getIngestion().getProviders().getFixture(), from, to);
    }

    /**
     * Coordinates upstream calls only. Each fixture is persisted in its own short transaction,
     * so slow provider requests do not hold a database transaction/connection open.
     */
    @Override
    public void syncFixturesForProvider(Provider selectedProvider, LocalDate from, LocalDate to) {
        ingestionSupport.requireProviderEnabled(selectedProvider, "fixture");
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be before or equal to 'to'");
        }

        // Recover from a cold start, but do not re-fetch every team's roster on each fixture poll.
        if (teamExternalIdRepository.findAllByProvider(selectedProvider).isEmpty()) {
            teamIngestionService.syncTeamsForProvider(selectedProvider);
        }

        List<Season> seasons = ingestionSupport.seasonsToSync(selectedProvider);
        Map<Long, com.mmo.module.fb.league.entity.LeagueExternalId> leagueMappingsByLeagueId = new HashMap<>();
        leagueExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping ->
                leagueMappingsByLeagueId.put(mapping.getLeague().getId(), mapping));
        Map<String, Long> teamIdsByExternalId = new HashMap<>();
        teamExternalIdRepository.findAllByProvider(selectedProvider).forEach(mapping ->
                teamIdsByExternalId.put(mapping.getExternalId(), mapping.getTeam().getId()));

        int saved = 0;
        int skipped = 0;
        int failed = 0;
        int fetched = 0;
        for (Season season : seasons) {
            var leagueMapping = leagueMappingsByLeagueId.get(season.getLeague().getId());
            Long externalLeagueId = IngestionServiceSupport.parseExternalId(
                    leagueMapping == null ? null : leagueMapping.getExternalId());
            if (externalLeagueId == null) {
                log.warn("Skipping season {} for league {} because provider {} has no league ID mapping",
                        season.getYear(), season.getLeague().getId(), selectedProvider);
                continue;
            }

            // Provider I/O deliberately occurs outside any persistence transaction.
            List<ProviderFixture> fixtures;
            try {
                fixtures = providerRegistry.fixture(selectedProvider).fetchFixtures(
                        new FixtureQuery(externalLeagueId, season.getYear(), from, to,
                                leagueMapping == null ? null : leagueMapping.getIdentify()));
            } catch (FootballDataRestrictedResourceException ex) {
                if (selectedProvider != Provider.FOOTBALL_DATA) throw ex;
                skipped++;
                log.warn("Skipping fixtures for league {} season {} because football-data.org restricts this resource: {}",
                        externalLeagueId, season.getYear(), ex.getMessage());
                continue;
            } catch (ApiFootballPlanRestrictionException ex) {
                skipped++;
                log.warn("Skipping fixtures for league {} season {} because the API-Football plan does not include it: {}",
                        externalLeagueId, season.getYear(), ex.getMessage());
                continue;
            } catch (ProviderTransportException ex) {
                if (selectedProvider == Provider.FOOTBALL_DATA) {
                    throw ex;
                }
                failed++;
                log.warn("Provider {} transport failed for league {} season {}. "
                                + "Continuing with remaining seasons; the next scheduled sync can retry this season.",
                        selectedProvider, externalLeagueId, season.getYear(), ex);
                continue;
            }
            fetched += fixtures.size();
            log.info("Fetched {} fixtures for league {} season {} from {}",
                    fixtures.size(), externalLeagueId, season.getYear(), selectedProvider);

            for (ProviderFixture fixture : fixtures) {
                if (fixture.externalId() == null || fixture.externalId().isBlank()) {
                    skipped++;
                    log.warn("Skipping fixture with empty external ID from {}", selectedProvider);
                    continue;
                }
                Long homeTeamId = teamIdsByExternalId.get(fixture.homeTeamExternalId());
                Long awayTeamId = teamIdsByExternalId.get(fixture.awayTeamExternalId());
                if (homeTeamId == null || awayTeamId == null) {
                    skipped++;
                    log.debug("Skipping fixture {} because one or both teams are not mapped",
                            fixture.externalId());
                    continue;
                }
                try {
                    if (fixturePersistenceService.persistFixture(
                            season.getId(), homeTeamId, awayTeamId, selectedProvider, fixture)) {
                        saved++;
                    } else {
                        skipped++;
                    }
                } catch (Exception ex) {
                    failed++;
                    log.error("Failed to persist provider {} fixture {}; continuing with remaining fixtures",
                            selectedProvider, fixture.externalId(), ex);
                }
            }
        }

        log.info("Fixture ingestion via {} finished. fetched={}, saved/updated={}, skipped={}, failed={}; date range {} to {}",
                selectedProvider, fetched, saved, skipped, failed, from, to);
    }


}
