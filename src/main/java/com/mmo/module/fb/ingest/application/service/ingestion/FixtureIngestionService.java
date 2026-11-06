package com.mmo.module.fb.ingest.application.service.ingestion;

import com.mmo.module.fb.ingest.application.exception.ProviderAccessException;
import com.mmo.module.fb.ingest.application.exception.ProviderTransportException;
import com.mmo.module.fb.ingest.application.service.support.IngestionServiceSupport;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.application.model.provider.FixtureQuery;
import com.mmo.module.fb.ingest.application.model.provider.ProviderFixture;
import com.mmo.module.fb.ingest.application.registry.ProviderIngestionRegistry;
import com.mmo.module.fb.ingest.application.port.in.FixtureIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.out.FixturePersistencePort;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointPort;
import com.mmo.module.fb.ingest.application.port.out.IngestionBatchTransactionPort;
import com.mmo.module.fb.ingest.application.model.IngestionCheckpointProgress;
import com.mmo.module.fb.ingest.application.port.out.LeagueExternalMappingPort;
import com.mmo.module.fb.ingest.application.port.out.TeamExternalMappingPort;
import com.mmo.module.fb.ingest.application.model.ResolvedProviderFixture;
import com.mmo.module.fb.ingest.application.port.in.TeamIngestionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixtureIngestionService implements FixtureIngestionUseCase {

    private final ProviderIngestionRegistry providerRegistry;
    private final AppProperties appProperties;
    private final IngestionServiceSupport ingestionSupport;
    private final TeamIngestionUseCase teamIngestionService;
    private final LeagueExternalMappingPort leagueExternalMappingPort;
    private final TeamExternalMappingPort teamExternalMappingPort;
    private final FixturePersistencePort fixturePersistenceService;
    private final ProviderCheckpointPort checkpointStore;
    private final IngestionBatchTransactionPort batchTransaction;

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
        if (teamExternalMappingPort.findAllByProvider(selectedProvider).isEmpty()) {
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
        Map<Long, LeagueExternalMappingPort.LeagueMapping> leagueMappingsByLeagueId = new HashMap<>();
        leagueExternalMappingPort.findAllByProvider(selectedProvider).forEach(mapping ->
                leagueMappingsByLeagueId.put(mapping.leagueId(), mapping));
        Map<String, Long> teamIdsByExternalId = new HashMap<>();
        teamExternalMappingPort.findAllByProvider(selectedProvider).forEach(mapping ->
                teamIdsByExternalId.put(mapping.externalId(), mapping.teamId()));

        int saved = 0;
        int skipped = 0;
        int failed = 0;
        int fetched = 0;
        for (Season season : seasons) {
            var leagueMapping = leagueMappingsByLeagueId.get(season.getLeague().getId());
            Long externalLeagueId = IngestionServiceSupport.parseExternalId(
                    leagueMapping == null ? null : leagueMapping.externalId());
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
                                leagueMapping == null ? null : leagueMapping.identify()));
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
            if (fixtures.isEmpty()) {
                checkpointStore.clearProgress(selectedProvider, Dataset.FIXTURE,
                        season.getLeague().getId(), season.getYear());
                continue;
            }

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

            // Every provider uses the same resumable chunk boundary. Data writes and cursor
            // advancement join one transaction; a failed commit leaves the previous cursor intact.
            int[] counts = persistResumableFixtureBatches(selectedProvider, season, resolved);
            saved += counts[0];
            skipped += counts[1];
        }

        log.info("Fixture ingestion via {} finished. fetched={}, saved/updated={}, skipped={}, failed={}; date range {} to {}",
                selectedProvider, fetched, saved, skipped, failed, from, to);
        if (selectedProvider == Provider.FOOTBALL_DATA && failed > 0) {
            throw new IllegalStateException("Football-Data fixture ingestion incomplete: failed=" + failed);
        }
    }

    private int[] persistResumableFixtureBatches(Provider provider, Season season,
                                                 List<ResolvedProviderFixture> rows) {
        Long leagueId = season.getLeague().getId();
        String seasonKey = season.getYear();
        if (rows == null || rows.isEmpty()) {
            checkpointStore.clearProgress(provider, Dataset.FIXTURE, leagueId, seasonKey);
            return new int[]{0, 0};
        }

        String fingerprint = fixtureFingerprint(rows);
        int startOffset = 0;
        long persistedBefore = 0;
        long failedBefore = 0;
        IngestionCheckpointProgress progress = checkpointStore.findProgress(
                provider, Dataset.FIXTURE, leagueId, seasonKey).orElse(null);
        if (progress != null) {
            int cursorOffset = parseFixtureCursor(progress.cursor(), fingerprint, rows.size());
            if (cursorOffset >= 0 && progress.processedRows() == cursorOffset) {
                startOffset = cursorOffset;
                persistedBefore = progress.persistedRows();
                failedBefore = progress.failedRows();
            } else {
                checkpointStore.clearProgress(provider, Dataset.FIXTURE, leagueId, seasonKey);
            }
        }

        int newlyPersisted = 0;
        int newlySkipped = 0;
        final int batchSize = 100;
        for (int offset = startOffset; offset < rows.size(); offset += batchSize) {
            final int batchStart = offset;
            final int batchEnd = Math.min(rows.size(), offset + batchSize);
            List<ResolvedProviderFixture> batch = List.copyOf(rows.subList(batchStart, batchEnd));
            final long persistedBase = persistedBefore;
            final long failedBase = failedBefore;
            int batchSaved = batchTransaction.execute(() -> {
                int count = fixturePersistenceService.persistFixtures(season.getId(), provider, batch);
                checkpointStore.saveProgress(provider, Dataset.FIXTURE, leagueId, seasonKey,
                        fingerprint + ":" + batchEnd, batchEnd,
                        persistedBase + count, failedBase + batch.size() - count);
                return count;
            });
            newlyPersisted += batchSaved;
            newlySkipped += batch.size() - batchSaved;
            persistedBefore += batchSaved;
            failedBefore += batch.size() - batchSaved;
        }
        checkpointStore.clearProgress(provider, Dataset.FIXTURE, leagueId, seasonKey);
        return new int[]{newlyPersisted, newlySkipped};
    }

    private static int parseFixtureCursor(String cursor, String fingerprint, int rowCount) {
        String prefix = fingerprint + ":";
        if (cursor == null || !cursor.startsWith(prefix)) return -1;
        try {
            int offset = Integer.parseInt(cursor.substring(prefix.length()));
            return offset >= 0 && offset <= rowCount ? offset : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static String fixtureFingerprint(List<ResolvedProviderFixture> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ResolvedProviderFixture row : rows) {
                String identity = row.homeTeamId() + "|" + row.awayTeamId() + "|"
                        + String.valueOf(row.fixture()) + "\n";
                digest.update(identity.getBytes(StandardCharsets.UTF_8));
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }


}
