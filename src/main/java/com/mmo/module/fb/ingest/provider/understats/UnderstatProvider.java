package com.mmo.module.fb.ingest.provider.understats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderLeagueMonthlyStats;
import com.mmo.module.fb.ingest.provider.model.ProviderMatchStatistics;
import com.mmo.module.fb.ingest.provider.model.ProviderTeamMatchStats;
import com.mmo.module.fb.ingest.provider.spi.HistoricalStatsProvider;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderstatDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class UnderstatProvider implements HistoricalStatsProvider {

    private static final TypeReference<UnderstatDtos.LeagueData> LEAGUE_DATA =
            new TypeReference<>() { };
    private static final int DECIMAL_SCALE = 6;

    private static final long CACHE_TTL_SECONDS = 600;
    private final UnderstatClient client;
    private final UnderstatMapper mapper;
    private final ConcurrentMap<String, CachedLeagueData> leagueCache = new ConcurrentHashMap<>();

    @Override
    public Provider getProvider() {
        return Provider.UNDER_STATS;
    }

    @Override
    public List<ProviderMatchStatistics> fetchLeagueMatches(String leagueSlug, int season) {
        UnderstatDtos.LeagueData league = fetchLeagueData(leagueSlug, season);
        if (league.dates() == null || league.dates().isEmpty()) {
            return List.of();
        }
        return league.dates().stream()
                .filter(match -> match != null && StringUtils.hasText(match.id()))
                .map(mapper::toMatchStatistics)
                .toList();
    }

    @Override
    public List<ProviderTeamMatchStats> fetchTeamHistory(
            String leagueSlug, String teamSlug, int season) {
        if (!StringUtils.hasText(teamSlug)) {
            throw new IllegalArgumentException("teamSlug must not be blank");
        }

        UnderstatDtos.LeagueData league = fetchLeagueData(leagueSlug, season);
        if (league.teams() == null || league.teams().isEmpty()) {
            return List.of();
        }

        UnderstatDtos.LeagueTeam team = findTeam(league.teams(), teamSlug)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Team '" + teamSlug + "' was not found in Understat league "
                                + leagueSlug + " season " + season));
        if (team.history() == null || team.history().isEmpty()) {
            return List.of();
        }

        return team.history().stream()
                .filter(match -> match != null)
                .map(match -> mapper.toTeamMatchStats(team.id(), match))
                .toList();
    }

    @Override
    public Optional<ProviderLeagueMonthlyStats> fetchLeagueMonthlyStats(
            String leagueSlug, int season, YearMonth month) {
        if (month == null) {
            throw new IllegalArgumentException("month must not be null");
        }

        List<ProviderMatchStatistics> completed = fetchLeagueMatches(leagueSlug, season).stream()
                .filter(ProviderMatchStatistics::result)
                .filter(match -> match.kickoff() != null && YearMonth.from(match.kickoff()).equals(month))
                .filter(match -> match.homeScore() != null && match.awayScore() != null
                        && match.homeXg() != null && match.awayXg() != null)
                .toList();
        if (completed.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal homeGoals = average(completed.stream()
                .map(match -> BigDecimal.valueOf(match.homeScore())).toList());
        BigDecimal awayGoals = average(completed.stream()
                .map(match -> BigDecimal.valueOf(match.awayScore())).toList());
        BigDecimal homeXg = average(completed.stream().map(ProviderMatchStatistics::homeXg).toList());
        BigDecimal awayXg = average(completed.stream().map(ProviderMatchStatistics::awayXg).toList());

        return Optional.of(new ProviderLeagueMonthlyStats(
                leagueSlug, month.getYear(), month.getMonthValue(), completed.size(),
                homeGoals, awayGoals, homeXg, awayXg));
    }

    private UnderstatDtos.LeagueData fetchLeagueData(String leagueSlug, int season) {
        String cacheKey = leagueSlug + ":" + season;
        Instant now = Instant.now();
        CachedLeagueData cached = leagueCache.get(cacheKey);
        if (cached != null && cached.loadedAt().plusSeconds(CACHE_TTL_SECONDS).isAfter(now)) {
            return cached.data();
        }

        UnderstatDtos.LeagueData league = client.fetchLeagueData(leagueSlug, season, LEAGUE_DATA);
        if (league == null) {
            throw new IllegalStateException(
                    "Understat returned an empty league payload for " + leagueSlug + "/" + season);
        }
        leagueCache.put(cacheKey, new CachedLeagueData(league, now));
        return league;
    }

    private record CachedLeagueData(UnderstatDtos.LeagueData data, Instant loadedAt) {
    }

    private Optional<UnderstatDtos.LeagueTeam> findTeam(
            Map<String, UnderstatDtos.LeagueTeam> teams, String teamExternalId) {
        return teams.entrySet().stream()
                .map(entry -> {
                    UnderstatDtos.LeagueTeam team = entry.getValue();
                    if (team == null) return null;
                    String id = StringUtils.hasText(team.id()) ? team.id() : entry.getKey();
                    return teamExternalId.equals(id) || teamExternalId.equals(entry.getKey()) ? team : null;
                })
                .filter(team -> team != null)
                .findFirst();
    }

    private BigDecimal average(List<BigDecimal> values) {
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), DECIMAL_SCALE, RoundingMode.HALF_UP);
    }
}
