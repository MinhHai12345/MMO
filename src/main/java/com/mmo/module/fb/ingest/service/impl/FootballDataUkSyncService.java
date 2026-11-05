package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointStore;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.enums.CheckpointStatus;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.provider.footballdatauk.FootballDataUkMatchDataProvider;
import com.mmo.module.fb.ingest.provider.footballdatauk.FootballDataUkCsvException;
import com.mmo.module.fb.ingest.persistence.MatchXgPersistenceService;
import com.mmo.module.fb.ingest.persistence.footballdatauk.FootballDataUkMatchStatisticsPersistenceService;
import com.mmo.module.fb.ingest.persistence.footballdatauk.FootballDataUkOddsPersistenceService;
import com.mmo.module.fb.ingest.provider.model.MmoProviderDataStandardizer;
import com.mmo.module.fb.ingest.service.ExternalIdMappingService;
import com.mmo.module.fb.league.entity.League;
import com.mmo.module.fb.league.entity.LeagueExternalId;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.league.entity.SeasonExternalId;
import com.mmo.module.fb.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.league.repository.LeagueRepository;
import com.mmo.module.fb.league.repository.SeasonRepository;
import com.mmo.module.fb.league.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchExternalId;
import com.mmo.module.fb.match.enums.MatchStatus;
import com.mmo.module.fb.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.match.enums.MatchProvider;
import com.mmo.module.fb.match.repository.MatchRepository;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.team.entity.TeamExternalId;
import com.mmo.module.fb.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
public class FootballDataUkSyncService implements FootballDataUkIngestionPort {
    private static final Provider PROVIDER = Provider.FOOTBALL_DATA_UK;
    private static final MatchProvider MATCH_PROVIDER = MatchProvider.fromName(PROVIDER.name());
    private static final ZoneId SOURCE_ZONE = ZoneId.of("Europe/London");
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("dd/MM/yy");

    private final AppProperties properties;
    private final ProviderCheckpointStore checkpointStore;
    private final DataSourcePolicy dataSourcePolicy;
    private final FootballDataUkMatchDataProvider matchDataProvider;
    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final SeasonExternalIdRepository seasonExternalIdRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamRepository teamRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final ExternalIdMappingService externalIdMappingService;
    private final MatchRepository matchRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final MatchXgPersistenceService matchXgPersistenceService;
    private final FootballDataUkMatchStatisticsPersistenceService statisticsMapper;
    private final FootballDataUkOddsPersistenceService oddsMapper;

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
            String canonicalLeagueName = MmoProviderDataStandardizer.leagueName(definition.leagueName());
            List<League> leagues = leagueRepository.findAll().stream()
                    .filter(candidate -> canonicalLeagueName.equalsIgnoreCase(
                            MmoProviderDataStandardizer.leagueName(candidate.getName())))
                    .toList();
            if (leagues.size() != 1) {
                skipped++;
                log.warn("Skip Football-Data UK league mapping: name={}, matches={}",
                        definition.leagueName(), leagues.size());
                continue;
            }

            League league = leagues.get(0);
            Optional<LeagueExternalId> existing = leagueExternalIdRepository
                    .findByLeagueIdAndProvider(league.getId(), PROVIDER);

            if (existing.isPresent()) {
                LeagueExternalId mapping = existing.get();
                boolean changed = !definition.externalId().equals(mapping.getExternalId())
                        || !definition.identify().equals(mapping.getIdentify());
                if (changed) {
                    mapping.setExternalId(definition.externalId());
                    mapping.setIdentify(definition.identify());
                    leagueExternalIdRepository.save(mapping);
                    updated++;
                }
                continue;
            }

            Optional<LeagueExternalId> conflicting = leagueExternalIdRepository
                    .findByProviderAndExternalId(PROVIDER, definition.externalId());
            if (conflicting.isPresent()) {
                log.error("Cannot initialize Football-Data UK mapping: provider externalId={} already belongs to leagueId={}",
                        definition.externalId(), conflicting.get().getLeague().getId());
                skipped++;
                continue;
            }

            leagueExternalIdRepository.save(LeagueExternalId.builder()
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

        if (leagueExternalIdRepository.findAllByProvider(PROVIDER).isEmpty()) {
            throw new IllegalStateException("No Football-Data.co.uk league mappings are available after initialization");
        }

        LocalDate today = LocalDate.now(SOURCE_ZONE);
        int importedFixtures = 0;
        int failedSeasons = 0;
        int incompleteSeasons = 0;
        int unavailableSeasons = 0;

        for (LeagueExternalId mapping : leagueExternalIdRepository.findAllByProvider(PROVIDER)) {
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
                if (!current && isHistoricalSeasonImported(season, mapping)) continue;

                markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.RUNNING);
                try {
                    ImportResult result = syncSeason(season, mapping, config);
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
                        markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.COMPLETED);
                    } else {
                        incompleteSeasons++;
                        markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.PARTIAL);
                        log.warn("Football-Data.co.uk season remains partial: league={}, season={}, unmatched={}, rows={}",
                                league.getName(), season.getYear(), result.unmatchedRows(), result.csvRows());
                    }
                    log.info("Football-Data.co.uk imported league={}, season={}, current={}, rows={}, createdFixtures={}, unmatched={}",
                            league.getName(), season.getYear(), current, result.csvRows(),
                            result.createdFixtures(), result.unmatchedRows());
                } catch (FootballDataUkCsvException exception) {
                    if (exception.isNotAvailable()) {
                        unavailableSeasons++;
                        markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.NOT_AVAILABLE);
                        log.info("Football-Data.co.uk CSV not published: league={}, season={}, reason={}",
                                league.getName(), season.getYear(), exception.getMessage());
                    } else {
                        failedSeasons++;
                        markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.FAILED);
                        log.error("Football-Data.co.uk CSV failure: league={}, season={}, reason={}, retryable={}",
                                league.getName(), season.getYear(), exception.getReason(),
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

        List<LeagueExternalId> leagueMappings = leagueExternalIdRepository.findAllByProvider(PROVIDER);
        if (leagueMappings.isEmpty()) {
            log.warn("Football-Data.co.uk active-season sync skipped: no provider league mappings exist");
            return;
        }

        int importedSeasons = 0;
        int failedSeasons = 0;
        for (LeagueExternalId mapping : leagueMappings) {
            if (!StringUtils.hasText(mapping.getIdentify())) continue;
            League league = mapping.getLeague();
            List<Season> activeSeasons = seasonRepository.findByLeague_Id(league.getId()).stream()
                    .filter(Season::isActive)
                    .filter(season -> dataSourcePolicy.isAllowed(PROVIDER, Dataset.FIXTURE, season))
                    .toList();

            for (Season season : activeSeasons) {
                markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.RUNNING);
                try {
                    ImportResult result = syncSeason(season, mapping, config);
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
                    markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.COMPLETED);
                    importedSeasons++;
                    log.info("Football-Data.co.uk active-season Core sync: league={}, season={}, rows={}, createdFixtures={}",
                            league.getName(), season.getYear(), result.csvRows(), result.createdFixtures());
                } catch (FootballDataUkCsvException exception) {
                    if (exception.isNotAvailable()) {
                        markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.NOT_AVAILABLE);
                        log.info("Football-Data.co.uk active-season CSV not published: league={}, season={}",
                                league.getName(), season.getYear());
                    } else {
                        failedSeasons++;
                        markCoreCheckpoint(league.getId(), season.getYear(), CheckpointStatus.FAILED);
                        log.error("Football-Data.co.uk active-season CSV failure: league={}, season={}, reason={}",
                                league.getName(), season.getYear(), exception.getReason(), exception);
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
        List<LeagueExternalId> leagueMappings = leagueExternalIdRepository.findAllByProvider(PROVIDER);
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
                if (isEnrichmentCheckpointCompleted(leagueId, season.getYear())) continue;

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
                    markEnrichmentCheckpoint(leagueId, season.getYear(),
                            result.unmatchedRows() == 0
                                    ? CheckpointStatus.COMPLETED : CheckpointStatus.PARTIAL);
                } catch (FootballDataUkCsvException exception) {
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
                                exception.getReason(), exception);
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
        for (LeagueExternalId leagueMapping : leagueExternalIdRepository.findAllByProvider(PROVIDER)) {
            List<Season> activeSeasons = seasonRepository.findByLeague_Id(
                            leagueMapping.getLeague().getId()).stream()
                    .filter(Season::isActive)
                    .filter(season -> dataSourcePolicy.isPrimary(
                            PROVIDER, Dataset.MATCH_STATISTICS, season))
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
                        markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.PARTIAL);
                    } else {
                        enrichedSeasons++;
                        markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.COMPLETED);
                    }
                    log.info("Football-Data UK active-season enrichment: league={}, season={}, rows={}, stats={}, odds={}, unmatched={}",
                            leagueMapping.getLeague().getName(), season.getYear(), rows.size(),
                            result.importedTeamStats(), result.importedOdds(), result.unmatchedRows());
                } catch (FootballDataUkCsvException exception) {
                    if (exception.isNotAvailable()) {
                        markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.NOT_AVAILABLE);
                        log.info("Football-Data UK active-season enrichment CSV not published: league={}, season={}",
                                leagueMapping.getLeague().getName(), season.getYear());
                    } else {
                        failed++;
                        markEnrichmentCheckpoint(leagueId, season.getYear(), CheckpointStatus.FAILED);
                        log.error("Football-Data UK active-season enrichment CSV failure: league={}, season={}, reason={}",
                                leagueMapping.getLeague().getName(), season.getYear(), exception.getReason(), exception);
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
        return seasonRepository.findByLeague_Id(league.getId()).stream()
                .filter(candidate -> seasonStartYear(candidate.getYear()) == startYear)
                .findFirst()
                .orElse(null);
    }

    private EnrichmentResult enrichRows(
            Season season, LeagueExternalId leagueMapping, List<Map<String, String>> rows) {
        int importedStats = 0;
        int importedOdds = 0;
        int unmatched = 0;
        for (Map<String, String> row : rows) {
            try {
                String homeName = valueAny(row, "HOMETEAM", "HOME TEAM");
                String awayName = valueAny(row, "AWAYTEAM", "AWAY TEAM");
                LocalDate date = parseDate(value(row, "DATE"));
                if (!StringUtils.hasText(homeName) || !StringUtils.hasText(awayName) || date == null) {
                    unmatched++;
                    continue;
                }

                Team home = findExistingTeam(leagueMapping.getIdentify(), homeName);
                Team away = findExistingTeam(leagueMapping.getIdentify(), awayName);
                if (home == null || away == null || home.getId().equals(away.getId())) {
                    unmatched++;
                    continue;
                }

                LocalTime time = parseTime(value(row, "TIME"));
                Instant kickoff = date.atTime(time == null ? LocalTime.NOON : time)
                        .atZone(SOURCE_ZONE).toInstant();
                String externalId = fixtureExternalId(leagueMapping.getIdentify(), season, home, away);
                Match match = matchExternalIdRepository.findByProviderAndExternalId(MATCH_PROVIDER, externalId)
                        .map(MatchExternalId::getMatch)
                        .orElseGet(() -> matchExternalIdRepository.findByProviderAndExternalId(
                                        MATCH_PROVIDER, legacyFixtureExternalId(
                                                leagueMapping.getIdentify(), season, homeName, awayName))
                                .map(MatchExternalId::getMatch)
                                .orElseGet(() -> findFixtureCandidate(season, home, away, kickoff)));

                if (match == null) {
                    unmatched++;
                    continue;
                }

                BigDecimal homeXg = decimalMetric(value(row, "HXG"));
                BigDecimal awayXg = decimalMetric(value(row, "AXG"));
                matchXgPersistenceService.upsert(PROVIDER, match, homeXg, awayXg);
                if (dataSourcePolicy.isPrimary(PROVIDER, Dataset.MATCH_STATISTICS, season)) {
                    importedStats += statisticsMapper.persist(match, row);
                }
                if (dataSourcePolicy.isPrimary(PROVIDER, Dataset.HISTORICAL_ODDS, season)) {
                    importedOdds += oddsMapper.persist(match, row);
                }
            } catch (RuntimeException exception) {
                unmatched++;
                log.debug("Skip unmatched Football-Data UK enrichment row: season={}, reason={}",
                        season.getYear(), exception.getMessage());
            }
        }
        return new EnrichmentResult(rows.size(), importedStats, importedOdds, unmatched);
    }

    private Team findExistingTeam(String leagueCode, String providerName) {
        String legacyExternalId = normalize(leagueCode) + ":" + normalize(providerName);
        Optional<TeamExternalId> legacy =
                teamExternalIdRepository.findByProviderAndExternalId(PROVIDER, legacyExternalId);
        if (legacy.isPresent()) return legacy.get().getTeam();

        String canonicalName = MmoProviderDataStandardizer.standardTeamName(providerName);
        if (!StringUtils.hasText(canonicalName)) return null;
        Optional<TeamExternalId> canonical = teamExternalIdRepository.findByProviderAndExternalId(
                PROVIDER, "TEAM:" + normalize(canonicalName));
        if (canonical.isPresent()) return canonical.get().getTeam();

        List<Team> exact = teamRepository.findAllByNameIgnoreCase(canonicalName);
        return exact.size() == 1 ? exact.get(0) : null;
    }

    private Match findFixtureCandidate(Season season, Team home, Team away, Instant kickoff) {
        Instant start = season.getStartDate() == null
                ? kickoff.minusSeconds(18 * 3600L)
                : season.getStartDate().atStartOfDay(SOURCE_ZONE).toInstant();
        Instant end = season.getEndDate() == null
                ? kickoff.plusSeconds(18 * 3600L)
                : season.getEndDate().plusDays(1).atStartOfDay(SOURCE_ZONE).toInstant();
        List<Match> candidates = matchRepository.findFixtureCandidates(
                season.getId(), home.getId(), away.getId(), start, end);
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static String legacyFixtureExternalId(
            String leagueCode, Season season, String home, String away) {
        return normalize(leagueCode) + ":" + seasonCode(season.getYear()) + ":"
                + normalize(home) + ":" + normalize(away);
    }

    private record EnrichmentResult(
            int csvRows, int importedTeamStats, int importedOdds, int unmatchedRows) {
    }

    public ImportResult syncSeason(Season season, LeagueExternalId leagueMapping,
                                   AppProperties.ProviderConfig config) {
        List<Map<String, String>> rows = matchDataProvider.fetchSeason(
                config.getUri(), season.getYear(), leagueMapping.getIdentify());
        int createdFixtures = 0;
        int importedStats = 0;
        int importedOdds = 0;
        int unmatched = 0;

        for (Map<String, String> row : rows) {
            try {
                String homeName = valueAny(row, "HOMETEAM", "HOME TEAM");
                String awayName = valueAny(row, "AWAYTEAM", "AWAY TEAM");
                LocalDate date = parseDate(value(row, "DATE"));
                if (!StringUtils.hasText(homeName) || !StringUtils.hasText(awayName) || date == null) {
                    unmatched++;
                    continue;
                }

                Team home = resolveTeam(leagueMapping.getIdentify(), homeName, season);
                Team away = resolveTeam(leagueMapping.getIdentify(), awayName, season);
                if (home.getId().equals(away.getId())) {
                    unmatched++;
                    continue;
                }

                LocalTime time = parseTime(value(row, "TIME"));
                Instant kickoff = date.atTime(time == null ? LocalTime.NOON : time)
                        .atZone(SOURCE_ZONE).toInstant();
                String fixtureExternalId = fixtureExternalId(leagueMapping.getIdentify(), season, home, away);

                Optional<MatchExternalId> existingExternalId = matchExternalIdRepository
                        .findByProviderAndExternalId(MATCH_PROVIDER, fixtureExternalId);
                Match match = existingExternalId.map(MatchExternalId::getMatch)
                        .orElseGet(() -> resolveOrCreateFixture(season, home, away, kickoff));
                boolean footballDataOwnsCore = match.getId() != null
                        && matchExternalIdRepository.findByMatchIdAndProvider(
                                match.getId(), MatchProvider.fromName(Provider.FOOTBALL_DATA.name())).isPresent();
                boolean ukOwnsFixtureCore = dataSourcePolicy.isPrimary(
                        PROVIDER, Dataset.FIXTURE, season);
                boolean mayUseUkFallback = dataSourcePolicy.mayFallbackForCore(
                        PROVIDER, Dataset.FIXTURE, season, footballDataOwnsCore);
                boolean preservePrimaryCore = !ukOwnsFixtureCore && !mayUseUkFallback;
                if (!ukOwnsFixtureCore && !mayUseUkFallback && !footballDataOwnsCore) {
                    unmatched++;
                    continue;
                }
                if (!preservePrimaryCore || match.getMatchTime() == null) {
                    match.setMatchTime(kickoff);
                }
                // Season-aware ownership: UK is primary through 2022; from 2023 it may
                // only fill a missing Football-Data fixture, never replace mapped FD core fields.
                updateFixtureResult(match, row, preservePrimaryCore);
                Optional<MatchExternalId> mapping = match.getId() == null ? Optional.empty()
                        : matchExternalIdRepository.findByMatchIdAndProvider(match.getId(), MatchProvider.fromName(PROVIDER.name()));
                if (mapping.isPresent()) {
                    MatchExternalId externalId = mapping.get();
                    if (!fixtureExternalId.equals(externalId.getExternalId())) {
                        externalId.setExternalId(fixtureExternalId);
                        matchExternalIdRepository.save(externalId);
                    }
                } else if (match.getId() != null) {
                    externalIdMappingService.registerMatch(match, PROVIDER, fixtureExternalId);
                }
                if (match.getId() == null) {
                    match = matchRepository.save(match);
                    externalIdMappingService.registerMatch(match, PROVIDER, fixtureExternalId);
                    createdFixtures++;
                } else {
                    matchRepository.save(match);
                }
                // Core import owns fixture identity, kickoff, status and result only.
                // Statistics and odds are imported by the independent Match Enrichment workflow.
            } catch (RuntimeException ex) {
                unmatched++;
                log.warn("Skip invalid Football-Data.co.uk row: season={}, home={}, away={}, reason={}",
                        season.getYear(), value(row, "HOMETEAM"), value(row, "AWAYTEAM"), ex.getMessage());
            }
        }
        return new ImportResult(rows.size(), createdFixtures, 0, 0, unmatched);
    }

    private Season getOrCreateSeason(League league, int startYear, LocalDate today) {
        String year = startYear + "/" + (startYear + 1);
        LocalDate start = LocalDate.of(startYear, 7, 1);
        LocalDate end = LocalDate.of(startYear + 1, 6, 30);
        boolean current = !today.isBefore(start) && !today.isAfter(end);

        Optional<Season> existing = seasonRepository.findByLeague_Id(league.getId()).stream()
                .filter(candidate -> seasonStartYear(candidate.getYear()) == startYear)
                .findFirst();
        Season season;
        if (existing.isPresent()) {
            season = existing.get();
            boolean footballDataOwnsSeason = seasonExternalIdRepository
                    .findBySeasonIdAndProvider(season.getId(), Provider.FOOTBALL_DATA).isPresent();
            boolean mayUpdate = dataSourcePolicy.isPrimary(PROVIDER, Dataset.SEASON, season)
                    || dataSourcePolicy.mayFallbackForCore(
                            PROVIDER, Dataset.SEASON, season, footballDataOwnsSeason);
            if (!mayUpdate) return season;
        } else {
            // UK may create a season only when it owns the season dataset or is the
            // explicitly permitted fallback because the primary provider has no identity.
            Season candidate = Season.builder()
                    .league(league).year(year).startDate(start).endDate(end).build();
            boolean mayCreate = dataSourcePolicy.isPrimary(PROVIDER, Dataset.SEASON, candidate)
                    || dataSourcePolicy.mayFallbackForCore(
                            PROVIDER, Dataset.SEASON, candidate, false);
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
        return seasonRepository.save(season);
    }

    private void saveSeasonMapping(Season season, LeagueExternalId leagueMapping) {
        // SeasonExternalId stores provider identity only. Import completion belongs in
        // fb_provider_sync_checkpoints, never in a synthetic ":CORE2" suffix.
        String externalId = leagueMapping.getIdentify().trim().toUpperCase(Locale.ROOT)
                + ":" + seasonCode(season.getYear());
        SeasonExternalId mapping = seasonExternalIdRepository
                .findBySeasonIdAndProvider(season.getId(), PROVIDER)
                .orElseGet(() -> SeasonExternalId.builder()
                        .season(season).provider(PROVIDER).build());
        mapping.setExternalId(externalId);
        seasonExternalIdRepository.save(mapping);
    }

    private boolean isHistoricalSeasonImported(Season season, LeagueExternalId leagueMapping) {
        Long leagueId = leagueMapping.getLeague().getId();
        String seasonYear = season.getYear();
        boolean teamCompleted = checkpointStore.isCompleted(PROVIDER, Dataset.TEAM, leagueId, seasonYear);
        boolean seasonCompleted = checkpointStore.isCompleted(PROVIDER, Dataset.SEASON, leagueId, seasonYear);
        boolean fixtureCompleted = checkpointStore.isCompleted(PROVIDER, Dataset.FIXTURE, leagueId, seasonYear);
        if (teamCompleted && seasonCompleted && fixtureCompleted) return true;

        // Migrate the previous season-scoped key and legacy :CORE2 marker into the
        // structured dataset/league/season checkpoint namespace.
        String legacyKey = seasonCheckpointKey(leagueMapping.getLeague(), seasonStartYear(seasonYear));
        boolean legacyCheckpoint = checkpointStore.isCompleted(PROVIDER, legacyKey);
        Optional<SeasonExternalId> legacyMapping = seasonExternalIdRepository
                .findBySeasonIdAndProvider(season.getId(), PROVIDER);
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
        checkpointStore.markStatus(PROVIDER, Dataset.SEASON, leagueId, season, status);
        checkpointStore.markStatus(PROVIDER, Dataset.TEAM, leagueId, season, status);
        checkpointStore.markStatus(PROVIDER, Dataset.FIXTURE, leagueId, season, status);
    }

    private boolean isEnrichmentCheckpointCompleted(Long leagueId, String season) {
        return checkpointStore.isCompleted(PROVIDER, Dataset.MATCH_STATISTICS, leagueId, season)
                && checkpointStore.isCompleted(PROVIDER, Dataset.MATCH_XG, leagueId, season)
                && checkpointStore.isCompleted(PROVIDER, Dataset.HISTORICAL_ODDS, leagueId, season);
    }

    private void markEnrichmentCheckpoint(Long leagueId, String season, CheckpointStatus status) {
        checkpointStore.markStatus(PROVIDER, Dataset.MATCH_STATISTICS, leagueId, season, status);
        checkpointStore.markStatus(PROVIDER, Dataset.MATCH_XG, leagueId, season, status);
        checkpointStore.markStatus(PROVIDER, Dataset.HISTORICAL_ODDS, leagueId, season, status);
    }

    private static String seasonCheckpointKey(League league, int startYear) {
        return "UK_CORE_" + league.getId() + "_" + startYear;
    }

    private Team resolveTeam(String leagueCode, String providerName, Season season) {
        String legacyExternalId = normalize(leagueCode) + ":" + normalize(providerName);
        Optional<TeamExternalId> legacyMapping =
                teamExternalIdRepository.findByProviderAndExternalId(PROVIDER, legacyExternalId);
        if (legacyMapping.isPresent()) return legacyMapping.get().getTeam();

        String canonicalName = MmoProviderDataStandardizer.standardTeamName(providerName);
        if (!StringUtils.hasText(canonicalName)) {
            throw new IllegalArgumentException("Football-Data UK team name is blank");
        }

        String canonicalExternalId = "TEAM:" + normalize(canonicalName);
        Optional<TeamExternalId> canonicalMapping =
                teamExternalIdRepository.findByProviderAndExternalId(PROVIDER, canonicalExternalId);
        if (canonicalMapping.isPresent()) return canonicalMapping.get().getTeam();

        List<Team> exact = teamRepository.findAllByNameIgnoreCase(canonicalName);
        if (exact.size() > 1) {
            throw new IllegalStateException("Ambiguous canonical team name from Football-Data UK: "
                    + canonicalName + " (matches=" + exact.size() + ")");
        }

        Team team = exact.isEmpty() ? null : exact.get(0);
        boolean footballDataOwnsTeam = team != null
                && teamExternalIdRepository.findByTeamIdAndProvider(
                        team.getId(), Provider.FOOTBALL_DATA).isPresent();
        boolean ukOwnsTeam = dataSourcePolicy.isPrimary(PROVIDER, Dataset.TEAM, season);
        boolean mayUseUkFallback = dataSourcePolicy.mayFallbackForCore(
                PROVIDER, Dataset.TEAM, season, footballDataOwnsTeam);
        if (team == null) {
            if (!ukOwnsTeam && !mayUseUkFallback) {
                throw new IllegalStateException("Football-Data UK is not allowed to create team "
                        + canonicalName + " for season " + season.getYear());
            }
            team = teamRepository.save(Team.builder()
                    .name(canonicalName)
                    .shortName(canonicalName.length() > 80 ? canonicalName.substring(0, 80) : canonicalName)
                    .slug(slug(canonicalName))
                    .isActive(true)
                    .build());
        }

        Optional<TeamExternalId> existingForTeam =
                teamExternalIdRepository.findByTeamIdAndProvider(team.getId(), PROVIDER);
        if (existingForTeam.isEmpty()) {
            externalIdMappingService.registerTeam(team, PROVIDER, canonicalExternalId);
        } else {
            // Existing legacy mappings remain valid; do not reassign another provider ID.
            log.debug("Team already has a Football-Data UK mapping: teamId={}, externalId={}",
                    team.getId(), existingForTeam.get().getExternalId());
        }
        return team;
    }

    private Match resolveOrCreateFixture(Season season, Team home, Team away, Instant kickoff) {
        Instant seasonStart = season.getStartDate() == null
                ? kickoff.minusSeconds(18 * 3600L)
                : season.getStartDate().atStartOfDay(SOURCE_ZONE).toInstant();
        Instant seasonEnd = season.getEndDate() == null
                ? kickoff.plusSeconds(18 * 3600L)
                : season.getEndDate().plusDays(1).atStartOfDay(SOURCE_ZONE).toInstant();
        List<Match> candidates = matchRepository.findFixtureCandidates(
                season.getId(), home.getId(), away.getId(), seasonStart, seasonEnd);
        if (candidates.size() == 1) return candidates.get(0);
        if (candidates.size() > 1) {
            throw new IllegalStateException("Ambiguous core fixture candidates");
        }
        return Match.builder().season(season).homeTeam(home).awayTeam(away)
                .matchTime(kickoff).status(MatchStatus.SCHEDULED).build();
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

    private static String fixtureExternalId(String leagueCode, Season season, Team home, Team away) {
        // Canonical team IDs make the provider identity stable across historical name aliases.
        return normalize(leagueCode) + ":" + seasonCode(season.getYear()) + ":"
                + home.getId() + ":" + away.getId();
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

    public record ImportResult(int csvRows, int createdFixtures, int importedTeamStats, int importedOdds, int unmatchedRows) {}
}
