package com.mmo.module.fb.ingest.application.service.ingestion;

import com.mmo.module.fb.ingest.application.exception.ProviderAccessException;
import com.mmo.module.fb.ingest.application.exception.ProviderTransportException;
import com.mmo.module.fb.domain.league.entity.LeagueExternalId;
import com.mmo.module.fb.ingest.application.service.support.IngestionServiceSupport;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.domain.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.ingest.provider.model.FixtureQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import com.mmo.module.fb.ingest.application.registry.ProviderIngestionRegistry;
import com.mmo.module.fb.ingest.application.port.in.FixtureIngestionService;
import com.mmo.module.fb.ingest.application.port.out.FixturePersistencePort;
import com.mmo.module.fb.ingest.application.model.ResolvedProviderFixture;
import com.mmo.module.fb.ingest.application.port.in.TeamIngestionService;
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

    private final ProviderIngestionRegistry providerRegistry;
    private final AppProperties appProperties;
    private final IngestionServiceSupport ingestionSupport;
    private final TeamIngestionService teamIngestionService;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final FixturePersistencePort fixturePersistenceService;

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
        syncFixtures(selectedProvider, from, to, false);
    }

    @Override
    public void syncAllSeasonsForProvider(Provider selectedProvider, LocalDate from, LocalDate to) {
        syncFixtures(selectedProvider, from, to, true);
    }

    @Override
    public void syncSeasonForProvider(Provider selectedProvider, Long seasonId, LocalDate from, LocalDate to) {
        if (seasonId == null) throw new IllegalArgumentException("seasonId must not be null");
        syncFixtures(selectedProvider, from, to, true, seasonId);
    }

    private void syncFixtures(Provider selectedProvider, LocalDate from, LocalDate to,
                              boolean includeHistoricalSeasons) {
        syncFixtures(selectedProvider, from, to, includeHistoricalSeasons, null);
    }

    private void syncFixtures(Provider selectedProvider, LocalDate from, LocalDate to,
                              boolean includeHistoricalSeasons, Long onlySeasonId) {
        ingestionSupport.requireProviderEnabled(selectedProvider, "fixture");
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be before or equal to 'to'");
        }

        // Recover from a cold start, but do not re-fetch every team's roster on each fixture poll.
        if (teamExternalIdRepository.findAllByProvider(selectedProvider).isEmpty()) {
            if (onlySeasonId != null) {
                teamIngestionService.syncSeasonForProvider(selectedProvider, onlySeasonId);
            } else if (includeHistoricalSeasons) {
                teamIngestionService.syncAllSeasonsForProvider(selectedProvider);
            } else {
                teamIngestionService.syncTeamsForProvider(selectedProvider);
            }
        }

        List<Season> seasons = includeHistoricalSeasons
                ? ingestionSupport.allMappedSeasonsToSync(selectedProvider, Dataset.FIXTURE)
                : ingestionSupport.seasonsToSync(selectedProvider, Dataset.FIXTURE);
        if (onlySeasonId != null) seasons = seasons.stream().filter(s -> onlySeasonId.equals(s.getId())).toList();
        if (seasons.isEmpty()) {
            if (!ingestionSupport.hasAnySeasons(includeHistoricalSeasons)) {
                throw new IllegalStateException("No canonical seasons available for fixture ingestion");
            }
            if (!ingestionSupport.hasAnySeasonOwnedBy(
                    selectedProvider, Dataset.FIXTURE, includeHistoricalSeasons)) {
                log.info("Skipping fixture ingestion via {}: selected seasons are owned by another provider",
                        selectedProvider);
                return;
            }
            throw new IllegalStateException("Missing " + selectedProvider
                    + " season mappings for a season it owns in fixture ingestion");
        }
        Map<Long, LeagueExternalId> leagueMappingsByLeagueId = new HashMap<>();
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
                if (selectedProvider == Provider.FOOTBALL_DATA) failed++;
                continue;
            }

            // Provider I/O deliberately occurs outside any persistence transaction.
            List<ProviderFixture> fixtures;
            try {
                fixtures = providerRegistry.fixture(selectedProvider).fetchFixtures(
                        new FixtureQuery(externalLeagueId, season.getYear(), from, to,
                                leagueMapping == null ? null : leagueMapping.getIdentify()));
            } catch (ProviderAccessException ex) {
                if (ex.is(ProviderAccessException.Kind.RESOURCE_RESTRICTED)) {
                    if (selectedProvider != Provider.FOOTBALL_DATA) throw ex;
                    skipped++;
                    log.warn("Skipping fixtures for league {} season {} because provider access is restricted: {}",
                            externalLeagueId, season.getYear(), ex.getMessage());
                    continue;
                }
                if (ex.is(ProviderAccessException.Kind.PLAN_RESTRICTED)) {
                    skipped++;
                    log.warn("Skipping fixtures for league {} season {} because the provider plan does not include it: {}",
                            externalLeagueId, season.getYear(), ex.getMessage());
                    continue;
                }
                throw ex;
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
            if (fixtures.isEmpty()) continue;

            List<ResolvedProviderFixture> resolved = new java.util.ArrayList<>();
            for (ProviderFixture fixture : fixtures) {
                if (fixture == null || fixture.externalId() == null || fixture.externalId().isBlank()) {
                    skipped++;
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
                resolved.add(new ResolvedProviderFixture(homeTeamId, awayTeamId, fixture));
            }

            // Keep transactions bounded and independent. A failed chunk does not block later chunks.
            final int persistenceBatchSize = 100;
            for (int offset = 0; offset < resolved.size(); offset += persistenceBatchSize) {
                List<ResolvedProviderFixture> batch = resolved.subList(
                        offset, Math.min(offset + persistenceBatchSize, resolved.size()));
                try {
                    int batchSaved = fixturePersistenceService.persistFixtures(
                            season.getId(), selectedProvider, batch);
                    saved += batchSaved;
                    skipped += batch.size() - batchSaved;
                } catch (Exception ex) {
                    failed += batch.size();
                    log.error("Failed to persist provider {} fixture batch for league={} season={} offset={}",
                            selectedProvider, externalLeagueId, season.getYear(), offset, ex);
                }
            }
        }

        log.info("Fixture ingestion via {} finished. fetched={}, saved/updated={}, skipped={}, failed={}; date range {} to {}",
                selectedProvider, fetched, saved, skipped, failed, from, to);
        if (selectedProvider == Provider.FOOTBALL_DATA && failed > 0) {
            throw new IllegalStateException("Football-Data fixture ingestion incomplete: failed=" + failed);
        }
    }


}
