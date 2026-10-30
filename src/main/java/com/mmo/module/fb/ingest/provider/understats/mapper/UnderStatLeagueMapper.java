package com.mmo.module.fb.ingest.provider.understats.mapper;

import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderStatDtos;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Maps UnderStat's stat catalogue into independent league and season resources. */
@Component
public class UnderStatLeagueMapper {
    private static final Map<String, LeagueDefinition> LEAGUES = Map.of(
            "epl", new LeagueDefinition("EPL", "Premier League", "England"),
            "premier league", new LeagueDefinition("EPL", "Premier League", "England"),
            "la liga", new LeagueDefinition("La_Liga", "LaLiga", "Spain"),
            "laliga", new LeagueDefinition("La_Liga", "LaLiga", "Spain"),
            "bundesliga", new LeagueDefinition("Bundesliga", "Bundesliga", "Germany"),
            "serie a", new LeagueDefinition("Serie_A", "Serie A", "Italy"),
            "ligue 1", new LeagueDefinition("Ligue_1", "Ligue 1", "France"),
            "rfpl", new LeagueDefinition("RFPL", "Russian Premier League", "Russia"),
            "russian premier league", new LeagueDefinition("RFPL", "Russian Premier League", "Russia")
    );

    /** Maps league metadata together with the seasons exposed by the same catalogue response. */
    public List<ProviderLeague> toProviderLeagues(UnderStatDtos.StatDataResponse response) {
        Map<String, LeagueAccumulator> leagues = group(response);
        return leagues.values().stream()
                .map(source -> new ProviderLeague(
                        source.externalId(),
                        source.definition().identify(),
                        source.definition().name(),
                        source.definition().country(),
                        null,
                        toProviderSeasons(source)))
                .toList();
    }

    /** Returns seasons for one UnderStat league, preserving its provider-specific season IDs. */
    public List<ProviderSeason> toProviderSeasons(
            UnderStatDtos.StatDataResponse response, String leagueExternalId) {
        if (!StringUtils.hasText(leagueExternalId)) return List.of();
        LeagueAccumulator league = group(response).get(leagueExternalId.trim());
        return league == null ? List.of() : toProviderSeasons(league);
    }

    private static List<ProviderSeason> toProviderSeasons(LeagueAccumulator league) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int currentSeasonStartYear = today.getMonthValue() >= 7
                ? today.getYear() : today.getYear() - 1;

        // /getStatData is a sparse monthly statistics catalogue. It must not be
        // treated as the authoritative list of historical seasons.
        return java.util.stream.IntStream.rangeClosed(2014, currentSeasonStartYear)
                .mapToObj(year -> new ProviderSeason(
                        seasonExternalId(league.externalId(), String.valueOf(year)),
                        year,
                        year == currentSeasonStartYear,
                        null,
                        null))
                .toList();
    }

    /**
     * UnderStat exposes a league ID and a year, but no independent season ID.
     * Compose both values to keep each season unique within the provider namespace.
     */
    static String seasonExternalId(String leagueExternalId, String year) {
        return leagueExternalId + ":" + year;
    }

    private static Map<String, LeagueAccumulator> group(UnderStatDtos.StatDataResponse response) {
        if (response == null || response.stat() == null || response.stat().isEmpty()) {
            return Map.of();
        }

        Map<String, LeagueAccumulator> leagues = new LinkedHashMap<>();
        for (UnderStatDtos.LeagueStat row : response.stat()) {
            if (row == null || !StringUtils.hasText(row.leagueId())
                    || !StringUtils.hasText(row.league())) {
                continue;
            }

            String externalId = row.leagueId().trim();
            LeagueDefinition definition = definition(row.league());
            LeagueAccumulator accumulator = leagues.computeIfAbsent(
                    externalId, ignored -> new LeagueAccumulator(externalId, definition));
            String seasonStartYear = toSeasonStartYear(row.year(), row.month());
            if (seasonStartYear != null) {
                accumulator.seasonStartYears().add(seasonStartYear);
            }
        }
        return leagues;
    }

    private static LeagueDefinition definition(String sourceName) {
        String key = sourceName.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        LeagueDefinition known = LEAGUES.get(key);
        if (known != null) return known;
        String identify = sourceName.trim().replaceAll("\\s+", "_");
        return new LeagueDefinition(identify, sourceName.trim(), null);
    }

    /**
     * UnderStat's /getStatData catalogue is grouped by calendar month, not by football season.
     * Seasons start in July: January-June rows belong to the season that started the previous year.
     */
    private static String toSeasonStartYear(String calendarYearValue, String monthValue) {
        if (calendarYearValue == null || !calendarYearValue.matches("\\d{4}")
                || monthValue == null || !monthValue.matches("\\d{1,2}")) {
            return null;
        }
        int calendarYear = Integer.parseInt(calendarYearValue);
        int month = Integer.parseInt(monthValue);
        if (month < 1 || month > 12) return null;
        return String.valueOf(month >= 7 ? calendarYear : calendarYear - 1);
    }

    private static boolean isSeasonStartYear(String value) {
        if (value == null || !value.matches("\\d{4}")) return false;
        int year = Integer.parseInt(value);
        return year >= 2014 && year <= LocalDate.now(ZoneOffset.UTC).getYear();
    }

    private record LeagueDefinition(String identify, String name, String country) { }

    private record LeagueAccumulator(
            String externalId,
            LeagueDefinition definition,
            Set<String> seasonStartYears) {
        private LeagueAccumulator(String externalId, LeagueDefinition definition) {
            this(externalId, definition, new LinkedHashSet<>());
        }
    }
}
