package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkIngestionPort;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.footballdatauk.FootballDataUkMatchDataProvider;
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
    private static final ZoneId SOURCE_ZONE = ZoneId.of("Europe/London");
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("dd/MM/yy");

    private final AppProperties properties;
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
                    .findByProviderAndExternalId(MatchProvider.fromName(PROVIDER.name()), definition.externalId());
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

        for (LeagueExternalId mapping : leagueExternalIdRepository.findAllByProvider(PROVIDER)) {
            League league = mapping.getLeague();
            if (!StringUtils.hasText(mapping.getIdentify())) {
                log.warn("Skip Football-Data.co.uk league without identify mapping: leagueId={}, name={}",
                        league.getId(), league.getName());
                continue;
            }

            for (int startYear = 2014; startYear <= today.getYear(); startYear++) {
                Season season = getOrCreateSeason(league, startYear, today);
                boolean current = season.isCurrent();
                Optional<SeasonExternalId> seasonMapping = seasonExternalIdRepository
                        .findBySeasonIdAndProvider(season.getId(), PROVIDER);
                boolean alreadyImported = seasonMapping
                        .map(mappingRow -> mappingRow.getExternalId().endsWith(":CORE2"))
                        .orElse(false);
                if (!current && alreadyImported) {
                    continue;
                }
                try {
                    ImportResult result = syncSeason(season, mapping, config);
                    importedFixtures += result.createdFixtures();
                    if (result.unmatchedRows() == 0 && result.csvRows() > 0) {
                        saveSeasonMapping(season, mapping);
                    } else {
                        incompleteSeasons++;
                        log.warn("Football-Data.co.uk season remains pending because rows were unmatched or CSV was empty: league={}, season={}, unmatched={}, rows={}",
                                league.getName(), season.getYear(), result.unmatchedRows(), result.csvRows());
                    }
                    log.info("Football-Data.co.uk imported league={}, season={}, current={}, rows={}, createdFixtures={}, stats={}, odds={}, unmatched={}",
                            league.getName(), season.getYear(), current, result.csvRows(),
                            result.createdFixtures(), result.importedTeamStats(), result.importedOdds(), result.unmatchedRows());
                } catch (RuntimeException ex) {
                    failedSeasons++;
                    log.error("Football-Data.co.uk import failed: league={}, season={}, reason={}",
                            league.getName(), season.getYear(), ex.getMessage(), ex);
                }
            }
        }

        log.info("Football-Data.co.uk Core import completed: createdFixtures={}, failedSeasons={}",
                importedFixtures, failedSeasons);
        if (failedSeasons > 0 || incompleteSeasons > 0) {
            throw new IllegalStateException("Football-Data.co.uk initial import incomplete: failedSeasons="
                    + failedSeasons + ", incompleteSeasons=" + incompleteSeasons);
        }
        return true;
    }

    /** Compatibility entry point. New callers should choose initial or current-season scope explicitly. */
    public void syncAllData() {
        syncInitialData();
    }

    /** Incremental CSV sync: fetches only the current season for every mapped league. */
    public void syncCurrentSeasonData() {
        initialLeagueMappings();
        AppProperties.ProviderConfig config = properties.getIngestion().getProviderConfig().get(PROVIDER);
        if (config == null || !config.isEnabled()) {
            log.info("Football-Data.co.uk incremental import disabled");
            return;
        }

        if (leagueExternalIdRepository.findAllByProvider(PROVIDER).isEmpty()) {
            log.warn("Football-Data.co.uk current-season sync skipped: no provider league mappings exist");
            return;
        }

        LocalDate today = LocalDate.now(SOURCE_ZONE);
        int startYear = currentSeasonStartYear(today);
        int failedSeasons = 0;
        for (LeagueExternalId mapping : leagueExternalIdRepository.findAllByProvider(PROVIDER)) {
            if (!StringUtils.hasText(mapping.getIdentify())) continue;
            League league = mapping.getLeague();
            Season season = getOrCreateSeason(league, startYear, today);
            try {
                ImportResult result = syncSeason(season, mapping, config);
                if (result.csvRows() == 0 || result.unmatchedRows() > 0) {
                    failedSeasons++;
                    log.warn("Football-Data.co.uk current season remains incomplete: league={}, season={}, rows={}, unmatched={}",
                            league.getName(), season.getYear(), result.csvRows(), result.unmatchedRows());
                    continue;
                }
                saveSeasonMapping(season, mapping);
                log.info("Football-Data.co.uk current-season Core sync: league={}, season={}, rows={}, createdFixtures={}",
                        league.getName(), season.getYear(), result.csvRows(), result.createdFixtures());
            } catch (RuntimeException exception) {
                failedSeasons++;
                log.error("Football-Data.co.uk current-season sync failed: league={}, season={}, reason={}",
                        league.getName(), season.getYear(), exception.getMessage(), exception);
            }
        }
        if (failedSeasons > 0) {
            throw new IllegalStateException("Football-Data.co.uk current-season sync failed for "
                    + failedSeasons + " league(s)");
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
        int importedStats = 0;
        int importedOdds = 0;
        List<LeagueExternalId> leagueMappings = leagueExternalIdRepository.findAllByProvider(PROVIDER);
        if (leagueMappings.isEmpty()) {
            throw new IllegalStateException("No Football-Data.co.uk league mappings are available for enrichment");
        }
        for (LeagueExternalId leagueMapping : leagueMappings) {
            for (int startYear = 2014; startYear <= today.getYear(); startYear++) {
                Season season = findSeason(leagueMapping.getLeague(), startYear);
                if (season == null) {
                    failedSeasons++;
                    log.warn("Cannot enrich Football-Data UK season because canonical Core season is missing: league={}, year={}",
                            leagueMapping.getLeague().getName(), startYear);
                    continue;
                }
                try {
                    List<Map<String, String>> rows = matchDataProvider.fetchSeason(
                            config.getUri(), season.getYear(), leagueMapping.getIdentify());
                    if (rows.isEmpty()) {
                        failedSeasons++;
                        continue;
                    }
                    EnrichmentResult result = enrichRows(season, leagueMapping, rows);
                    importedStats += result.importedTeamStats();
                    importedOdds += result.importedOdds();
                    unmatchedRows += result.unmatchedRows();
                } catch (RuntimeException exception) {
                    failedSeasons++;
                    log.error("Football-Data UK historical enrichment failed: league={}, season={}",
                            leagueMapping.getLeague().getName(), season.getYear(), exception);
                }
            }
        }
        log.info("Football-Data UK initial enrichment completed: stats={}, odds={}, unmatched={}, failedSeasons={}",
                importedStats, importedOdds, unmatchedRows, failedSeasons);
        if (failedSeasons > 0 || unmatchedRows > 0) {
            throw new IllegalStateException("Football-Data UK initial enrichment incomplete: failedSeasons="
                    + failedSeasons + ", unmatchedRows=" + unmatchedRows);
        }
        return true;
    }

    /** Incremental match enrichment is restricted to the current CSV season. */
    public void syncCurrentSeasonEnrichment() {
        initialLeagueMappings();
        AppProperties.ProviderConfig config = properties.getIngestion().getProviderConfig().get(PROVIDER);
        if (config == null || !config.isEnabled()) return;

        LocalDate today = LocalDate.now(SOURCE_ZONE);
        int startYear = currentSeasonStartYear(today);
        int failed = 0;
        for (LeagueExternalId leagueMapping : leagueExternalIdRepository.findAllByProvider(PROVIDER)) {
            Season season = findSeason(leagueMapping.getLeague(), startYear);
            if (season == null) {
                failed++;
                continue;
            }
            try {
                List<Map<String, String>> rows = matchDataProvider.fetchSeason(
                        config.getUri(), season.getYear(), leagueMapping.getIdentify());
                EnrichmentResult result = enrichRows(season, leagueMapping, rows);
                if (rows.isEmpty() || result.unmatchedRows() > 0) failed++;
                log.info("Football-Data UK current-season enrichment: league={}, season={}, rows={}, stats={}, odds={}, unmatched={}",
                        leagueMapping.getLeague().getName(), season.getYear(), rows.size(),
                        result.importedTeamStats(), result.importedOdds(), result.unmatchedRows());
            } catch (RuntimeException exception) {
                failed++;
                log.error("Football-Data UK current-season enrichment failed: league={}, season={}",
                        leagueMapping.getLeague().getName(), season.getYear(), exception);
            }
        }
        if (failed > 0) {
            throw new IllegalStateException("Football-Data UK current-season enrichment failed for "
                    + failed + " league(s)");
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
                Match match = matchExternalIdRepository.findByProviderAndExternalId(PROVIDER, externalId)
                        .map(MatchExternalId::getMatch)
                        .orElseGet(() -> matchExternalIdRepository.findByProviderAndExternalId(
                                        PROVIDER, legacyFixtureExternalId(
                                                leagueMapping.getIdentify(), season, homeName, awayName))
                                .map(MatchExternalId::getMatch)
                                .orElseGet(() -> findFixtureCandidate(season, home, away, kickoff)));

                if (match == null) {
                    unmatched++;
                    continue;
                }

                boolean footballDataOwnsCore = match.getId() != null
                        && matchExternalIdRepository.findByMatchIdAndProvider(
                                match.getId(), MatchProvider.fromName(Provider.FOOTBALL_DATA.name())).isPresent();
                BigDecimal homeXg = decimalMetric(value(row, "HXG"));
                BigDecimal awayXg = decimalMetric(value(row, "AXG"));
                if ((!footballDataOwnsCore || match.getHomeXG() == null) && homeXg != null) {
                    match.setHomeXG(homeXg.doubleValue());
                }
                if ((!footballDataOwnsCore || match.getAwayXG() == null) && awayXg != null) {
                    match.setAwayXG(awayXg.doubleValue());
                }
                matchRepository.save(match);
                importedStats += statisticsMapper.persist(match, row);
                importedOdds += oddsMapper.persist(match, row);
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

                Team home = resolveTeam(leagueMapping.getIdentify(), homeName);
                Team away = resolveTeam(leagueMapping.getIdentify(), awayName);
                if (home.getId().equals(away.getId())) {
                    unmatched++;
                    continue;
                }

                LocalTime time = parseTime(value(row, "TIME"));
                Instant kickoff = date.atTime(time == null ? LocalTime.NOON : time)
                        .atZone(SOURCE_ZONE).toInstant();
                String fixtureExternalId = fixtureExternalId(leagueMapping.getIdentify(), season, home, away);

                Optional<MatchExternalId> existingExternalId = matchExternalIdRepository
                        .findByProviderAndExternalId(PROVIDER, fixtureExternalId);
                Match match = existingExternalId.map(MatchExternalId::getMatch)
                        .orElseGet(() -> resolveOrCreateFixture(season, home, away, kickoff));
                boolean footballDataOwnsCore = match.getId() != null
                        && matchExternalIdRepository.findByMatchIdAndProvider(
                                match.getId(), Provider.FOOTBALL_DATA).isPresent();
                if (!footballDataOwnsCore || match.getMatchTime() == null) {
                    match.setMatchTime(kickoff);
                }
                // Football-Data is the primary Core source for overlapping seasons.
                // UK refreshes its own matches and fills gaps on Football-Data-owned matches.
                updateFixtureResult(match, row, footballDataOwnsCore);
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

        Season season = seasonRepository.findByLeague_Id(league.getId()).stream()
                .filter(candidate -> seasonStartYear(candidate.getYear()) == startYear)
                .findFirst()
                .orElseGet(() -> Season.builder()
                        .league(league).year(year).startDate(start).endDate(end).build());

        // Historical seasons are backfill-only; scheduled jobs must see only the current season.
        season.setCurrent(current);
        season.setActive(current);
        if (season.getStartDate() == null) season.setStartDate(start);
        if (season.getEndDate() == null) season.setEndDate(end);
        return seasonRepository.save(season);
    }

    private void saveSeasonMapping(Season season, LeagueExternalId leagueMapping) {
        String externalId = leagueMapping.getIdentify().trim().toUpperCase(Locale.ROOT)
                + ":" + seasonCode(season.getYear()) + ":CORE2";
        SeasonExternalId mapping = seasonExternalIdRepository
                .findBySeasonIdAndProvider(season.getId(), PROVIDER)
                .orElseGet(() -> SeasonExternalId.builder()
                        .season(season).provider(PROVIDER).build());
        mapping.setExternalId(externalId);
        seasonExternalIdRepository.save(mapping);
    }

    private Team resolveTeam(String leagueCode, String providerName) {
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

        Team team = exact.isEmpty()
                ? teamRepository.save(Team.builder()
                        .name(canonicalName)
                        .shortName(canonicalName.length() > 80 ? canonicalName.substring(0, 80) : canonicalName)
                        .slug(slug(canonicalName))
                        .isActive(true)
                        .build())
                : exact.get(0);

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
