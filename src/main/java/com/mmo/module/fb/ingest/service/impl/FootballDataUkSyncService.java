package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.footballdatauk.FootballDataUkCsvClient;
import com.mmo.module.fb.ingest.provider.footballdatauk.FootballDataUkCsvParser;
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
import com.mmo.module.fb.match.enums.MatchStatus;
import com.mmo.module.fb.match.repository.MatchAdvancedStatsRepository;
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
    private static final LocalDate FIRST_SEASON_START = LocalDate.of(2014, 7, 1);

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

    /**
     * Bootstrap seasons from 2014 for every active league with a Football-Data.co.uk
     * identify code. Historical seasons are processed once; current season is refreshed.
     */
    public void syncActiveSeasons() {
        AppProperties.ProviderConfig config = properties.getIngestion().getProviderConfig().get(PROVIDER);
        if (config == null || !config.isEnabled()) {
            log.info("Football-Data.co.uk import disabled");
            return;
        }

        LocalDate today = LocalDate.now(SOURCE_ZONE);
        int importedFixtures = 0;
        int importedStats = 0;
        int failedSeasons = 0;

        for (League league : leagueRepository.findByIsActiveTrue()) {
            Optional<LeagueExternalId> mapping = leagueExternalIdRepository
                    .findByLeagueIdAndProvider(league.getId(), PROVIDER);
            if (mapping.isEmpty() || !StringUtils.hasText(mapping.get().getIdentify())) {
                log.warn("Skip Football-Data.co.uk league without identify mapping: leagueId={}, name={}",
                        league.getId(), league.getName());
                continue;
            }

            for (int startYear = 2014; startYear <= today.getYear(); startYear++) {
                Season season = getOrCreateSeason(league, startYear, today);
                if (!season.isActive()) {
                    continue;
                }
                boolean current = season.isCurrent();
                boolean alreadyImported = seasonExternalIdRepository
                        .findBySeasonIdAndProvider(season.getId(), PROVIDER).isPresent();
                if (!current && alreadyImported) {
                    continue;
                }
                try {
                    ImportResult result = syncSeason(season, mapping.get(), config);
                    importedFixtures += result.createdFixtures();
                    importedStats += result.importedTeamStats();
                    if (result.unmatchedRows() == 0 && result.csvRows() > 0) {
                        saveSeasonMapping(season, mapping.get());
                    } else {
                        log.warn("Football-Data.co.uk season remains pending because rows were unmatched or CSV was empty: league={}, season={}, unmatched={}, rows={}",
                                league.getName(), season.getYear(), result.unmatchedRows(), result.csvRows());
                    }
                    log.info("Football-Data.co.uk imported league={}, season={}, current={}, rows={}, createdFixtures={}, stats={}, unmatched={}",
                            league.getName(), season.getYear(), current, result.csvRows(),
                            result.createdFixtures(), result.importedTeamStats(), result.unmatchedRows());
                } catch (RuntimeException ex) {
                    failedSeasons++;
                    log.error("Football-Data.co.uk import failed: league={}, season={}, reason={}",
                            league.getName(), season.getYear(), ex.getMessage(), ex);
                }
            }
        }

        log.info("Football-Data.co.uk sync completed: createdFixtures={}, importedTeamStats={}, failedSeasons={}",
                importedFixtures, importedStats, failedSeasons);
        if (failedSeasons > 0) {
            throw new IllegalStateException("Football-Data.co.uk import failed for " + failedSeasons + " season(s)");
        }
    }

    @Transactional
    public ImportResult syncSeason(Season season, LeagueExternalId leagueMapping,
                                   AppProperties.ProviderConfig config) {
        String url = buildCsvUrl(config, season.getYear(), leagueMapping.getIdentify());
        List<Map<String, String>> rows = csvParser.parse(csvClient.getCsv(url));
        int createdFixtures = 0;
        int importedStats = 0;
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
                        date, time, homeName, awayName);

                Match match = matchExternalIdRepository.findByProviderAndExternalId(PROVIDER, fixtureExternalId)
                        .map(MatchExternalId::getMatch)
                        .orElseGet(() -> resolveOrCreateFixture(season, home, away, kickoff,
                                fixtureExternalId, row));
                if (match.getId() != null && matchExternalIdRepository
                        .findByMatchIdAndProvider(match.getId(), PROVIDER).isEmpty()) {
                    matchExternalIdRepository.save(MatchExternalId.builder()
                            .match(match).provider(PROVIDER).externalId(fixtureExternalId).build());
                }
                if (match.getId() == null) {
                    match = matchRepository.save(match);
                    matchExternalIdRepository.save(MatchExternalId.builder()
                            .match(match).provider(PROVIDER).externalId(fixtureExternalId).build());
                    createdFixtures++;
                } else {
                    updateFixtureResult(match, row);
                    matchRepository.save(match);
                }
                importedStats += persistStatistics(match, row);
            } catch (RuntimeException ex) {
                unmatched++;
                log.warn("Skip invalid Football-Data.co.uk row: season={}, home={}, away={}, reason={}",
                        season.getYear(), value(row, "HOMETEAM"), value(row, "AWAYTEAM"), ex.getMessage());
            }
        }
        return new ImportResult(rows.size(), createdFixtures, importedStats, unmatched);
    }

    private Season getOrCreateSeason(League league, int startYear, LocalDate today) {
        String year = startYear + "/" + (startYear + 1);
        return seasonRepository.findByLeague_IdAndYear(league.getId(), year).orElseGet(() -> {
            LocalDate start = LocalDate.of(startYear, 7, 1);
            LocalDate end = LocalDate.of(startYear + 1, 6, 30);
            boolean current = !today.isBefore(start) && !today.isAfter(end);
            return seasonRepository.save(Season.builder()
                    .league(league).year(year).startDate(start).endDate(end)
                    .isCurrent(current).isActive(true).build());
        }).map(existing -> {
            LocalDate start = LocalDate.of(startYear, 7, 1);
            LocalDate end = LocalDate.of(startYear + 1, 6, 30);
            boolean current = !today.isBefore(start) && !today.isAfter(end);
            if (existing.isCurrent() != current) {
                existing.setCurrent(current);
                return seasonRepository.save(existing);
            }
            return existing;
        });
    }

    private void saveSeasonMapping(Season season, LeagueExternalId leagueMapping) {
        if (seasonExternalIdRepository.findBySeasonIdAndProvider(season.getId(), PROVIDER).isPresent()) {
            return;
        }
        seasonExternalIdRepository.save(SeasonExternalId.builder()
                .season(season).provider(PROVIDER)
                .externalId(leagueMapping.getIdentify().trim().toUpperCase(Locale.ROOT)
                        + ":" + seasonCode(season.getYear()))
                .build());
    }

    private Team resolveTeam(String leagueCode, String providerName) {
        String externalId = normalize(leagueCode) + ":" + normalize(providerName);
        Optional<TeamExternalId> mapped = teamExternalIdRepository.findByProviderAndExternalId(PROVIDER, externalId);
        if (mapped.isPresent()) {
            return mapped.get().getTeam();
        }

        List<Team> exact = teamRepository.findAllByNameIgnoreCase(providerName.trim());
        Team team;
        if (exact.size() == 1) {
            team = exact.get(0);
        } else if (exact.isEmpty()) {
            team = teamRepository.save(Team.builder()
                    .name(providerName.trim())
                    .shortName(providerName.trim().length() > 80
                            ? providerName.trim().substring(0, 80) : providerName.trim())
                    .slug(slug(providerName))
                    .isActive(true)
                    .build());
        } else {
            // Do not guess between duplicate canonical names; create a provider-specific team.
            team = teamRepository.save(Team.builder().name(providerName.trim())
                    .shortName(providerName.trim().length() > 80
                            ? providerName.trim().substring(0, 80) : providerName.trim())
                    .slug(slug(providerName) + "-" + normalize(leagueCode))
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
                                         String externalId, Map<String, String> row) {
        List<Match> candidates = matchRepository.findFixtureCandidates(
                season.getId(), home.getId(), away.getId(),
                kickoff.minusSeconds(18 * 3600L), kickoff.plusSeconds(18 * 3600L));
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
        String fullTimeResult = value(row, "FTR");
        if (homeScore != null) match.setHomeScore(homeScore);
        if (awayScore != null) match.setAwayScore(awayScore);
        if (homeScore != null && awayScore != null
                && List.of("H", "D", "A").contains(fullTimeResult.toUpperCase(Locale.ROOT))) {
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
        Integer offsides = integer(value(row, side + "O"));
        if (shots == null && onTarget == null && corners == null && fouls == null
                && yellow == null && red == null && offsides == null) return false;

        MatchAdvancedStats stats = statsRepository.findByMatchIdAndTeamId(match.getId(), team.getId())
                .orElseGet(() -> MatchAdvancedStats.builder().match(match).team(team).build());
        if (shots != null) stats.setTotalShots(shots);
        if (onTarget != null) stats.setShotsOnTarget(onTarget);
        if (corners != null) stats.setCornerKicks(corners);
        if (fouls != null) stats.setFouls(fouls);
        if (yellow != null) stats.setYellowCards(yellow);
        if (red != null) stats.setRedCards(red);
        if (offsides != null) stats.setOffsides(offsides);
        if (!StringUtils.hasText(stats.getStatisticsProvider())) {
            stats.setStatisticsProvider(PROVIDER.name());
        }
        if (stats.getStatisticsSyncedAt() == null) stats.setStatisticsSyncedAt(Instant.now());
        statsRepository.save(stats);
        return true;
    }

    private String buildCsvUrl(AppProperties.ProviderConfig config, String season, String leagueCode) {
        String base = StringUtils.hasText(config.getUri())
                ? config.getUri().trim() : "https://www.football-data.co.uk";
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/mmz" + seasonCode(season) + "/"
                + leagueCode.trim().toUpperCase(Locale.ROOT) + ".csv";
    }

    private static String seasonCode(String season) {
        String digits = season.replaceAll("\\D", "");
        if (digits.length() < 4) throw new IllegalArgumentException("Unsupported season format: " + season);
        int startYear = Integer.parseInt(digits.substring(0, 4));
        return digits.substring(2, 4) + String.format(Locale.ROOT, "%02d", (startYear + 1) % 100);
    }

    private static String fixtureExternalId(String leagueCode, Season season, LocalDate date,
                                            LocalTime time, String home, String away) {
        return normalize(leagueCode) + ":" + seasonCode(season.getYear()) + ":"
                + date + ":" + (time == null ? "unknown" : time.toString()) + ":"
                + normalize(home) + ":" + normalize(away);
    }

    private static LocalDate parseDate(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        for (DateTimeFormatter formatter : List.of(CSV_DATE, DateTimeFormatter.ISO_LOCAL_DATE)) {
            try { return LocalDate.parse(raw.trim(), formatter); }
            catch (DateTimeParseException ignored) { }
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
        try { return (int) Double.parseDouble(raw.trim()); }
        catch (NumberFormatException ex) { return null; }
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

    public record ImportResult(int csvRows, int createdFixtures, int importedTeamStats, int unmatchedRows) {}
}
