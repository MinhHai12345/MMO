package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.footballdatauk.FootballDataUkCsvClient;
import com.mmo.module.fb.ingest.provider.footballdatauk.FootballDataUkCsvParser;
import com.mmo.module.fb.ingest.provider.model.MmoProviderDataStandardizer;
import com.mmo.module.fb.league.entity.League;
import com.mmo.module.fb.league.entity.LeagueExternalId;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.league.entity.SeasonExternalId;
import com.mmo.module.fb.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.league.repository.LeagueRepository;
import com.mmo.module.fb.league.repository.SeasonRepository;
import com.mmo.module.fb.league.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchAdvancedStats;
import com.mmo.module.fb.match.entity.MatchExternalId;
import com.mmo.module.fb.match.entity.Bookmaker;
import com.mmo.module.fb.match.entity.MatchOdds;
import com.mmo.module.fb.match.enums.MatchStatus;
import com.mmo.module.fb.match.repository.MatchAdvancedStatsRepository;
import com.mmo.module.fb.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.match.repository.MatchRepository;
import com.mmo.module.fb.match.repository.BookmakerRepository;
import com.mmo.module.fb.match.repository.MatchOddsRepository;
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
public class FootballDataUkSyncService {
    private static final Provider PROVIDER = Provider.FOOTBALL_DATA_UK;
    private static final ZoneId SOURCE_ZONE = ZoneId.of("Europe/London");
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("dd/MM/yy");

    private final AppProperties properties;
    private final FootballDataUkCsvClient csvClient;
    private final FootballDataUkCsvParser csvParser;
    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final SeasonExternalIdRepository seasonExternalIdRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamRepository teamRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final MatchRepository matchRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final MatchAdvancedStatsRepository statsRepository;
    private final BookmakerRepository bookmakerRepository;
    private final MatchOddsRepository matchOddsRepository;

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
                new LeagueMappingDefinition("Bundesliga", "D1"),
                new LeagueMappingDefinition("Serie A", "I1"),
                new LeagueMappingDefinition("LaLiga", "SP1"),
                new LeagueMappingDefinition("Ligue 1", "F1")
        );

        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (LeagueMappingDefinition definition : definitions) {
            List<League> leagues = leagueRepository.findAllByNameIgnoreCase(definition.leagueName());
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
    public void syncAllData() {
        AppProperties.ProviderConfig config = properties.getIngestion().getProviderConfig().get(PROVIDER);
        if (config == null || !config.isEnabled()) {
            log.info("Football-Data.co.uk import disabled");
            return;
        }

        LocalDate today = LocalDate.now(SOURCE_ZONE);
        int importedFixtures = 0;
        int importedStats = 0;
        int importedOdds = 0;
        int failedSeasons = 0;

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
                        .map(mappingRow -> mappingRow.getExternalId().endsWith(":CORE1"))
                        .orElse(false);
                if (!current && alreadyImported) {
                    continue;
                }
                try {
                    ImportResult result = syncSeason(season, mapping, config);
                    importedFixtures += result.createdFixtures();
                    importedStats += result.importedTeamStats();
                    importedOdds += result.importedOdds();
                    if (result.unmatchedRows() == 0 && result.csvRows() > 0) {
                        saveSeasonMapping(season, mapping);
                    } else {
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

        log.info("Football-Data.co.uk sync completed: createdFixtures={}, importedTeamStats={}, importedOdds={}, failedSeasons={}",
                importedFixtures, importedStats, importedOdds, failedSeasons);
        if (failedSeasons > 0) {
            throw new IllegalStateException("Football-Data.co.uk import failed for " + failedSeasons + " season(s)");
        }
    }

    public ImportResult syncSeason(Season season, LeagueExternalId leagueMapping,
                                   AppProperties.ProviderConfig config) {
        String url = buildCsvUrl(config, season.getYear(), leagueMapping.getIdentify());
        List<Map<String, String>> rows = csvParser.parse(csvClient.getCsv(url));
        int createdFixtures = 0;
        int importedStats = 0;
        int importedOdds = 0;
        int unmatched = 0;

        for (Map<String, String> row : rows) {
            try {
                String homeName = value(row, "HOMETEAM");
                String awayName = value(row, "AWAYTEAM");
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
                String fixtureExternalId = fixtureExternalId(leagueMapping.getIdentify(), season,
                        homeName, awayName);

                Optional<MatchExternalId> existingExternalId = matchExternalIdRepository
                        .findByProviderAndExternalId(PROVIDER, fixtureExternalId);
                Match match = existingExternalId.map(MatchExternalId::getMatch)
                        .orElseGet(() -> resolveOrCreateFixture(season, home, away, kickoff, row));
                // Keep existing match data intact; enrich only fields that are still missing.
                updateFixtureResult(match, row);
                Optional<MatchExternalId> mapping = match.getId() == null ? Optional.empty()
                        : matchExternalIdRepository.findByMatchIdAndProvider(match.getId(), PROVIDER);
                if (mapping.isPresent()) {
                    MatchExternalId externalId = mapping.get();
                    if (!fixtureExternalId.equals(externalId.getExternalId())) {
                        externalId.setExternalId(fixtureExternalId);
                        matchExternalIdRepository.save(externalId);
                    }
                } else if (match.getId() != null) {
                    matchExternalIdRepository.save(MatchExternalId.builder()
                            .match(match).provider(PROVIDER).externalId(fixtureExternalId).build());
                }
                if (match.getId() == null) {
                    match = matchRepository.save(match);
                    matchExternalIdRepository.save(MatchExternalId.builder()
                            .match(match).provider(PROVIDER).externalId(fixtureExternalId).build());
                    createdFixtures++;
                } else {
                    matchRepository.save(match);
                }
                importedStats += persistStatistics(match, row);
                importedOdds += persistOdds(match, row);
            } catch (RuntimeException ex) {
                unmatched++;
                log.warn("Skip invalid Football-Data.co.uk row: season={}, home={}, away={}, reason={}",
                        season.getYear(), value(row, "HOMETEAM"), value(row, "AWAYTEAM"), ex.getMessage());
            }
        }
        return new ImportResult(rows.size(), createdFixtures, importedStats, importedOdds, unmatched);
    }

    private Season getOrCreateSeason(League league, int startYear, LocalDate today) {
        String year = startYear + "/" + (startYear + 1);
        LocalDate start = LocalDate.of(startYear, 7, 1);
        LocalDate end = LocalDate.of(startYear + 1, 6, 30);
        boolean current = !today.isBefore(start) && !today.isAfter(end);

        Optional<Season> existing = seasonRepository.findByLeague_Id(league.getId()).stream()
                .filter(candidate -> seasonStartYear(candidate.getYear()) == startYear)
                .findFirst();
        if (existing.isEmpty()) {
            return seasonRepository.save(Season.builder()
                    .league(league).year(year).startDate(start).endDate(end)
                    .isCurrent(current).isActive(true).build());
        }

        Season season = existing.get();
        if (season.isCurrent() != current) {
            season.setCurrent(current);
            season = seasonRepository.save(season);
        }
        return season;
    }

    private void saveSeasonMapping(Season season, LeagueExternalId leagueMapping) {
        String externalId = leagueMapping.getIdentify().trim().toUpperCase(Locale.ROOT)
                + ":" + seasonCode(season.getYear()) + ":CORE1";
        SeasonExternalId mapping = seasonExternalIdRepository
                .findBySeasonIdAndProvider(season.getId(), PROVIDER)
                .orElseGet(() -> SeasonExternalId.builder()
                        .season(season).provider(PROVIDER).build());
        mapping.setExternalId(externalId);
        seasonExternalIdRepository.save(mapping);
    }

    private Team resolveTeam(String leagueCode, String providerName) {
        String externalId = normalize(leagueCode) + ":" + normalize(providerName);
        Optional<TeamExternalId> mapped = teamExternalIdRepository.findByProviderAndExternalId(PROVIDER, externalId);
        if (mapped.isPresent()) {
            return mapped.get().getTeam();
        }

        String canonicalName = MmoProviderDataStandardizer.standardTeamName(providerName);
        if (!StringUtils.hasText(canonicalName)) {
            throw new IllegalArgumentException("Football-Data UK team name is blank");
        }

        List<Team> exact = teamRepository.findAllByNameIgnoreCase(canonicalName);
        Team team;
        if (exact.size() == 1) {
            team = exact.get(0);
        } else if (exact.isEmpty()) {
            team = teamRepository.save(Team.builder()
                    .name(canonicalName)
                    .shortName(canonicalName.length() > 80
                            ? canonicalName.substring(0, 80) : canonicalName)
                    .slug(slug(canonicalName))
                    .isActive(true)
                    .build());
        } else {
            // Do not guess between duplicate canonical names; create a provider-specific team.
            team = teamRepository.save(Team.builder().name(canonicalName)
                    .shortName(canonicalName.length() > 80
                            ? canonicalName.substring(0, 80) : canonicalName)
                    .slug(slug(canonicalName) + "-" + normalize(leagueCode))
                    .isActive(true).build());
        }

        Optional<TeamExternalId> existingForTeam = teamExternalIdRepository
                .findByTeamIdAndProvider(team.getId(), PROVIDER);
        if (existingForTeam.isEmpty()) {
            teamExternalIdRepository.save(TeamExternalId.builder()
                    .team(team).provider(PROVIDER).externalId(externalId).build());
        } else if (!existingForTeam.get().getExternalId().equals(externalId)) {
            // The provider schema has one mapping per team/provider. Keep the existing
            // canonical mapping and use the resolved team without adding a conflicting row.
            log.debug("Team already has a Football-Data UK mapping: teamId={}, externalId={}",
                    team.getId(), existingForTeam.get().getExternalId());
        }
        return team;
    }

    private Match resolveOrCreateFixture(Season season, Team home, Team away, Instant kickoff,
                                         Map<String, String> row) {
        Instant seasonStart = season.getStartDate() == null
                ? kickoff.minusSeconds(18 * 3600L)
                : season.getStartDate().atStartOfDay(SOURCE_ZONE).toInstant();
        Instant seasonEnd = season.getEndDate() == null
                ? kickoff.plusSeconds(18 * 3600L)
                : season.getEndDate().plusDays(1).atStartOfDay(SOURCE_ZONE).toInstant();
        List<Match> candidates = matchRepository.findFixtureCandidates(
                season.getId(), home.getId(), away.getId(), seasonStart, seasonEnd);
        if (candidates.size() == 1) {
            Match existing = candidates.get(0);
            updateFixtureResult(existing, row);
            return existing;
        }
        if (candidates.size() > 1) {
            throw new IllegalStateException("Ambiguous core fixture candidates");
        }

        Match created = Match.builder().season(season).homeTeam(home).awayTeam(away)
                .matchTime(kickoff).status(MatchStatus.SCHEDULED).build();
        updateFixtureResult(created, row);
        return created;
    }

    private void updateFixtureResult(Match match, Map<String, String> row) {
        Integer homeScore = integer(value(row, "FTHG"));
        Integer awayScore = integer(value(row, "FTAG"));
        String fullTimeResult = value(row, "FTR").toUpperCase(Locale.ROOT);
        Integer homeHalfTimeScore = integer(value(row, "HTHG"));
        Integer awayHalfTimeScore = integer(value(row, "HTAG"));
        String halfTimeResult = value(row, "HTR").toUpperCase(Locale.ROOT);
        BigDecimal homeXg = decimalMetric(value(row, "HXG"));
        BigDecimal awayXg = decimalMetric(value(row, "AXG"));
        // This importer is an enrichment source: never replace a value already stored by
        // another provider or an earlier import.
        if (match.getHomeScore() == null && homeScore != null) match.setHomeScore(homeScore);
        if (match.getAwayScore() == null && awayScore != null) match.setAwayScore(awayScore);
        if (match.getHomeHalfTimeScore() == null && homeHalfTimeScore != null) {
            match.setHomeHalfTimeScore(homeHalfTimeScore);
        }
        if (match.getAwayHalfTimeScore() == null && awayHalfTimeScore != null) {
            match.setAwayHalfTimeScore(awayHalfTimeScore);
        }
        if (match.getHomeXG() == null && homeXg != null) match.setHomeXG(homeXg.doubleValue());
        if (match.getAwayXG() == null && awayXg != null) match.setAwayXG(awayXg.doubleValue());
        if (match.getHalfTimeResult() == null && List.of("H", "D", "A").contains(halfTimeResult)) {
            match.setHalfTimeResult(halfTimeResult);
        }
        if (match.getFullTimeResult() == null && List.of("H", "D", "A").contains(fullTimeResult)) {
            match.setFullTimeResult(fullTimeResult);
        }
        if (match.getStatus() == MatchStatus.SCHEDULED
                && match.getHomeScore() != null && match.getAwayScore() != null
                && List.of("H", "D", "A").contains(fullTimeResult)) {
            match.setStatus(MatchStatus.FINISHED);
        }
    }

    private int persistStatistics(Match match, Map<String, String> row) {
        int saved = 0;
        if (persistSide(match, match.getHomeTeam(), row, "H")) saved++;
        if (persistSide(match, match.getAwayTeam(), row, "A")) saved++;
        return saved;
    }

    private boolean persistSide(Match match, Team team, Map<String, String> row, String side) {
        Integer shots = integer(value(row, side + "S"));
        Integer onTarget = integer(value(row, side + "ST"));
        Integer corners = integer(value(row, side + "C"));
        Integer fouls = integer(value(row, side + "F"));
        Integer yellow = integer(value(row, side + "Y"));
        Integer red = integer(value(row, side + "R"));
        Integer homeGoals = integer(value(row, "FTHG"));
        Integer awayGoals = integer(value(row, "FTAG"));
        Integer scored = homeGoals == null || awayGoals == null ? null
                : (side.equals("H") ? homeGoals : awayGoals);
        Integer missed = homeGoals == null || awayGoals == null ? null
                : (side.equals("H") ? awayGoals : homeGoals);
        String outcome = homeGoals == null || awayGoals == null ? null
                : homeGoals.equals(awayGoals) ? "D"
                : (side.equals("H") == (homeGoals > awayGoals) ? "W" : "L");
        BigDecimal xg = decimalMetric(value(row, side.equals("H") ? "HXG" : "AXG"));
        BigDecimal xga = decimalMetric(value(row, side.equals("H") ? "AXG" : "HXG"));
        // This CSV schema does not provide blocked shots, offsides, PPDA, possession or passing metrics.
        if (shots == null && onTarget == null && corners == null && fouls == null
                && yellow == null && red == null && xg == null && scored == null) return false;

        MatchAdvancedStats stats = statsRepository.findByMatchIdAndTeamId(match.getId(), team.getId())
                .orElseGet(() -> MatchAdvancedStats.builder().match(match).team(team).build());
        // Fill gaps only. Existing values may have been collected from a richer provider.
        // Persist only when at least one metric (or its missing provenance) is actually added.
        boolean changed = false;
        if (stats.getTotalShots() == null && shots != null) {
            stats.setTotalShots(shots);
            changed = true;
        }
        if (stats.getShotsOnTarget() == null && onTarget != null) {
            stats.setShotsOnTarget(onTarget);
            changed = true;
        }
        if (stats.getCornerKicks() == null && corners != null) {
            stats.setCornerKicks(corners);
            changed = true;
        }
        if (stats.getFouls() == null && fouls != null) {
            stats.setFouls(fouls);
            changed = true;
        }
        if (stats.getYellowCards() == null && yellow != null) {
            stats.setYellowCards(yellow);
            changed = true;
        }
        if (stats.getRedCards() == null && red != null) {
            stats.setRedCards(red);
            changed = true;
        }
        if (stats.getXg() == null && xg != null) {
            stats.setXg(xg);
            changed = true;
        }
        if (stats.getXga() == null && xga != null) {
            stats.setXga(xga);
            changed = true;
        }
        if (stats.getScored() == null && scored != null) {
            stats.setScored(scored);
            changed = true;
        }
        if (stats.getMissed() == null && missed != null) {
            stats.setMissed(missed);
            changed = true;
        }
        if (stats.getResult() == null && outcome != null) {
            stats.setResult(outcome);
            changed = true;
        }
        if (stats.getWins() == null && outcome != null) {
            stats.setWins("W".equals(outcome) ? 1 : 0);
            changed = true;
        }
        if (stats.getDraws() == null && outcome != null) {
            stats.setDraws("D".equals(outcome) ? 1 : 0);
            changed = true;
        }
        if (stats.getLosses() == null && outcome != null) {
            stats.setLosses("L".equals(outcome) ? 1 : 0);
            changed = true;
        }
        if (stats.getPoints() == null && outcome != null) {
            stats.setPoints("W".equals(outcome) ? 3 : "D".equals(outcome) ? 1 : 0);
            changed = true;
        }
        if (!changed) {
            return false;
        }

        if (!StringUtils.hasText(stats.getStatisticsProvider())) {
            stats.setStatisticsProvider(PROVIDER.name());
        }
        stats.setStatisticsSyncedAt(Instant.now());
        statsRepository.save(stats);
        return true;
    }


    /**
     * Import opening and closing odds. Each market/period is stored separately so
     * opening prices can never overwrite closing prices (or vice versa).
     */
    private int persistOdds(Match match, Map<String, String> row) {
        int saved = 0;
        for (OddsSource source : ODDS_SOURCES) {
            saved += persistOneX12Odds(match, row, source, false);
            saved += persistOneX12Odds(match, row, source, true);
            saved += persistOneTotalsOdds(match, row, source, false);
            saved += persistOneTotalsOdds(match, row, source, true);
            saved += persistOneAsianHandicapOdds(match, row, source, false);
            saved += persistOneAsianHandicapOdds(match, row, source, true);
        }
        return saved;
    }

    private int persistOneX12Odds(Match match, Map<String, String> row,
                                  OddsSource source, boolean closing) {
        String periodPrefix = closing ? "C" : "";
        String prefix = source.prefix();
        BigDecimal home = decimalOdds(value(row, prefix + periodPrefix + "H"));
        BigDecimal draw = decimalOdds(value(row, prefix + periodPrefix + "D"));
        BigDecimal away = decimalOdds(value(row, prefix + periodPrefix + "A"));
        if (home == null && draw == null && away == null) return 0;
        return persistOddsRow(match, source, closing ? "1X2_CLOSE" : "1X2_OPEN",
                null, home, draw, away, null, null, null, null) ? 1 : 0;
    }

    private int persistOneTotalsOdds(Match match, Map<String, String> row,
                                     OddsSource source, boolean closing) {
        String prefix = source.prefix() + (closing ? "C" : "");
        BigDecimal over = decimalOdds(value(row, prefix + ">2.5"));
        BigDecimal under = decimalOdds(value(row, prefix + "<2.5"));
        if (over == null && under == null) return 0;
        return persistOddsRow(match, source, closing ? "OU25_CLOSE" : "OU25_OPEN",
                null, null, null, null, over, under, null, null) ? 1 : 0;
    }

    private int persistOneAsianHandicapOdds(Match match, Map<String, String> row,
                                            OddsSource source, boolean closing) {
        String periodPrefix = closing ? "C" : "";
        String oddsPrefix = source.prefix() + periodPrefix;
        String lineHeader = closing ? "AHCH" : "AHH";
        BigDecimal line = decimalSigned(value(row, lineHeader));
        BigDecimal home = decimalOdds(value(row, oddsPrefix + "AHH"));
        BigDecimal away = decimalOdds(value(row, oddsPrefix + "AHA"));
        if (line == null && home == null && away == null) return 0;
        if (home == null && away == null) return 0;
        return persistOddsRow(match, source, closing ? "AH_CLOSE" : "AH_OPEN",
                line, null, null, null, null, null, home, away) ? 1 : 0;
    }

    private boolean persistOddsRow(Match match, OddsSource source, String marketName,
                                   BigDecimal handicapLine,
                                   BigDecimal home, BigDecimal draw, BigDecimal away,
                                   BigDecimal over, BigDecimal under,
                                   BigDecimal asianHome, BigDecimal asianAway) {
        Bookmaker bookmaker = bookmakerRepository.findByName(source.name())
                .orElseGet(() -> bookmakerRepository.save(Bookmaker.builder()
                        .name(source.name())
                        .sourceType(source.aggregate() ? "MARKET_AGGREGATE" : "BOOKMAKER")
                        .isActive(true)
                        .build()));
        if (!StringUtils.hasText(bookmaker.getSourceType())) {
            bookmaker.setSourceType(source.aggregate() ? "MARKET_AGGREGATE" : "BOOKMAKER");
            bookmakerRepository.save(bookmaker);
        }

        MatchOdds odds = matchOddsRepository.findByMatchIdAndBookmakerIdAndMarketName(
                        match.getId(), bookmaker.getId(), marketName)
                .orElseGet(() -> MatchOdds.builder()
                        .match(match).bookmaker(bookmaker).marketName(marketName).build());

        boolean changed = false;
        if (odds.getAsianHandicapLine() == null && handicapLine != null) {
            odds.setAsianHandicapLine(handicapLine);
            changed = true;
        }
        if (odds.getHomeWinOdds() == null && home != null) {
            odds.setHomeWinOdds(home);
            changed = true;
        }
        if (odds.getDrawOdds() == null && draw != null) {
            odds.setDrawOdds(draw);
            changed = true;
        }
        if (odds.getAwayWinOdds() == null && away != null) {
            odds.setAwayWinOdds(away);
            changed = true;
        }
        if (odds.getOver25Odds() == null && over != null) {
            odds.setOver25Odds(over);
            changed = true;
        }
        if (odds.getUnder25Odds() == null && under != null) {
            odds.setUnder25Odds(under);
            changed = true;
        }
        if (odds.getAsianHandicapHome() == null && asianHome != null) {
            odds.setAsianHandicapHome(asianHome);
            changed = true;
        }
        if (odds.getAsianHandicapAway() == null && asianAway != null) {
            odds.setAsianHandicapAway(asianAway);
            changed = true;
        }
        if (changed) matchOddsRepository.save(odds);
        return changed;
    }

    /**
     * Keep the provider's published abbreviations as stable source keys. Max/Avg
     * are market aggregates, not bookmakers, and are explicitly tagged as such.
     */
    private static final List<OddsSource> ODDS_SOURCES = List.of(
            new OddsSource("B365", "B365", false),
            new OddsSource("BFD", "BFD", false),
            new OddsSource("BV", "BV", false),
            new OddsSource("BW", "BW", false),
            new OddsSource("PP", "PP", false),
            new OddsSource("SKB", "SKB", false),
            new OddsSource("BFE", "BFE", false),
            new OddsSource("MAX", "MAX", true),
            new OddsSource("AVG", "AVG", true)
    );

    private record OddsSource(String prefix, String name, boolean aggregate) {}

    private static BigDecimal decimalOdds(String raw) {
        BigDecimal value = decimalMetric(raw);
        return value != null && value.signum() > 0 ? value : null;
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

    private String buildCsvUrl(AppProperties.ProviderConfig config, String season, String leagueCode) {
        String base = StringUtils.hasText(config.getUri())
                ? config.getUri().trim() : "https://football-data.co.uk";
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if ("https://www.football-data.co.uk".equalsIgnoreCase(base)) {
            base = "https://football-data.co.uk";
        }
        return base + "/mmz4281/" + seasonCode(season) + "/"
                + leagueCode.trim().toUpperCase(Locale.ROOT) + ".csv";
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

    private static String fixtureExternalId(String leagueCode, Season season, String home, String away) {
        // Date/time are deliberately excluded: postponed fixtures retain the same identity.
        return normalize(leagueCode) + ":" + seasonCode(season.getYear()) + ":"
                + normalize(home) + ":" + normalize(away);
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
            double parsed = Double.parseDouble(raw.trim());
            if (!Double.isFinite(parsed) || parsed < 0 || parsed != Math.rint(parsed)
                    || parsed > Integer.MAX_VALUE) return null;
            return (int) parsed;
        } catch (NumberFormatException ex) { return null; }
    }

    private static String value(Map<String, String> row, String key) {
        return row.getOrDefault(key, "").trim();
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
