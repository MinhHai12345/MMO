package com.mmo.module.fb.ingest.provider.understats.mapper;

import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderStatDtos;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Maps UnderStat's monthly stat catalogue to MMO's provider-neutral league contract. */
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

    public List<ProviderLeague> toProviderLeagues(UnderStatDtos.StatDataResponse response) {
        if (response == null || response.stat() == null || response.stat().isEmpty()) {
            return List.of();
        }

        Map<String, LeagueAccumulator> leagues = new LinkedHashMap<>();
        for (UnderStatDtos.LeagueStat row : response.stat()) {
            if (row == null || !StringUtils.hasText(row.leagueId())
                    || !StringUtils.hasText(row.league())) {
                continue;
            }

            String externalId = row.leagueId().trim();
            LeagueDefinition definition = definition(row.league());
            LeagueAccumulator accumulator = leagues.computeIfAbsent(externalId,
                    ignored -> new LeagueAccumulator(externalId, definition));
            if (StringUtils.hasText(row.year())) {
                accumulator.years().add(row.year().trim());
            }
        }

        int currentYear = Year.now(ZoneOffset.UTC).getValue();
        List<ProviderLeague> result = new ArrayList<>();
        for (LeagueAccumulator accumulator : leagues.values()) {
            List<ProviderSeason> seasons = accumulator.years().stream()
                    .filter(UnderStatLeagueMapper::isYear)
                    .sorted(Comparator.comparingInt(Integer::parseInt))
                    .map(year -> new ProviderSeason(
                            accumulator.externalId() + ":" + year,
                            year,
                            Integer.parseInt(year) == currentYear,
                            null,
                            null))
                    .toList();

            LeagueDefinition definition = accumulator.definition();
            result.add(new ProviderLeague(
                    accumulator.externalId(),
                    definition.identify(),
                    definition.name(),
                    definition.country(),
                    null,
                    seasons));
        }
        return List.copyOf(result);
    }

    private static LeagueDefinition definition(String sourceName) {
        String key = sourceName.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        LeagueDefinition known = LEAGUES.get(key);
        if (known != null) return known;

        String identify = sourceName.trim().replaceAll("\\s+", "_");
        return new LeagueDefinition(identify, sourceName.trim(), null);
    }

    private static boolean isYear(String value) {
        if (value == null || !value.matches("\\d{4}")) return false;
        int year = Integer.parseInt(value);
        return year >= 2014 && year <= Year.now(ZoneOffset.UTC).getValue();
    }

    private record LeagueDefinition(String identify, String name, String country) { }

    private record LeagueAccumulator(
            String externalId,
            LeagueDefinition definition,
            Set<String> years) {
        private LeagueAccumulator(String externalId, LeagueDefinition definition) {
            this(externalId, definition, new LinkedHashSet<>());
        }
    }
}
