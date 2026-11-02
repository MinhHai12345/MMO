package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.footballdatauk.FootballDataUkCsvClient;
import com.mmo.module.fb.league.entity.LeagueExternalId;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.league.entity.SeasonExternalId;
import com.mmo.module.fb.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.league.repository.SeasonRepository;
import com.mmo.module.fb.league.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchAdvancedStats;
import com.mmo.module.fb.match.repository.MatchAdvancedStatsRepository;
import com.mmo.module.fb.match.repository.MatchRepository;
import com.mmo.module.fb.team.entity.Team;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Imports CSV statistics by URL and enriches existing MMO fixtures only. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FootballDataUkSyncService {
    private static final Provider PROVIDER = Provider.FOOTBALL_DATA_UK;
    private static final ZoneId SOURCE_ZONE = ZoneId.of("Europe/London");
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("dd/MM/yy");

    private final AppProperties properties;
    private final FootballDataUkCsvClient csvClient;
    private final SeasonRepository seasonRepository;
    private final SeasonExternalIdRepository seasonExternalIdRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final MatchRepository matchRepository;
    private final MatchAdvancedStatsRepository statsRepository;

    /**
     * One-time bootstrap for every existing active-league season from 2014 onward.
     * After bootstrap, only the current season is fetched on subsequent job runs.
     */
    public void syncActiveSeasons() {
        AppProperties.ProviderConfig config = properties.getIngestion().getProviderConfig().get(PROVIDER);
        if (config == null || !config.isEnabled()) {
            log.info("Football-Data.co.uk import disabled");
            return;
        }

        List<Season> seasons = seasonRepository.findAllForActiveLeagues().stream()
                .filter(season -> seasonStartYear(season.getYear()) >= 2014)
                .toList();

        int importedRows = 0;
        int historicalPending = 0;
        int failed = 0;
        for (Season season : seasons) {
            boolean current = season.isCurrent();
            boolean alreadyImported = seasonExternalIdRepository
                    .findBySeasonIdAndProvider(season.getId(), PROVIDER).isPresent();
            if (!current && alreadyImported) {
                continue;
            }

            if (!current && !alreadyImported) historicalPending++;
            try {
                int count = syncSeason(season, config);
                importedRows += count;
                if (!alreadyImported) {
                    seasonExternalIdRepository.save(SeasonExternalId.builder()
                            .season(season)
                            .provider(PROVIDER)
                            .externalId(buildSeasonExternalId(season))
                            .build());
                }
            } catch (RuntimeException ex) {
                failed++;
                log.error("Football-Data.co.uk import failed: seasonId={}, year={}, current={}, reason={}",
                        season.getId(), season.getYear(), current, ex.getMessage(), ex);
            }
        }

        log.info("Football-Data.co.uk sync summary: eligibleSeasons={}, historicalPending={}, importedTeamRows={}, failedSeasons={}",
                seasons.size(), historicalPending, importedRows, failed);
        if (failed > 0) {
            throw new IllegalStateException("Football-Data.co.uk import failed for " + failed + " season(s)");
        }
    }

    private static int seasonStartYear(String year) {
        if (!StringUtils.hasText(year)) return -1;
        String digits = year.replaceAll("\\D", "");
        if (digits.length() < 4) return -1;
        try {
            return Integer.parseInt(digits.substring(0, 4));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    @Transactional
    public int syncSeason(Season season, AppProperties.ProviderConfig config) {
        LeagueExternalId mapping = leagueExternalIdRepository
                .findByLeagueIdAndProvider(season.getLeague().getId(), PROVIDER).orElse(null);
        if (mapping == null || !StringUtils.hasText(mapping.getIdentify())) {
            throw new IllegalStateException("Missing FOOTBALL_DATA_UK identify mapping for season " + season.getYear());
        }
        String url = buildCsvUrl(config, season.getYear(), mapping.getIdentify());
        List<Map<String, String>> rows = parseCsv(csvClient.getCsv(url));
        int saved = 0, unmatched = 0;
        for (Map<String, String> row : rows) {
            Match match = resolveMatch(season, row);
            if (match == null) { unmatched++; continue; }
            if (persistSide(match, match.getHomeTeam(), row, "H")) saved++;
            if (persistSide(match, match.getAwayTeam(), row, "A")) saved++;
        }
        log.info("Football-Data.co.uk season summary: league={}, season={}, url={}, csvRows={}, importedTeamRows={}, unmatchedRows={}",
                season.getLeague().getName(), season.getYear(), url, rows.size(), saved, unmatched);
        return saved;
    }

    private String buildSeasonExternalId(Season season) {
        LeagueExternalId mapping = leagueExternalIdRepository
                .findByLeagueIdAndProvider(season.getLeague().getId(), PROVIDER)
                .orElseThrow(() -> new IllegalStateException("Missing FOOTBALL_DATA_UK league mapping"));
        String digits = season.getYear().replaceAll("\\D", "");
        if (digits.length() < 4) throw new IllegalArgumentException("Unsupported season format: " + season.getYear());
        int startYear = Integer.parseInt(digits.substring(0, 4));
        String seasonCode = digits.substring(2, 4)
                + String.format(Locale.ROOT, "%02d", (startYear + 1) % 100);
        return mapping.getIdentify().trim().toUpperCase(Locale.ROOT) + ":" + seasonCode;
    }

    String buildCsvUrl(AppProperties.ProviderConfig config, String season, String leagueCode) {
        String base = StringUtils.hasText(config.getUri()) ? config.getUri().trim() : "https://www.football-data.co.uk";
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String digits = season.replaceAll("\\D", "");
        if (digits.length() < 4) throw new IllegalArgumentException("Unsupported season format: " + season);
        String start = digits.substring(0, 4);
        int startYear = Integer.parseInt(start);
        String seasonCode = start.substring(2) + String.format(Locale.ROOT, "%02d", (startYear + 1) % 100);
        String code = leagueCode.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9]{2,4}")) throw new IllegalArgumentException("Invalid league identify: " + code);
        return base + "/mmz" + seasonCode + "/" + code + ".csv";
    }

    private List<Map<String, String>> parseCsv(String csv) {
        return new com.mmo.module.fb.ingest.provider.footballdatauk.FootballDataUkCsvParser().parse(csv);
    }

    private Match resolveMatch(Season season, Map<String, String> row) {
        String home = value(row, "HOMETEAM"), away = value(row, "AWAYTEAM");
        LocalDate date = parseDate(value(row, "DATE"));
        if (!StringUtils.hasText(home) || !StringUtils.hasText(away) || date == null) return null;
        LocalTime time = parseTime(value(row, "TIME"));
        Instant kickoff = date.atTime(time == null ? LocalTime.NOON : time).atZone(SOURCE_ZONE).toInstant();
        List<Match> candidates = matchRepository.findFixtureCandidatesBySeasonAndTime(
                season.getId(), kickoff.minusSeconds(18 * 3600L), kickoff.plusSeconds(18 * 3600L));
        String homeKey = normalize(home), awayKey = normalize(away);
        List<Match> matched = candidates.stream()
                .filter(m -> normalize(m.getHomeTeam().getName()).equals(homeKey)
                        && normalize(m.getAwayTeam().getName()).equals(awayKey))
                .toList();
        if (matched.size() != 1) return null;
        Match match = matched.get(0);
        Integer homeScore = integer(value(row, "FTHG")), awayScore = integer(value(row, "FTAG"));
        if (homeScore != null && match.getHomeScore() != null && !homeScore.equals(match.getHomeScore())) return null;
        if (awayScore != null && match.getAwayScore() != null && !awayScore.equals(match.getAwayScore())) return null;
        return match;
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
        if (!StringUtils.hasText(stats.getStatisticsProvider())) stats.setStatisticsProvider(PROVIDER.name());
        if (stats.getStatisticsSyncedAt() == null) stats.setStatisticsSyncedAt(Instant.now());
        statsRepository.save(stats);
        return true;
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
    private static String value(Map<String, String> row, String key) { return row.getOrDefault(key, "").trim(); }
    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
