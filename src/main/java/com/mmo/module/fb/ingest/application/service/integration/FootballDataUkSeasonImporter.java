package com.mmo.module.fb.ingest.application.service.integration;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.application.port.out.provider.SeasonBatchIngestionPort;
import com.mmo.module.fb.ingest.application.exception.IngestionPartialException;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointPort;
import com.mmo.module.fb.ingest.application.port.out.IngestionBatchTransactionPort;
import org.springframework.dao.DataAccessException;
import org.hibernate.LazyInitializationException;
import org.springframework.transaction.TransactionException;
import com.mmo.module.fb.ingest.application.model.IngestionCheckpointProgress;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.enums.CheckpointStatus;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkSeasonDataPort;
import com.mmo.module.fb.ingest.application.exception.ProviderDataException;
import com.mmo.module.fb.ingest.application.port.out.MatchXgPersistencePort;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkMatchStatisticsPersistencePort;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkOddsPersistencePort;
import com.mmo.module.fb.ingest.application.port.out.MmoDataStandardizationPort;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkImportPersistencePort;

import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.LeagueExternalId;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.entity.SeasonExternalId;




import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchExternalId;
import com.mmo.module.fb.domain.match.enums.MatchStatus;

import com.mmo.module.fb.domain.match.enums.MatchProvider;

import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.team.entity.TeamExternalId;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.IdentityHashMap;

/**
 * End-to-end Football-Data.co.uk importer:
 * active league -> season -> teams -> fixtures/mappings -> team statistics.
 *
 * Football-Data.co.uk has no stable numeric team/fixture IDs. Deterministic IDs are
 * therefore derived from its league code, season and normalized team/date identity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FootballDataUkSeasonImporter implements SeasonBatchIngestionPort {
    private static final Provider PROVIDER = Provider.FOOTBALL_DATA_UK;
    private static final MatchProvider MATCH_PROVIDER = MatchProvider.fromName(PROVIDER.name());
    private static final ZoneId SOURCE_ZONE = ZoneId.of("Europe/London");
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("dd/MM/yy");

    private final AppProperties properties;
    private final ProviderCheckpointPort checkpointStore;
    private final IngestionBatchTransactionPort batchTransaction;
    private final DataSourcePolicy dataSourcePolicy;
    private final FootballDataUkSeasonDataPort matchDataProvider;
    private final MmoDataStandardizationPort dataStandardizer;
    private final FootballDataUkImportPersistencePort persistencePort;
    private final MatchXgPersistencePort matchXgPersistenceService;
    private final FootballDataUkMatchStatisticsPersistencePort statisticsMapper;
    private final FootballDataUkOddsPersistencePort oddsMapper;

    /**
     * Initialize Football-Data.co.uk league mappings for leagues already present in MMO core.
     *
     * Safe to execute repeatedly: existing mappings are updated, missing mappings are inserted,
     * and unrelated provider mappings are never modified.
     */
    @Transactional
    public void initialLeagueMappings() {
        List<LeagueMappingDefinition> definitions = List.of(
                new LeagueMappingDefinition("Premier League", "E0"),
                new LeagueMappingDefinition("Championship", "E1"),
                new LeagueMappingDefinition("Bundesliga", "D1"),
                new LeagueMappingDefinition("Serie A", "I1"),
                new LeagueMappingDefinition("LaLiga", "SP1"),
                new LeagueMappingDefinition("Ligue 1", "F1")
        );

        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (LeagueMappingDefinition definition : definitions) {
            String canonicalLeagueName = dataStandardizer.leagueName(definition.leagueName());
            List<League> leagues = persistencePort.findAllLeagues().stream()
                    .filter(candidate -> canonicalLeagueName.equalsIgnoreCase(
                            dataStandardizer.leagueName(candidate.getName())))
                    .toList();
            if (leagues.size() != 1) {
                skipped++;
                log.warn("Skip Football-Data UK league mapping: name={}, matches={}",
                        definition.leagueName(), leagues.size());
                continue;
            }

            League league = leagues.get(0);
            Optional<LeagueExternalId> existing = persistencePort.findLeagueMapping(league.getId(), PROVIDER);

            if (existing.isPresent()) {
                LeagueExternalId mapping = existing.get();
                boolean changed = !definition.externalId().equals(mapping.getExternalId())
                        || !definition.identify().equals(mapping.getIdentify());
                if (changed) {
                    mapping.setExternalId(definition.externalId());
                    mapping.setIdentify(definition.identify());
                    persistencePort.saveLeagueMapping(mapping);
                    updated++;
                }
                continue;
            }

            Optional<LeagueExternalId> conflicting = persistencePort.findLeagueMappingByExternalId(PROVIDER, definition.externalId());
            if (conflicting.isPresent()) {
                log.error("Cannot initialize Football-Data UK mapping: provider externalId={} already belongs to leagueId={}",
                        definition.externalId(), conflicting.get().getLeague().getId());
                skipped++;
                continue;
            }

            persistencePort.saveLeagueMapping(LeagueExternalId.builder()
                    .league(league)
                    .provider(PROVIDER)
                    .externalId(definition.externalId())
                    .identify(definition.identify())
                    .build());
            created++;
        }

        log.info("Football-Data UK league mapping initialization completed: created={}, updated={}, skipped={}",
                created, updated, skipped);
    }

    private record LeagueMappingDefinition(String leagueName, String externalId, String identify) {
        private LeagueMappingDefinition(String leagueName, String code) {
            this(leagueName, code, code);
        }
    }

    /**
     * Creates the canonical season rows needed by the historical plan without importing CSVs.
     * The orchestrator can then schedule and checkpoint each league/season independently.
     */
    @Override
    public Provider getProvider() {
        return Provider.FOOTBALL_DATA_UK;
    }

    @Override
    public Set<Dataset> supportedDatasets() {
        return Set.of(Dataset.LEAGUE, Dataset.SEASON, Dataset.TEAM, Dataset.FIXTURE,
                Dataset.MATCH_STATISTICS, Dataset.MATCH_XG, Dataset.HISTORICAL_ODDS);
    }

    @Override
    public void prepareHistoricalSeasons() {
        initialLeagueMappings();
        AppProperties.ProviderConfig config = properties.getIngestion().getProviderConfig().get(PROVIDER);
        if (config == null || !config.isEnabled()) return;

        LocalDate today = LocalDate.now(SOURCE_ZONE);
        for (LeagueExternalId mapping : persistencePort.findLeagueMappings(PROVIDER)) {
            if (!StringUtils.hasText(mapping.getIdentify())) continue;
            for (int startYear = 2014; startYear <= today.getYear(); startYear++) {
                getOrCreateSeason(mapping.getLeague(), startYear, today);
            }
        }
    }

    /**
     * Imports exactly one canonical season. The CSV contains both teams and fixtures,
     * therefore it is fetched once, while TEAM/SEASON/FIXTURE checkpoints are persisted
     * independently from the resulting import counts.
     */
    @Override
    public void syncCoreSeason(Long seasonId, boolean historical) {
        if (seasonId == null) throw new IllegalArgumentException("seasonId must not be null");
        AppProperties.ProviderConfig config = properties.getIngestion().getProviderConfig().get(PROVIDER);
        if (config == null || !config.isEnabled()) return;

        Season season = persistencePort.findSeasonById(seasonId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown season id: " + seasonId));
        if (!historical && !season.isActive()) {
            log.debug("Skipping non-active Football-Data UK season {}", season.getYear());
            return;
        }
        // League is a LAZY association. Use its proxy identifier only; never dereference
        // League#getName here because this scheduler entry point may run detached.
        Long leagueId = season.getLeague().getId();
        LeagueExternalId mapping = persistencePort.findLeagueMapping(leagueId, PROVIDER)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing Football-Data UK league mapping for leagueId=" + leagueId));
        if (historical && isHistoricalSeasonImported(season, mapping)) {
            log.debug("Skipping terminal Football-Data UK Core season: leagueId={}, season={}",
                    leagueId, season.getYear());
            return;
        }
        markCoreCheckpoint(leagueId, season.getYear(), CheckpointStatus.RUNNING);
        try {
            ImportResult result = syncSeason(season, mapping, config, historical);
            if (result.csvRows() == 0) {
                markCoreCheckpoint(leagueId, season.getYear(), CheckpointStatus.NOT_AVAILABLE);
                return;
            }

            saveSeasonMapping(season, mapping);
            CheckpointStatus teamStatus = datasetStatus(result.csvRows(), result.unmatchedRows());
            CheckpointStatus fixtureStatus = datasetStatus(result.csvRows(), result.unmatchedRows());
            markCoreDatasetCheckpoints(leagueId, season.getYear(), CheckpointStatus.COMPLETED,
                    teamStatus, fixtureStatus);
            if (result.unmatchedRows() > 0) {
                throw new IngestionPartialException("Football-Data UK season has "
                        + result.unmatchedRows() + " unmatched CSV rows");
            }
        } catch (ProviderDataException exception) {
            if (exception.isNotAvailable()) {
                markCoreCheckpoint(leagueId, season.getYear(), CheckpointStatus.NOT_AVAILABLE);
                return;
            }
            markCoreCheckpoint(leagueId, season.getYear(), CheckpointStatus.FAILED);
            throw exception;
        } catch (IngestionPartialException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            try {
                markCoreCheckpoint(leagueId, season.getYear(), CheckpointStatus.FAILED);
            } catch (RuntimeException checkpointFailure) {
                exception.addSuppressed(checkpointFailure);
            }
            throw exception;
        }
    }

    /**
     * Enriches exactly one season. Historical and active invocations have separate skip
     * rules; each enrichment dataset receives its own checkpoint status.
     */
    @Override
    public void syncEnrichmentSeason(Long seasonId, boolean historical) {
        if (seasonId == null) throw new IllegalArgumentException("seasonId must not be null");
        AppProperties.ProviderConfig config = properties.getIngestion().getProviderConfig().get(PROVIDER);
        if (config == null || !config.isEnabled()) return;

        Season season = persistencePort.findSeasonById(seasonId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown season id: " + seasonId));
        if (historical && season.isActive()) return;
        if (!historical && !season.isActive()) return;

        // Do not initialize the LAZY League proxy outside the persistence session.
        Long leagueId = season.getLeague().getId();
        LeagueExternalId mapping = persistencePort.findLeagueMapping(leagueId, PROVIDER)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing Football-Data UK league mapping for leagueId=" + leagueId));
        if (historical && isEnrichmentCheckpointSatisfied(leagueId, season.getYear())) return;

        markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.RUNNING);
        try {
            List<Map<String, String>> rows = matchDataProvider.fetchSeason(
                    config.getUri(), season.getYear(), mapping.getIdentify());
            if (rows.isEmpty()) {
                markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.NOT_AVAILABLE);
                return;
            }
            EnrichmentResult result = enrichRows(season, mapping, rows);
            markEnrichmentCheckpoint(leagueId, season.getYear(), result);
            if (result.unmatchedRows() > 0) {
                throw new IngestionPartialException("Football-Data UK enrichment incomplete: "
                        + result.unmatchedSummary());
            }
        } catch (ProviderDataException exception) {
            if (exception.isNotAvailable()) {
                markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.NOT_AVAILABLE);
                return;
            }
            markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.FAILED);
            throw exception;
        } catch (IngestionPartialException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            try {
                markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.FAILED);
            } catch (RuntimeException checkpointFailure) {
                exception.addSuppressed(checkpointFailure);
            }
            throw exception;
        }
    }

    private static CheckpointStatus datasetStatus(int importedRows, int unmatchedRows) {
        if (importedRows == 0) {
            return unmatchedRows > 0 ? CheckpointStatus.PARTIAL : CheckpointStatus.NOT_AVAILABLE;
        }
        return unmatchedRows > 0 ? CheckpointStatus.PARTIAL : CheckpointStatus.COMPLETED;
    }

    /**
     * Bootstrap seasons from 2014 for every active league with a Football-Data.co.uk
     * identify code. Historical seasons are processed once; current season is refreshed.
     */
    /**
     * Synchronize all Football-Data UK mapped leagues and every available season from 2014
     * through the current season, regardless of the league/season active flags.
     * Historical seasons with a completed import marker are skipped on later executions;
     * the current season is refreshed on every run.
     */
    public boolean syncInitialData() {
        initialLeagueMappings();
        AppProperties.ProviderConfig config = properties.getIngestion().getProviderConfig().get(PROVIDER);
        if (config == null || !config.isEnabled()) {
            log.info("Football-Data.co.uk initial import disabled");
            return false;
        }

        if (persistencePort.findLeagueMappings(PROVIDER).isEmpty()) {
            throw new IllegalStateException("No Football-Data.co.uk league mappings are available after initialization");
        }

        LocalDate today = LocalDate.now(SOURCE_ZONE);
        int importedFixtures = 0;
        int failedSeasons = 0;
        int incompleteSeasons = 0;
        int unavailableSeasons = 0;

        for (LeagueExternalId mapping : persistencePort.findLeagueMappings(PROVIDER)) {
            League league = mapping.getLeague();
            if (!StringUtils.hasText(mapping.getIdentify())) {
                failedSeasons++;
                log.warn("Cannot complete Football-Data.co.uk backfill without identify mapping: leagueId={}, name={}",
                        league.getId(), league.getName());
                continue;
            }

            for (int startYear = 2014; startYear <= today.getYear(); startYear++) {
                Season season = getOrCreateSeason(league, startYear, today);
                boolean current = season.isCurrent();
                // Backfill is idempotent for every season. Active-season refresh is a
                // separate incremental workflow and must remain controlled by Season.active.
                if (isHistoricalSeasonImported(season, mapping)) continue;

                markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.RUNNING);
                try {
                    ImportResult result = syncSeason(season, mapping, config, true);
                    importedFixtures += result.createdFixtures();
                    if (result.csvRows() == 0) {
                        unavailableSeasons++;
                        markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.NOT_AVAILABLE);
                        log.info("Football-Data.co.uk CSV not available; skipping without failing backfill: league={}, season={}",
                                league.getName(), season.getYear());
                        continue;
                    }
                    if (result.unmatchedRows() == 0) {
                        saveSeasonMapping(season, mapping);
                        markCoreDatasetCheckpoints(league.getId(), season.getYear(),
                                CheckpointStatus.COMPLETED,
                                datasetStatus(result.csvRows(), result.unmatchedRows()),
                                datasetStatus(result.csvRows(), result.unmatchedRows()));
                    } else {
                        incompleteSeasons++;
                        markCoreDatasetCheckpoints(league.getId(), season.getYear(),
                                CheckpointStatus.PARTIAL, CheckpointStatus.PARTIAL, CheckpointStatus.PARTIAL);
                        log.warn("Football-Data.co.uk season remains partial: league={}, season={}, unmatched={}, rows={}",
                                league.getName(), season.getYear(), result.unmatchedRows(), result.csvRows());
                    }
                    log.info("Football-Data.co.uk imported league={}, season={}, current={}, rows={}, createdFixtures={}, unmatched={}",
                            league.getName(), season.getYear(), current, result.csvRows(),
                            result.createdFixtures(), result.unmatchedRows());
                } catch (ProviderDataException exception) {
                    if (exception.isNotAvailable()) {
                        unavailableSeasons++;
                        markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.NOT_AVAILABLE);
                        log.info("Football-Data.co.uk CSV not published: league={}, season={}, reason={}",
                                league.getName(), season.getYear(), exception.getMessage());
                    } else {
                        failedSeasons++;
                        markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.FAILED);
                        log.error("Football-Data.co.uk CSV failure: league={}, season={}, reason={}, retryable={}",
                                league.getName(), season.getYear(), exception.getFailureCode(),
                                exception.isRetryable(), exception);
                    }
                } catch (RuntimeException exception) {
                    failedSeasons++;
                    markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.FAILED);
                    log.error("Football-Data.co.uk import failed: league={}, season={}, reason={}",
                            league.getName(), season.getYear(), exception.getMessage(), exception);
                }
            }
        }

        log.info("Football-Data.co.uk Core import completed: createdFixtures={}, unavailableSeasons={}, failedSeasons={}, partialSeasons={}",
                importedFixtures, unavailableSeasons, failedSeasons, incompleteSeasons);
        if (failedSeasons > 0 || incompleteSeasons > 0) {
            throw new IllegalStateException("Football-Data.co.uk initial import incomplete: failedSeasons="
                    + failedSeasons + ", partialSeasons=" + incompleteSeasons
                    + ", unavailableSeasons=" + unavailableSeasons);
        }
        return true;
    }

    /** Compatibility entry point. New callers should choose initial or current-season scope explicitly. */
    public void syncAllData() {
        syncInitialData();
    }

    /**
     * Incremental CSV sync for operator-selected active seasons that Football-Data UK
     * is allowed to contribute to. The policy makes UK primary through 2022 and a
     * fixture fallback from 2023 onward.
     */
    public void syncActiveSeasonsData() {
        initialLeagueMappings();
        AppProperties.ProviderConfig config = properties.getIngestion().getProviderConfig().get(PROVIDER);
        if (config == null || !config.isEnabled()) {
            log.info("Football-Data.co.uk incremental import disabled");
            return;
        }

        List<LeagueExternalId> leagueMappings = persistencePort.findLeagueMappings(PROVIDER);
        if (leagueMappings.isEmpty()) {
            log.warn("Football-Data.co.uk active-season sync skipped: no provider league mappings exist");
            return;
        }

        int importedSeasons = 0;
        int failedSeasons = 0;
        for (LeagueExternalId mapping : leagueMappings) {
            if (!StringUtils.hasText(mapping.getIdentify())) continue;
            League league = mapping.getLeague();
            List<Season> activeSeasons = persistencePort.findSeasonsByLeagueId(league.getId()).stream()
                    .filter(Season::isActive)
                    .filter(season -> dataSourcePolicy.isAllowed(PROVIDER, Dataset.FIXTURE, season.getYear()))
                    .toList();

            for (Season season : activeSeasons) {
                markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.RUNNING);
                try {
                    ImportResult result = syncSeason(season, mapping, config, false);
                    if (result.csvRows() == 0) {
                        markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.NOT_AVAILABLE);
                        log.info("Football-Data.co.uk active-season CSV not available: league={}, season={}",
                                league.getName(), season.getYear());
                        continue;
                    }
                    if (result.unmatchedRows() > 0) {
                        failedSeasons++;
                        markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.PARTIAL);
                        log.warn("Football-Data.co.uk active-season sync remains partial: league={}, season={}, rows={}, unmatched={}",
                                league.getName(), season.getYear(), result.csvRows(), result.unmatchedRows());
                        continue;
                    }
                    saveSeasonMapping(season, mapping);
                    markCoreDatasetCheckpoints(league.getId(), season.getYear(),
                            CheckpointStatus.COMPLETED,
                            datasetStatus(result.csvRows(), result.unmatchedRows()),
                            datasetStatus(result.csvRows(), result.unmatchedRows()));
                    importedSeasons++;
                    log.info("Football-Data.co.uk active-season Core sync: league={}, season={}, rows={}, createdFixtures={}",
                            league.getName(), season.getYear(), result.csvRows(), result.createdFixtures());
                } catch (ProviderDataException exception) {
                    if (exception.isNotAvailable()) {
                        markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.NOT_AVAILABLE);
                        log.info("Football-Data.co.uk active-season CSV not published: league={}, season={}",
                                league.getName(), season.getYear());
                    } else {
                        failedSeasons++;
                        markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.FAILED);
                        log.error("Football-Data.co.uk active-season CSV failure: league={}, season={}, reason={}",
                                league.getName(), season.getYear(), exception.getFailureCode(), exception);
                    }
                } catch (RuntimeException exception) {
                    failedSeasons++;
                    markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.FAILED);
                    log.error("Football-Data.co.uk active-season sync failed: league={}, season={}, reason={}",
                            league.getName(), season.getYear(), exception.getMessage(), exception);
                }
            }
        }
        log.info("Football-Data.co.uk active-season sync completed: importedSeasons={}, failedSeasons={}",
                importedSeasons, failedSeasons);
        if (failedSeasons > 0) {
            throw new IllegalStateException("Football-Data.co.uk active-season sync failed for "
                    + failedSeasons + " season(s)");
        }
    }

    static int currentSeasonStartYear(LocalDate today) {
        return today.getMonthValue() >= 7 ? today.getYear() : today.getYear() - 1;
    }

    /**
     * One-time historical CSV enrichment. Core fixtures must already have been imported;
     * this method never creates teams or matches.
     */
    public boolean syncInitialEnrichment() {
        initialLeagueMappings();
        AppProperties.ProviderConfig config = properties.getIngestion().getProviderConfig().get(PROVIDER);
        if (config == null || !config.isEnabled()) return false;

        LocalDate today = LocalDate.now(SOURCE_ZONE);
        int failedSeasons = 0;
        int unmatchedRows = 0;
        int unavailableSeasons = 0;
        int importedStats = 0;
        int importedOdds = 0;
        List<LeagueExternalId> leagueMappings = persistencePort.findLeagueMappings(PROVIDER);
        if (leagueMappings.isEmpty()) {
            throw new IllegalStateException("No Football-Data.co.uk league mappings are available for enrichment");
        }

        for (LeagueExternalId leagueMapping : leagueMappings) {
            if (!StringUtils.hasText(leagueMapping.getIdentify())) {
                failedSeasons++;
                log.warn("Cannot enrich Football-Data UK league without identify mapping: league={}",
                        leagueMapping.getLeague().getName());
                continue;
            }
            Long leagueId = leagueMapping.getLeague().getId();
            for (int startYear = 2014; startYear <= today.getYear(); startYear++) {
                Season season = findSeason(leagueMapping.getLeague(), startYear);
                if (season == null) {
                    failedSeasons++;
                    log.warn("Cannot enrich Football-Data UK season because canonical Core season is missing: league={}, year={}",
                            leagueMapping.getLeague().getName(), startYear);
                    continue;
                }
                // The initial enrichment pass is historical-only. The active-season
                // workflow below owns refreshes for operator-selected seasons.
                if (season.isActive()) continue;
                if (isEnrichmentCheckpointSatisfied(leagueId, season.getYear())) continue;

                markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.RUNNING);
                try {
                    List<Map<String, String>> rows = matchDataProvider.fetchSeason(
                            config.getUri(), season.getYear(), leagueMapping.getIdentify());
                    if (rows.isEmpty()) {
                        unavailableSeasons++;
                        markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.NOT_AVAILABLE);
                        continue;
                    }
                    EnrichmentResult result = enrichRows(season, leagueMapping, rows);
                    importedStats += result.importedTeamStats();
                    importedOdds += result.importedOdds();
                    unmatchedRows += result.unmatchedRows();
                    markEnrichmentCheckpoint(leagueId, season.getYear(), result);
                } catch (ProviderDataException exception) {
                    if (exception.isNotAvailable()) {
                        unavailableSeasons++;
                        markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.NOT_AVAILABLE);
                        log.info("Football-Data UK enrichment CSV not published: league={}, season={}",
                                leagueMapping.getLeague().getName(), season.getYear());
                    } else {
                        failedSeasons++;
                        markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.FAILED);
                        log.error("Football-Data UK historical enrichment CSV failure: league={}, season={}, reason={}",
                                leagueMapping.getLeague().getName(), season.getYear(),
                                exception.getFailureCode(), exception);
                    }
                } catch (RuntimeException exception) {
                    failedSeasons++;
                    markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.FAILED);
                    log.error("Football-Data UK historical enrichment failed: league={}, season={}",
                            leagueMapping.getLeague().getName(), season.getYear(), exception);
                }
            }
        }
        log.info("Football-Data UK initial enrichment completed: stats={}, odds={}, unmatched={}, unavailableSeasons={}, failedSeasons={}",
                importedStats, importedOdds, unmatchedRows, unavailableSeasons, failedSeasons);
        if (failedSeasons > 0 || unmatchedRows > 0) {
            throw new IllegalStateException("Football-Data UK initial enrichment incomplete: failedSeasons="
                    + failedSeasons + ", unmatchedRows=" + unmatchedRows
                    + ", unavailableSeasons=" + unavailableSeasons);
        }
        return true;
    }

    /** Incremental match enrichment refreshes only operator-selected active UK-covered seasons. */
    public void syncActiveSeasonsEnrichment() {
        initialLeagueMappings();
        AppProperties.ProviderConfig config = properties.getIngestion().getProviderConfig().get(PROVIDER);
        if (config == null || !config.isEnabled()) return;

        int failed = 0;
        int enrichedSeasons = 0;
        for (LeagueExternalId leagueMapping : persistencePort.findLeagueMappings(PROVIDER)) {
            List<Season> activeSeasons = persistencePort.findSeasonsByLeagueId(
                            leagueMapping.getLeague().getId()).stream()
                    .filter(Season::isActive)
                    .filter(season -> dataSourcePolicy.isPrimary(
                            PROVIDER, Dataset.MATCH_STATISTICS, season.getYear()))
                    .toList();
            for (Season season : activeSeasons) {
                Long leagueId = leagueMapping.getLeague().getId();
                markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.RUNNING);
                try {
                    List<Map<String, String>> rows = matchDataProvider.fetchSeason(
                            config.getUri(), season.getYear(), leagueMapping.getIdentify());
                    if (rows.isEmpty()) {
                        markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.NOT_AVAILABLE);
                        continue;
                    }
                    EnrichmentResult result = enrichRows(season, leagueMapping, rows);
                    if (result.unmatchedRows() > 0) {
                        failed++;
                        markEnrichmentCheckpoint(leagueId, season.getYear(), result);
                    } else {
                        enrichedSeasons++;
                        markEnrichmentCheckpoint(leagueId, season.getYear(), result);
                    }
                    log.info("Football-Data UK active-season enrichment: league={}, season={}, rows={}, stats={}, odds={}, unmatched={}",
                            leagueMapping.getLeague().getName(), season.getYear(), rows.size(),
                            result.importedTeamStats(), result.importedOdds(), result.unmatchedRows());
                } catch (ProviderDataException exception) {
                    if (exception.isNotAvailable()) {
                        markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.NOT_AVAILABLE);
                        log.info("Football-Data UK active-season enrichment CSV not published: league={}, season={}",
                                leagueMapping.getLeague().getName(), season.getYear());
                    } else {
                        failed++;
                        markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.FAILED);
                        log.error("Football-Data UK active-season enrichment CSV failure: league={}, season={}, reason={}",
                                leagueMapping.getLeague().getName(), season.getYear(), exception.getFailureCode(), exception);
                    }
                } catch (RuntimeException exception) {
                    failed++;
                    markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.FAILED);
                    log.error("Football-Data UK active-season enrichment failed: league={}, season={}",
                            leagueMapping.getLeague().getName(), season.getYear(), exception);
                }
            }
        }
        log.info("Football-Data UK active-season enrichment completed: enrichedSeasons={}, failedSeasons={}",
                enrichedSeasons, failed);
        if (failed > 0) {
            throw new IllegalStateException("Football-Data UK active-season enrichment failed for "
                    + failed + " season(s)");
        }
    }

    private Season findSeason(League league, int startYear) {
        return persistencePort.findSeasonsByLeagueId(league.getId()).stream()
                .filter(candidate -> seasonStartYear(candidate.getYear()) == startYear)
                .findFirst()
                .orElse(null);
    }

    private EnrichmentResult enrichRows(
            Season season, LeagueExternalId leagueMapping, List<Map<String, String>> rows) {
        int importedStats = 0;
        int importedXg = 0;
        int importedOdds = 0;
        int invalidRows = 0;
        int teamNotFoundRows = 0;
        int fixtureNotFoundRows = 0;
        int ambiguousFixtureRows = 0;
        int diagnosticSamples = 0;

        long enrichmentStartedAt = System.nanoTime();
        // Team IDs, canonical names and provider mappings are loaded once per season.
        // Fixture mappings and candidate matches are then fetched once for the whole CSV.
        TeamCache teamCache = loadTeamCache(rows);
        List<PreparedEnrichmentRow> prepared = new ArrayList<>(rows.size());
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Map<String, String> row = rows.get(rowIndex);
            String homeName = row == null ? "" : valueAny(row, "HOMETEAM", "HOME TEAM");
            String awayName = row == null ? "" : valueAny(row, "AWAYTEAM", "AWAY TEAM");
            LocalDate date = row == null ? null : parseDate(value(row, "DATE"));
            if (row == null || !StringUtils.hasText(homeName)
                    || !StringUtils.hasText(awayName) || date == null) {
                invalidRows++;
                diagnosticSamples = logEnrichmentMismatch(season, rowIndex + 1, row,
                        "invalid date or missing home/away team", diagnosticSamples);
                continue;
            }

            Team home = findExistingTeamCached(leagueMapping.getIdentify(), homeName, teamCache);
            Team away = findExistingTeamCached(leagueMapping.getIdentify(), awayName, teamCache);
            if (home == null || away == null || home.getId().equals(away.getId())) {
                teamNotFoundRows++;
                diagnosticSamples = logEnrichmentMismatch(season, rowIndex + 1, row,
                        home == null || away == null ? "team mapping not found" : "home and away resolve to the same team",
                        diagnosticSamples);
                continue;
            }

            LocalTime time = parseTime(value(row, "TIME"));
            Instant kickoff = date.atTime(time == null ? LocalTime.NOON : time)
                    .atZone(SOURCE_ZONE).toInstant();
            String externalId = fixtureExternalId(
                    leagueMapping.getIdentify(), season.getYear(), home.getId(), away.getId(), date);
            prepared.add(new PreparedEnrichmentRow(rowIndex + 1, row, home, away, kickoff, externalId));
        }

        Set<String> externalIds = prepared.stream().map(PreparedEnrichmentRow::externalId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<String, Match> mappedMatches = persistencePort
                .findMatchMappingsByProviderAndExternalIds(MATCH_PROVIDER, externalIds).stream()
                .collect(java.util.stream.Collectors.toMap(MatchExternalId::getExternalId,
                        mapping -> mapping.getMatch(), (left, right) -> left));
        Set<Long> teamIds = prepared.stream()
                .flatMap(item -> java.util.stream.Stream.of(item.home().getId(), item.away().getId()))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Instant seasonStart = season.getStartDate() == null
                ? prepared.stream().map(PreparedEnrichmentRow::kickoff).min(Instant::compareTo)
                        .orElse(Instant.EPOCH).minusSeconds(36 * 3600L)
                : season.getStartDate().atStartOfDay(SOURCE_ZONE).toInstant();
        Instant seasonEnd = season.getEndDate() == null
                ? prepared.stream().map(PreparedEnrichmentRow::kickoff).max(Instant::compareTo)
                        .orElse(Instant.EPOCH).plusSeconds(36 * 3600L)
                : season.getEndDate().plusDays(1).atStartOfDay(SOURCE_ZONE).toInstant();
        List<Match> candidates = persistencePort.findBatchFixtureCandidates(
                season.getId(), teamIds, seasonStart, seasonEnd);
        Map<TeamPair, List<Match>> candidatesByPair = candidates.stream()
                .collect(java.util.stream.Collectors.groupingBy(candidate ->
                        new TeamPair(candidate.getHomeTeam().getId(), candidate.getAwayTeam().getId())));
        log.info("Football-Data UK enrichment prefetch completed: season={}, csvRows={}, preparedRows={}, "
                        + "mappedFixtures={}, candidateFixtures={}",
                season.getYear(), rows.size(), prepared.size(), mappedMatches.size(), candidates.size());

        List<MatchedEnrichmentRow> matchedRows = new ArrayList<>(prepared.size());
        for (PreparedEnrichmentRow item : prepared) {
            Match match = mappedMatches.get(item.externalId());
            if (match == null) {
                List<Match> sameDirection = candidatesByPair.getOrDefault(
                        new TeamPair(item.home().getId(), item.away().getId()), List.of());
                try {
                    match = closestFixtureCandidate(sameDirection, item.kickoff()).orElse(null);
                } catch (AmbiguousFixtureCandidateException ambiguous) {
                    ambiguousFixtureRows++;
                    diagnosticSamples = logEnrichmentMismatch(season, item.rowNumber(), item.row(),
                            ambiguous.getMessage(), diagnosticSamples);
                    continue;
                }
            }
            if (match == null) {
                fixtureNotFoundRows++;
                diagnosticSamples = logEnrichmentMismatch(season, item.rowNumber(), item.row(),
                        "canonical fixture not found within kickoff matching window", diagnosticSamples);
                continue;
            }
            matchedRows.add(new MatchedEnrichmentRow(match, item.row(),
                    decimalMetric(value(item.row(), "HXG")),
                    decimalMetric(value(item.row(), "AXG"))));
        }

        // The adapters process bounded fixture chunks. Each dataset now performs one
        // source-aware merge/prefetch and one bulk persistence pass per chunk, instead
        // of repeating locks, provenance, stats, bookmaker and quote reads per fixture.
        for (int batchStart = 0; batchStart < matchedRows.size(); batchStart += 100) {
            int batchEnd = Math.min(matchedRows.size(), batchStart + 100);
            long persistenceChunkStartedAt = System.nanoTime();
            List<MatchedEnrichmentRow> chunk = matchedRows.subList(batchStart, batchEnd);
            List<MatchXgPersistencePort.XgInput> xgInputs = new ArrayList<>(chunk.size());
            List<FootballDataUkMatchStatisticsPersistencePort.StatisticsInput> statsInputs =
                    new ArrayList<>(chunk.size());
            List<FootballDataUkOddsPersistencePort.OddsInput> oddsInputs = new ArrayList<>(chunk.size());

            for (MatchedEnrichmentRow item : chunk) {
                if (item.homeXg() != null || item.awayXg() != null) {
                    xgInputs.add(new MatchXgPersistencePort.XgInput(
                            item.match(), season.getYear(), item.homeXg(), item.awayXg()));
                }
                if (dataSourcePolicy.isPrimary(PROVIDER, Dataset.MATCH_STATISTICS, season.getYear())) {
                    statsInputs.add(new FootballDataUkMatchStatisticsPersistencePort.StatisticsInput(
                            item.match(), season.getYear(), item.row()));
                }
                if (dataSourcePolicy.isPrimary(PROVIDER, Dataset.HISTORICAL_ODDS, season.getYear())) {
                    oddsInputs.add(new FootballDataUkOddsPersistencePort.OddsInput(
                            item.match(), season.getYear(), item.row()));
                }
            }

            if (!xgInputs.isEmpty()) {
                matchXgPersistenceService.upsertAll(PROVIDER, xgInputs);
                importedXg += xgInputs.size();
            }
            if (!statsInputs.isEmpty()) importedStats += statisticsMapper.persistAll(statsInputs);
            if (!oddsInputs.isEmpty()) importedOdds += oddsMapper.persistAll(oddsInputs);

            long persistenceChunkMs = Math.max(1, (System.nanoTime() - persistenceChunkStartedAt) / 1_000_000);
            log.info("Football-Data UK enrichment persistence chunk: season={}, fixtures={}-{}, "
                            + "xgRows={}, statsRows={}, oddsRows={}, durationMs={}, fixturesPerSecond={}",
                    season.getYear(), batchStart + 1, batchEnd,
                    xgInputs.size(), statsInputs.size(), oddsInputs.size(), persistenceChunkMs,
                    Math.round(chunk.size() * 1000.0 / persistenceChunkMs));
        }
        long durationMs = Math.max(1, (System.nanoTime() - enrichmentStartedAt) / 1_000_000);
        log.info("Football-Data UK enrichment completed: season={}, csvRows={}, matchedRows={}, stats={}, "
                        + "xg={}, odds={}, unmatched={}, durationMs={}, rowsPerSecond={}",
                season.getYear(), rows.size(), matchedRows.size(),
                importedStats, importedXg, importedOdds,
                invalidRows + teamNotFoundRows + fixtureNotFoundRows + ambiguousFixtureRows,
                durationMs, Math.round(rows.size() * 1000.0 / durationMs));
        return new EnrichmentResult(rows.size(), importedStats, importedXg, importedOdds,
                invalidRows, teamNotFoundRows, fixtureNotFoundRows, ambiguousFixtureRows);
    }

    private Team findExistingTeamCached(String leagueCode, String providerName, TeamCache cache) {
        Team mapped = cache.byExternalId.get(normalize(leagueCode) + ":" + normalize(providerName));
        if (mapped != null) return mapped;
        String canonicalName = dataStandardizer.teamName(providerName);
        if (!StringUtils.hasText(canonicalName)) return null;
        mapped = cache.byExternalId.get("TEAM:" + normalize(canonicalName));
        if (mapped != null) return mapped;
        List<Team> exact = cache.byCanonicalName.getOrDefault(
                canonicalName.toLowerCase(Locale.ROOT), List.of());
        if (exact.size() == 1) return exact.get(0);
        if (exact.size() > 1) return null;
        List<Team> normalized = cache.byMatchKey.getOrDefault(
                dataStandardizer.teamNameMatchKey(canonicalName), List.of());
        return normalized.size() == 1 ? normalized.get(0) : null;
    }

    private record MatchedEnrichmentRow(Match match, Map<String, String> row,
                                        BigDecimal homeXg, BigDecimal awayXg) {}

    private record PreparedEnrichmentRow(int rowNumber, Map<String, String> row,
                                         Team home, Team away, Instant kickoff, String externalId) {}

    private int logEnrichmentMismatch(
            Season season, int rowNumber, Map<String, String> row, String reason, int sampleCount) {
        if (sampleCount >= 10) return sampleCount;
        log.warn("Football-Data UK enrichment row not matched: season={}, row={}, reason={}, date={}, time={}, home={}, away={}",
                season.getYear(), rowNumber, reason,
                row == null ? null : value(row, "DATE"),
                row == null ? null : value(row, "TIME"),
                row == null ? "" : valueAny(row, "HOMETEAM", "HOME TEAM"),
                row == null ? "" : valueAny(row, "AWAYTEAM", "AWAY TEAM"));
        return sampleCount + 1;
    }

    private record EnrichmentResult(
            int csvRows,
            int importedTeamStats,
            int importedXg,
            int importedOdds,
            int invalidRows,
            int teamNotFoundRows,
            int fixtureNotFoundRows,
            int ambiguousFixtureRows) {

        private int unmatchedRows() {
            return invalidRows + teamNotFoundRows + fixtureNotFoundRows + ambiguousFixtureRows;
        }

        private String unmatchedSummary() {
            return "unmatchedRows=" + unmatchedRows()
                    + " [invalidRows=" + invalidRows
                    + ", teamNotFoundRows=" + teamNotFoundRows
                    + ", fixtureNotFoundRows=" + fixtureNotFoundRows
                    + ", ambiguousFixtureRows=" + ambiguousFixtureRows + "]";
        }
    }

    static final class AmbiguousFixtureCandidateException extends IllegalStateException {
        AmbiguousFixtureCandidateException(String message) {
            super(message);
        }
    }

    public ImportResult syncSeason(Season season, LeagueExternalId leagueMapping,
                                   AppProperties.ProviderConfig config) {
        return syncSeason(season, leagueMapping, config, false);
    }

    /**
     * Processes historical CSVs in bounded chunks. The cursor is committed only after the
     * rows in that chunk have been persisted. A crash before cursor commit replays at most
     * one chunk; deterministic external identities make that replay idempotent.
     */
    private ImportResult syncSeason(Season season, LeagueExternalId leagueMapping,
                                    AppProperties.ProviderConfig config, boolean resumable) {
        List<Map<String, String>> rows = matchDataProvider.fetchSeason(
                config.getUri(), season.getYear(), leagueMapping.getIdentify());
        Long leagueId = season.getLeague().getId();
        String seasonYear = season.getYear();
        if (rows.isEmpty()) {
            checkpointStore.clearProgress(PROVIDER, Dataset.FIXTURE, leagueId, seasonYear);
            return new ImportResult(0, 0, 0, 0, 0);
        }

        String fingerprint = csvFingerprint(rows);
        long processedRows = 0;
        long persistedRows = 0;
        long failedRows = 0;
        int startOffset = 0;
        if (resumable) {
            IngestionCheckpointProgress progress = checkpointStore.findProgress(
                    PROVIDER, Dataset.FIXTURE, leagueId, seasonYear).orElse(null);
            int candidateOffset = progress == null ? -1
                    : parseCursorOffset(progress.cursor(), fingerprint, rows.size());
            if (progress != null && candidateOffset >= 0
                    && progress.processedRows() == candidateOffset) {
                startOffset = candidateOffset;
                processedRows = startOffset;
                persistedRows = progress.persistedRows();
                failedRows = progress.failedRows();
            } else if (progress != null) {
                // The source CSV changed; a previous row offset is no longer safe.
                checkpointStore.clearProgress(PROVIDER, Dataset.FIXTURE, leagueId, seasonYear);
            }
        } else {
            checkpointStore.clearProgress(PROVIDER, Dataset.FIXTURE, leagueId, seasonYear);
        }

        int createdFixtures = 0;
        Set<Long> resolvedTeamIds = new HashSet<>();
        int importedFixtures = Math.toIntExact(Math.min(Integer.MAX_VALUE, persistedRows));
        int unmatched = Math.toIntExact(Math.min(Integer.MAX_VALUE, failedRows));
        long teamPrefetchStartedAt = System.nanoTime();
        TeamCache teamCache = loadTeamCache(rows);
        log.info("Football-Data UK core prefetch completed: season={}, csvRows={}, teamPrefetchMs={}",
                seasonYear, rows.size(), (System.nanoTime() - teamPrefetchStartedAt) / 1_000_000);

        for (int batchStart = startOffset; batchStart < rows.size(); batchStart += 100) {
            final int batchStartOffset = batchStart;
            int batchEnd = Math.min(rows.size(), batchStartOffset + 100);
            final int persistedBeforeBatch = importedFixtures;
            final int unmatchedBeforeBatch = unmatched;
            final List<Map<String, String>> batchRows = rows.subList(batchStartOffset, batchEnd);
            final TeamCache currentTeamCache = teamCache;
            long batchStartedAt = System.nanoTime();
            BatchCounters batch = batchTransaction.execute(() -> {
                BatchCounters current = new BatchCounters();
                TeamCache batchTeamCache = currentTeamCache.copy();
                List<PreparedCoreRow> prepared = new ArrayList<>(batchRows.size());

                for (Map<String, String> row : batchRows) {
                    if (row == null) { current.unmatched++; continue; }
                    try {
                        String homeName = valueAny(row, "HOMETEAM", "HOME TEAM");
                        String awayName = valueAny(row, "AWAYTEAM", "AWAY TEAM");
                        LocalDate date = parseDate(value(row, "DATE"));
                        if (!StringUtils.hasText(homeName) || !StringUtils.hasText(awayName) || date == null) {
                            current.unmatched++;
                            continue;
                        }
                        Team home = resolveTeamCached(leagueMapping.getIdentify(), homeName, season, batchTeamCache);
                        Team away = resolveTeamCached(leagueMapping.getIdentify(), awayName, season, batchTeamCache);
                        current.resolvedTeamIds.add(home.getId());
                        current.resolvedTeamIds.add(away.getId());
                        if (home.getId().equals(away.getId())) { current.unmatched++; continue; }
                        LocalTime time = parseTime(value(row, "TIME"));
                        Instant kickoff = date.atTime(time == null ? LocalTime.NOON : time)
                                .atZone(SOURCE_ZONE).toInstant();
                        String externalId = fixtureExternalId(
                                leagueMapping.getIdentify(), season.getYear(), home.getId(), away.getId(), date);
                        prepared.add(new PreparedCoreRow(row, home, away, kickoff, externalId));
                    } catch (LazyInitializationException | DataAccessException | TransactionException infrastructure) {
                        throw infrastructure;
                    } catch (RuntimeException invalidRow) {
                        current.unmatched++;
                        log.warn("Skip invalid Football-Data.co.uk row: season={}, home={}, away={}, reason={}",
                                season.getYear(), value(row, "HOMETEAM"), value(row, "AWAYTEAM"),
                                invalidRow.getMessage());
                    }
                }

                Set<String> externalIds = prepared.stream().map(PreparedCoreRow::externalId)
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
                Map<String, MatchExternalId> mappingByExternalId =
                        persistencePort.findMatchMappingsByProviderAndExternalIds(MATCH_PROVIDER, externalIds)
                                .stream().collect(java.util.stream.Collectors.toMap(
                                        MatchExternalId::getExternalId, mapping -> mapping, (left, right) -> left));

                Set<Long> teamIds = prepared.stream()
                        .flatMap(item -> java.util.stream.Stream.of(item.home().getId(), item.away().getId()))
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
                Instant seasonStart = season.getStartDate() == null
                        ? prepared.stream().map(PreparedCoreRow::kickoff).min(Instant::compareTo)
                                .orElse(Instant.EPOCH).minusSeconds(36 * 3600L)
                        : season.getStartDate().atStartOfDay(SOURCE_ZONE).toInstant();
                Instant seasonEnd = season.getEndDate() == null
                        ? prepared.stream().map(PreparedCoreRow::kickoff).max(Instant::compareTo)
                                .orElse(Instant.EPOCH).plusSeconds(36 * 3600L)
                        : season.getEndDate().plusDays(1).atStartOfDay(SOURCE_ZONE).toInstant();
                List<Match> candidates = persistencePort.findBatchFixtureCandidates(
                        season.getId(), teamIds, seasonStart, seasonEnd);
                Map<TeamPair, List<Match>> candidatesByPair = candidates.stream()
                        .collect(java.util.stream.Collectors.groupingBy(candidate ->
                                new TeamPair(candidate.getHomeTeam().getId(), candidate.getAwayTeam().getId())));
                Set<Long> relevantMatchIds = new HashSet<>(candidates.stream()
                        .map(Match::getId).filter(java.util.Objects::nonNull).toList());
                mappingByExternalId.values().forEach(mapping -> relevantMatchIds.add(mapping.getMatch().getId()));
                List<Long> relevantIds = relevantMatchIds.stream().filter(java.util.Objects::nonNull).toList();
                Map<Long, MatchExternalId> ukMappingByMatchId = persistencePort
                        .findMatchMappingsByMatchIds(relevantIds, MATCH_PROVIDER).stream()
                        .collect(java.util.stream.Collectors.toMap(mapping -> mapping.getMatch().getId(),
                                mapping -> mapping, (left, right) -> left));
                Set<Long> footballDataMatchIds = persistencePort
                        .findMatchMappingsByMatchIds(relevantIds,
                                MatchProvider.fromName(Provider.FOOTBALL_DATA.name())).stream()
                        .map(mapping -> mapping.getMatch().getId())
                        .collect(java.util.stream.Collectors.toSet());

                Map<String, Match> matchByExternalId = new HashMap<>();
                mappingByExternalId.forEach((id, mapping) -> matchByExternalId.put(id, mapping.getMatch()));
                Map<String, Match> matchesToSaveByIdentity = new LinkedHashMap<>();
                Set<Match> newlyCreated = Collections.newSetFromMap(new IdentityHashMap<>());
                Map<Match, String> matchesNeedingMapping = new IdentityHashMap<>();
                List<MatchExternalId> mappingsToSave = new ArrayList<>();

                for (PreparedCoreRow item : prepared) {
                    try {
                        Match match = matchByExternalId.get(item.externalId());
                        if (match == null) {
                            List<Match> sameDirection = candidatesByPair.getOrDefault(
                                    new TeamPair(item.home().getId(), item.away().getId()), List.of());
                            match = closestFixtureCandidate(sameDirection, item.kickoff())
                                    .orElseGet(() -> Match.builder().season(season)
                                            .homeTeam(item.home()).awayTeam(item.away())
                                            .matchTime(item.kickoff()).status(MatchStatus.SCHEDULED).build());
                        }

                        boolean footballDataOwnsCore = match.getId() != null
                                && footballDataMatchIds.contains(match.getId());
                        boolean ukOwnsFixtureCore = dataSourcePolicy.isPrimary(
                                PROVIDER, Dataset.FIXTURE, season.getYear());
                        boolean mayUseUkFallback = dataSourcePolicy.mayFallbackForCore(
                                PROVIDER, Dataset.FIXTURE, season.getYear(), footballDataOwnsCore);
                        boolean preservePrimaryCore = !ukOwnsFixtureCore && !mayUseUkFallback;
                        if (!ukOwnsFixtureCore && !mayUseUkFallback && !footballDataOwnsCore) {
                            current.unmatched++;
                            continue;
                        }
                        if (!preservePrimaryCore || match.getMatchTime() == null) {
                            match.setMatchTime(item.kickoff());
                        }
                        updateFixtureResult(match, item.row(), preservePrimaryCore);
                        String identity = match.getId() == null
                                ? "new:" + System.identityHashCode(match) : "id:" + match.getId();
                        matchesToSaveByIdentity.put(identity, match);
                        matchByExternalId.put(item.externalId(), match);
                        if (match.getId() == null) newlyCreated.add(match);
                        MatchExternalId existingMapping = match.getId() == null
                                ? null : ukMappingByMatchId.get(match.getId());
                        if (existingMapping == null) {
                            matchesNeedingMapping.putIfAbsent(match, item.externalId());
                        } else if (!item.externalId().equals(existingMapping.getExternalId())
                                && !mappingsToSave.contains(existingMapping)) {
                            existingMapping.setExternalId(item.externalId());
                            mappingsToSave.add(existingMapping);
                        }
                        current.importedFixtures++;
                    } catch (LazyInitializationException | DataAccessException | TransactionException infrastructure) {
                        throw infrastructure;
                    } catch (RuntimeException invalidRow) {
                        current.unmatched++;
                        log.warn("Skip invalid Football-Data.co.uk row: season={}, home={}, away={}, reason={}",
                                season.getYear(), value(item.row(), "HOMETEAM"), value(item.row(), "AWAYTEAM"),
                                invalidRow.getMessage());
                    }
                }

                persistencePort.saveMatches(matchesToSaveByIdentity.values());
                current.createdFixtures = newlyCreated.size();
                for (Map.Entry<Match, String> entry : matchesNeedingMapping.entrySet()) {
                    if (entry.getKey().getId() != null) {
                        mappingsToSave.add(MatchExternalId.builder().match(entry.getKey())
                                .provider(MATCH_PROVIDER).externalId(entry.getValue()).build());
                    }
                }
                if (!mappingsToSave.isEmpty()) persistencePort.saveMatchMappings(mappingsToSave);
                current.teamCache = batchTeamCache;

                long processedAfterBatch = batchEnd;
                long persistedAfterBatch = (long) persistedBeforeBatch + current.importedFixtures;
                long failedAfterBatch = (long) unmatchedBeforeBatch + current.unmatched;
                checkpointStore.saveProgress(PROVIDER, Dataset.FIXTURE, leagueId, seasonYear,
                        fingerprint + ":" + batchEnd, processedAfterBatch,
                        persistedAfterBatch, failedAfterBatch);
                return current;
            });

            teamCache = batch.teamCache;
            long batchDurationMs = Math.max(1, (System.nanoTime() - batchStartedAt) / 1_000_000);
            log.info("Football-Data UK core batch completed: season={}, rows={}-{}, importedFixtures={}, "
                            + "createdFixtures={}, unmatched={}, durationMs={}, rowsPerSecond={}",
                    seasonYear, batchStartOffset + 1, batchEnd, batch.importedFixtures,
                    batch.createdFixtures, batch.unmatched, batchDurationMs,
                    Math.round((batchEnd - batchStartOffset) * 1000.0 / batchDurationMs));
            createdFixtures += batch.createdFixtures;
            importedFixtures += batch.importedFixtures;
            unmatched += batch.unmatched;
            resolvedTeamIds.addAll(batch.resolvedTeamIds);
        }

        checkpointStore.clearProgress(PROVIDER, Dataset.FIXTURE, leagueId, seasonYear);
        int importedTeams = resolvedTeamIds.size();
        return new ImportResult(rows.size(), createdFixtures, importedTeams, importedFixtures, unmatched);
    }

    static int parseCursorOffset(String cursor, String fingerprint, int rowCount) {
        String prefix = fingerprint + ":";
        if (cursor == null || !cursor.startsWith(prefix)) return -1;
        try {
            int offset = Integer.parseInt(cursor.substring(prefix.length()));
            return offset >= 0 && offset <= rowCount ? offset : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    static String csvFingerprint(List<Map<String, String>> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map<String, String> row : rows) {
                if (row != null) {
                    row.entrySet().stream().sorted(Map.Entry.comparingByKey())
                            .forEach(entry -> {
                                digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                                digest.update((byte) 0);
                                digest.update(String.valueOf(entry.getValue()).getBytes(StandardCharsets.UTF_8));
                                digest.update((byte) 0);
                            });
                }
                digest.update((byte) 0x7f);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable for CSV fingerprinting", exception);
        }
    }

    private Season getOrCreateSeason(League league, int startYear, LocalDate today) {
        String year = startYear + "/" + (startYear + 1);
        LocalDate start = LocalDate.of(startYear, 7, 1);
        LocalDate end = LocalDate.of(startYear + 1, 6, 30);
        boolean current = !today.isBefore(start) && !today.isAfter(end);

        Optional<Season> existing = persistencePort.findSeasonsByLeagueId(league.getId()).stream()
                .filter(candidate -> seasonStartYear(candidate.getYear()) == startYear)
                .findFirst();
        Season season;
        if (existing.isPresent()) {
            season = existing.get();
            boolean footballDataOwnsSeason = persistencePort.findSeasonMapping(season.getId(), Provider.FOOTBALL_DATA).isPresent();
            boolean mayUpdate = dataSourcePolicy.isPrimary(PROVIDER, Dataset.SEASON, season.getYear())
                    || dataSourcePolicy.mayFallbackForCore(
                            PROVIDER, Dataset.SEASON, season.getYear(), footballDataOwnsSeason);
            if (!mayUpdate) return season;
        } else {
            // UK may create a season only when it owns the season dataset or is the
            // explicitly permitted fallback because the primary provider has no identity.
            Season candidate = Season.builder()
                    .league(league).year(year).startDate(start).endDate(end).build();
            boolean mayCreate = dataSourcePolicy.isPrimary(PROVIDER, Dataset.SEASON, candidate.getYear())
                    || dataSourcePolicy.mayFallbackForCore(
                            PROVIDER, Dataset.SEASON, candidate.getYear(), false);
            if (!mayCreate) {
                throw new IllegalStateException("Football-Data UK is not allowed to create season "
                        + year + " for league " + league.getName());
            }
            // Default a newly discovered current season to active. Once persisted, the
            // active flag is operator-controlled and later syncs never change it.
            season = candidate;
            season.setActive(current);
        }

        // isCurrent is derived from the calendar. isActive is operator-controlled after creation.
        season.setCurrent(current);
        if (season.getStartDate() == null) season.setStartDate(start);
        if (season.getEndDate() == null) season.setEndDate(end);
        return persistencePort.saveSeason(season);
    }

    private void saveSeasonMapping(Season season, LeagueExternalId leagueMapping) {
        // SeasonExternalId stores provider identity only. Import completion belongs in
        // fb_provider_sync_checkpoints, never in a synthetic ":CORE2" suffix.
        String externalId = leagueMapping.getIdentify().trim().toUpperCase(Locale.ROOT)
                + ":" + seasonCode(season.getYear());
        SeasonExternalId mapping = persistencePort.findSeasonMapping(season.getId(), PROVIDER)
                .orElseGet(() -> SeasonExternalId.builder()
                        .season(season).provider(PROVIDER).build());
        mapping.setExternalId(externalId);
        persistencePort.saveSeasonMapping(mapping);
    }

    private boolean isHistoricalSeasonImported(Season season, LeagueExternalId leagueMapping) {
        Long leagueId = leagueMapping.getLeague().getId();
        String seasonYear = season.getYear();
        boolean teamSatisfied = checkpointStore.isSatisfied(PROVIDER, Dataset.TEAM, leagueId, seasonYear);
        boolean seasonSatisfied = checkpointStore.isSatisfied(PROVIDER, Dataset.SEASON, leagueId, seasonYear);
        boolean fixtureSatisfied = checkpointStore.isSatisfied(PROVIDER, Dataset.FIXTURE, leagueId, seasonYear);
        if (teamSatisfied && seasonSatisfied && fixtureSatisfied) return true;

        // Migrate the previous season-scoped key and legacy :CORE2 marker into the
        // structured dataset/league/season checkpoint namespace.
        String legacyKey = seasonCheckpointKey(leagueMapping.getLeague(), seasonStartYear(seasonYear));
        boolean legacyCheckpoint = checkpointStore.isCompleted(PROVIDER, legacyKey);
        Optional<SeasonExternalId> legacyMapping = persistencePort.findSeasonMapping(season.getId(), PROVIDER);
        boolean legacyCore2 = legacyMapping.isPresent()
                && legacyMapping.get().getExternalId() != null
                && legacyMapping.get().getExternalId().endsWith(":CORE2");
        if (legacyCheckpoint || legacyCore2) {
            if (legacyCore2) saveSeasonMapping(season, leagueMapping);
            markCoreCheckpoint(leagueId, seasonYear, CheckpointStatus.COMPLETED);
            log.info("Migrated legacy Football-Data UK Core checkpoint: league={}, season={}",
                    leagueMapping.getLeague().getName(), seasonYear);
            return true;
        }
        return false;
    }

    private void markCoreCheckpoint(Long leagueId, String season, CheckpointStatus status) {
        markCoreDatasetCheckpoints(leagueId, season, status, status, status);
    }

    private void markCoreDatasetCheckpoints(Long leagueId, String season,
                                            CheckpointStatus seasonStatus,
                                            CheckpointStatus teamStatus,
                                            CheckpointStatus fixtureStatus) {
        checkpointStore.markStatus(PROVIDER, Dataset.SEASON, leagueId, season, seasonStatus);
        checkpointStore.markStatus(PROVIDER, Dataset.TEAM, leagueId, season, teamStatus);
        checkpointStore.markStatus(PROVIDER, Dataset.FIXTURE, leagueId, season, fixtureStatus);
    }

    private boolean isEnrichmentCheckpointSatisfied(Long leagueId, String season) {
        return checkpointStore.isSatisfied(PROVIDER, Dataset.MATCH_STATISTICS, leagueId, season)
                && checkpointStore.isSatisfied(PROVIDER, Dataset.MATCH_XG, leagueId, season)
                && checkpointStore.isSatisfied(PROVIDER, Dataset.HISTORICAL_ODDS, leagueId, season);
    }

    private void markEnrichmentCheckpoint(Long leagueId, String season, CheckpointStatus status) {
        checkpointStore.markStatus(PROVIDER, Dataset.MATCH_STATISTICS, leagueId, season, status);
        checkpointStore.markStatus(PROVIDER, Dataset.MATCH_XG, leagueId, season, status);
        checkpointStore.markStatus(PROVIDER, Dataset.HISTORICAL_ODDS, leagueId, season, status);
    }

    private void markEnrichmentCheckpoint(Long leagueId, String season, EnrichmentResult result) {
        CheckpointStatus partialOrComplete = result.unmatchedRows() == 0
                ? CheckpointStatus.COMPLETED : CheckpointStatus.PARTIAL;
        checkpointStore.markStatus(PROVIDER, Dataset.MATCH_STATISTICS, leagueId, season,
                result.importedTeamStats() == 0 ? CheckpointStatus.NOT_AVAILABLE : partialOrComplete);
        checkpointStore.markStatus(PROVIDER, Dataset.MATCH_XG, leagueId, season,
                result.importedXg() == 0 ? CheckpointStatus.NOT_AVAILABLE : partialOrComplete);
        checkpointStore.markStatus(PROVIDER, Dataset.HISTORICAL_ODDS, leagueId, season,
                result.importedOdds() == 0 ? CheckpointStatus.NOT_AVAILABLE : partialOrComplete);
    }

    private static String seasonCheckpointKey(League league, int startYear) {
        return "UK_CORE_" + league.getId() + "_" + startYear;
    }

    private TeamCache loadTeamCache(List<Map<String, String>> rows) {
        TeamCache cache = new TeamCache();
        persistencePort.findTeamMappings(PROVIDER).forEach(cache::addMapping);
        persistencePort.findTeamMappings(Provider.FOOTBALL_DATA).forEach(cache::addMapping);
        // Load the canonical identity catalogue as existing core names may predate
        // the current provider aliases and therefore miss exact-name prefetch.
        persistencePort.findAllTeams().forEach(cache::addTeam);

        Set<String> canonicalNames = new HashSet<>();
        for (Map<String, String> row : rows) {
            if (row == null) continue;
            String home = dataStandardizer.teamName(valueAny(row, "HOMETEAM", "HOME TEAM"));
            String away = dataStandardizer.teamName(valueAny(row, "AWAYTEAM", "AWAY TEAM"));
            if (StringUtils.hasText(home)) canonicalNames.add(home);
            if (StringUtils.hasText(away)) canonicalNames.add(away);
        }
        persistencePort.findTeamsByNames(canonicalNames).forEach(cache::addTeam);
        return cache;
    }

    private Team resolveTeamCached(String leagueCode, String providerName, Season season, TeamCache cache) {
        String legacyExternalId = normalize(leagueCode) + ":" + normalize(providerName);
        Team mapped = cache.byExternalId.get(legacyExternalId);
        if (mapped != null) return mapped;

        String canonicalName = dataStandardizer.teamName(providerName);
        if (!StringUtils.hasText(canonicalName)) {
            throw new IllegalArgumentException("Football-Data UK team name is blank");
        }
        String canonicalExternalId = "TEAM:" + normalize(canonicalName);
        mapped = cache.byExternalId.get(canonicalExternalId);
        if (mapped != null) return mapped;

        List<Team> exact = cache.byCanonicalName.getOrDefault(canonicalName.toLowerCase(Locale.ROOT), List.of());
        if (exact.size() > 1) {
            throw new IllegalStateException("Ambiguous canonical team name from Football-Data UK: "
                    + canonicalName + " (matches=" + exact.size() + ")");
        }
        Team team = exact.isEmpty() ? null : exact.get(0);
        if (team == null) {
            List<Team> normalized = cache.byMatchKey.getOrDefault(
                    dataStandardizer.teamNameMatchKey(canonicalName), List.of());
            if (normalized.size() > 1) {
                throw new IllegalStateException("Ambiguous normalized team identity from Football-Data UK: "
                        + canonicalName + " (matches=" + normalized.size() + ")");
            }
            if (normalized.size() == 1) team = normalized.get(0);
        }
        boolean footballDataOwnsTeam = team != null && cache.footballDataOwnedTeamIds.contains(team.getId());
        boolean ukOwnsTeam = dataSourcePolicy.isPrimary(PROVIDER, Dataset.TEAM, season.getYear());
        boolean mayUseUkFallback = dataSourcePolicy.mayFallbackForCore(
                PROVIDER, Dataset.TEAM, season.getYear(), footballDataOwnsTeam);
        if (team == null) {
            if (!ukOwnsTeam && !mayUseUkFallback) {
                throw new IllegalStateException("Football-Data UK is not allowed to create team "
                        + canonicalName + " for season " + season.getYear());
            }
            team = persistencePort.saveTeam(Team.builder()
                    .name(canonicalName)
                    .shortName(canonicalName.length() > 80 ? canonicalName.substring(0, 80) : canonicalName)
                    .slug(slug(canonicalName))
                    .isActive(true)
                    .build());
            cache.addTeam(team);
        }

        if (!cache.ukMappedTeamIds.contains(team.getId())) {
            persistencePort.registerTeamExternalId(team, PROVIDER, canonicalExternalId);
            cache.ukMappedTeamIds.add(team.getId());
            cache.byExternalId.put(canonicalExternalId, team);
        }
        return team;
    }

    private record TeamPair(Long homeTeamId, Long awayTeamId) {}

    private record PreparedCoreRow(Map<String, String> row, Team home, Team away,
                                   Instant kickoff, String externalId) {}

    private final class TeamCache {
        private final Map<String, Team> byExternalId = new HashMap<>();
        private final Map<String, List<Team>> byCanonicalName = new HashMap<>();
        private final Map<String, List<Team>> byMatchKey = new HashMap<>();
        private final Set<Long> ukMappedTeamIds = new HashSet<>();
        private final Set<Long> footballDataOwnedTeamIds = new HashSet<>();

        private TeamCache copy() {
            TeamCache copy = new TeamCache();
            copy.byExternalId.putAll(byExternalId);
            byCanonicalName.forEach((name, teams) -> copy.byCanonicalName.put(name, new ArrayList<>(teams)));
            byMatchKey.forEach((key, teams) -> copy.byMatchKey.put(key, new ArrayList<>(teams)));
            copy.ukMappedTeamIds.addAll(ukMappedTeamIds);
            copy.footballDataOwnedTeamIds.addAll(footballDataOwnedTeamIds);
            return copy;
        }

        private void addMapping(TeamExternalId mapping) {
            if (mapping == null || mapping.getTeam() == null || mapping.getTeam().getId() == null) return;
            if (mapping.getProvider() == PROVIDER) {
                byExternalId.put(mapping.getExternalId(), mapping.getTeam());
                ukMappedTeamIds.add(mapping.getTeam().getId());
            } else if (mapping.getProvider() == Provider.FOOTBALL_DATA) {
                footballDataOwnedTeamIds.add(mapping.getTeam().getId());
            }
        }

        private void addTeam(Team team) {
            if (team == null || team.getName() == null) return;
            List<Team> teams = byCanonicalName.computeIfAbsent(
                    team.getName().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>());
            if (teams.stream().noneMatch(existing -> existing.getId().equals(team.getId()))) teams.add(team);
            String matchKey = dataStandardizer.teamNameMatchKey(team.getName());
            if (StringUtils.hasText(matchKey)) {
                List<Team> matches = byMatchKey.computeIfAbsent(matchKey, ignored -> new ArrayList<>());
                if (matches.stream().noneMatch(existing -> existing.getId().equals(team.getId()))) {
                    matches.add(team);
                }
            }
        }
    }

    static Optional<Match> closestFixtureCandidate(List<Match> candidates, Instant kickoff) {
        if (candidates == null || candidates.isEmpty() || kickoff == null) {
            return Optional.empty();
        }

        List<Match> nearby = candidates.stream()
                .filter(candidate -> candidate != null && candidate.getMatchTime() != null)
                .filter(candidate -> Duration.between(kickoff, candidate.getMatchTime()).abs()
                        .compareTo(Duration.ofHours(36)) <= 0)
                .toList();
        if (nearby.isEmpty()) {
            return Optional.empty();
        }

        Duration closestDistance = nearby.stream()
                .map(candidate -> Duration.between(kickoff, candidate.getMatchTime()).abs())
                .min(Duration::compareTo)
                .orElseThrow();
        List<Match> closest = nearby.stream()
                .filter(candidate -> Duration.between(kickoff, candidate.getMatchTime()).abs()
                        .equals(closestDistance))
                .toList();
        if (closest.size() > 1) {
            String candidateIds = closest.stream()
                    .map(candidate -> String.valueOf(candidate.getId()))
                    .toList().toString();
            throw new AmbiguousFixtureCandidateException("Ambiguous fixture candidates for kickoff="
                    + kickoff + ", equally close matchIds=" + candidateIds);
        }
        return Optional.of(closest.get(0));
    }

    private void updateFixtureResult(Match match, Map<String, String> row, boolean preservePrimaryProvider) {
        Integer homeScore = integer(valueAny(row, "FTHG", "HG"));
        Integer awayScore = integer(valueAny(row, "FTAG", "AG"));
        String fullTimeResult = valueAny(row, "FTR", "RES").toUpperCase(Locale.ROOT);
        Integer homeHalfTimeScore = integer(value(row, "HTHG"));
        Integer awayHalfTimeScore = integer(value(row, "HTAG"));
        String halfTimeResult = value(row, "HTR").toUpperCase(Locale.ROOT);
        Integer attendance = integer(value(row, "ATTENDANCE"));
        String referee = value(row, "REFEREE");

        if ((!preservePrimaryProvider || match.getHomeScore() == null) && homeScore != null) {
            match.setHomeScore(homeScore);
        }
        if ((!preservePrimaryProvider || match.getAwayScore() == null) && awayScore != null) {
            match.setAwayScore(awayScore);
        }
        if ((!preservePrimaryProvider || match.getHomeHalfTimeScore() == null) && homeHalfTimeScore != null) {
            match.setHomeHalfTimeScore(homeHalfTimeScore);
        }
        if ((!preservePrimaryProvider || match.getAwayHalfTimeScore() == null) && awayHalfTimeScore != null) {
            match.setAwayHalfTimeScore(awayHalfTimeScore);
        }
        if ((!preservePrimaryProvider || match.getAttendance() == null) && attendance != null) {
            match.setAttendance(attendance);
        }
        if ((!preservePrimaryProvider || match.getReferee() == null) && StringUtils.hasText(referee)) {
            match.setReferee(referee.trim());
        }
        if ((!preservePrimaryProvider || match.getHalfTimeResult() == null)
                && List.of("H", "D", "A").contains(halfTimeResult)) {
            match.setHalfTimeResult(halfTimeResult);
        }
        if ((!preservePrimaryProvider || match.getFullTimeResult() == null)
                && List.of("H", "D", "A").contains(fullTimeResult)) {
            match.setFullTimeResult(fullTimeResult);
        }
        if ((!preservePrimaryProvider || match.getStatus() == null)
                && match.getHomeScore() != null && match.getAwayScore() != null
                && List.of("H", "D", "A").contains(fullTimeResult)) {
            match.setStatus(MatchStatus.FINISHED);
        }
    }

    private static BigDecimal decimalSigned(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Parse non-negative statistics including a legitimate 0.000 xG value. */
    private static BigDecimal decimalMetric(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            BigDecimal parsed = new BigDecimal(raw.trim());
            return parsed.signum() >= 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int seasonStartYear(String season) {
        if (!StringUtils.hasText(season)) return -1;
        String digits = season.replaceAll("\\D", "");
        if (digits.length() < 4) return -1;
        try {
            return Integer.parseInt(digits.substring(0, 4));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String seasonCode(String season) {
        String digits = season.replaceAll("\\D", "");
        if (digits.length() < 4) throw new IllegalArgumentException("Unsupported season format: " + season);
        int startYear = Integer.parseInt(digits.substring(0, 4));
        return digits.substring(2, 4) + String.format(Locale.ROOT, "%02d", (startYear + 1) % 100);
    }

    /**
     * Football-Data UK has no stable fixture ID. Include the source kickoff date so
     * repeated home/away pairings in one season (league plus cup/play-offs) do not
     * collapse into the same provider mapping. Keep team IDs canonical across aliases.
     */
    static String fixtureExternalId(
            String leagueCode, String season, Long homeTeamId, Long awayTeamId, LocalDate kickoffDate) {
        if (kickoffDate == null) {
            throw new IllegalArgumentException("kickoffDate must not be null");
        }
        return normalize(leagueCode) + ":" + seasonCode(season) + ":" + kickoffDate + ":"
                + homeTeamId + ":" + awayTeamId;
    }

    private static final List<DateTimeFormatter> SUPPORTED_CSV_DATES = List.of(
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT),
            CSV_DATE,
            DateTimeFormatter.ISO_LOCAL_DATE
    );

    private static LocalDate parseDate(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String value = raw.trim();
        for (DateTimeFormatter formatter : SUPPORTED_CSV_DATES) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported Football-Data date representation.
            }
        }
        return null;
    }

    private static LocalTime parseTime(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try { return LocalTime.parse(raw.trim()); }
        catch (DateTimeParseException ignored) { return null; }
    }

    private static Integer integer(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            double parsed = Double.parseDouble(raw.trim().replace(",", ""));
            if (!Double.isFinite(parsed) || parsed < 0 || parsed != Math.rint(parsed)
                    || parsed > Integer.MAX_VALUE) return null;
            return (int) parsed;
        } catch (NumberFormatException ex) { return null; }
    }

    private static String value(Map<String, String> row, String key) {
        return row.getOrDefault(key, "").trim();
    }

    private static String valueAny(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String candidate = row.getOrDefault(key, "").trim();
            if (StringUtils.hasText(candidate)) return candidate;
        }
        return "";
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    private static String slug(String value) {
        return normalize(value).replaceAll("([a-z])([0-9])", "$1-$2");
    }

    private static final class BatchCounters {
        private int createdFixtures;
        private int importedFixtures;
        private int unmatched;
        private TeamCache teamCache;
        private final Set<Long> resolvedTeamIds = new HashSet<>();
    }

    public record ImportResult(int csvRows, int createdFixtures, int importedTeams,
                               int importedFixtures, int unmatchedRows) {}
}
