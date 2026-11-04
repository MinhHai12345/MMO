package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.persistence.footballdatauk.FootballDataUkMatchStatisticsPersistenceService;
import com.mmo.module.fb.ingest.persistence.footballdatauk.FootballDataUkOddsPersistenceService;
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
import com.mmo.module.fb.match.entity.MatchExternalId;
import com.mmo.module.fb.match.enums.MatchStatus;
import com.mmo.module.fb.match.repository.MatchExternalIdRepository;
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
public class FootballDataUkSyncService {
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
                        .map(mappingRow -> mappingRow.getExternalId().endsWith(":CORE2"))
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
                importedStats += statisticsMapper.persist(match, row);
                importedOdds += oddsMapper.persist(match, row);
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
//        if (season.isCurrent() != current) {
//            season.setCurrent(current);
//            season = seasonRepository.save(season);
//        }
        return season;
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
        Integer homeScore = integer(valueAny(row, "FTHG", "HG"));
        Integer awayScore = integer(valueAny(row, "FTAG", "AG"));
        String fullTimeResult = valueAny(row, "FTR", "RES").toUpperCase(Locale.ROOT);
        Integer homeHalfTimeScore = integer(value(row, "HTHG"));
        Integer awayHalfTimeScore = integer(value(row, "HTAG"));
        String halfTimeResult = value(row, "HTR").toUpperCase(Locale.ROOT);
        BigDecimal homeXg = decimalMetric(value(row, "HXG"));
        BigDecimal awayXg = decimalMetric(value(row, "AXG"));
        Integer attendance = integer(value(row, "ATTENDANCE"));
        String referee = value(row, "REFEREE");
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
        if (match.getAttendance() == null && attendance != null) match.setAttendance(attendance);
        if (match.getReferee() == null && StringUtils.hasText(referee)) {
            match.setReferee(referee.trim());
        }
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
