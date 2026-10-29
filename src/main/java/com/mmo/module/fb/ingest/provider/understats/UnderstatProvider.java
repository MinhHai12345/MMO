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
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UnderstatProvider implements HistoricalStatsProvider {

    private static final TypeReference<List<UnderstatDtos.Match>> MATCH_LIST =
            new TypeReference<>() { };
    private static final TypeReference<UnderstatDtos.TeamData> TEAM_DATA =
            new TypeReference<>() { };
    private static final DateTimeFormatter SOURCE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DECIMAL_SCALE = 6;

    private final UnderstatClient client;

    @Override
    public Provider getProvider() {
        return Provider.UNDER_STATS;
    }

    @Override
    public List<ProviderMatchStatistics> fetchLeagueMatches(String leagueSlug, int season) {
        List<UnderstatDtos.Match> matches = client.fetchLeaguePageData(
                leagueSlug, season, "datesData", MATCH_LIST);
        if (matches == null || matches.isEmpty()) {
            return List.of();
        }
        return matches.stream()
                .filter(match -> match != null && StringUtils.hasText(match.id()))
                .map(this::toMatchStatistics)
                .toList();
    }

    @Override
    public List<ProviderTeamMatchStats> fetchTeamHistory(String teamSlug, int season) {
        UnderstatDtos.TeamData team = client.fetchTeamPageData(teamSlug, season, "teamData", TEAM_DATA);
        if (team == null || team.history() == null || team.history().isEmpty()) {
            return List.of();
        }
        String teamId = team.id();
        return team.history().stream()
                .filter(match -> match != null)
                .map(match -> toTeamMatchStats(teamId, match))
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

    private ProviderMatchStatistics toMatchStatistics(UnderstatDtos.Match match) {
        UnderstatDtos.TeamRef home = match.h();
        UnderstatDtos.TeamRef away = match.a();
        UnderstatDtos.Goals goals = match.goals();
        UnderstatDtos.Xg xg = match.xg();
        UnderstatDtos.Forecast forecast = match.forecast();
        return new ProviderMatchStatistics(
                match.id(),
                home == null ? null : home.id(),
                home == null ? null : home.title(),
                home == null ? null : home.shortTitle(),
                away == null ? null : away.id(),
                away == null ? null : away.title(),
                away == null ? null : away.shortTitle(),
                parseDateTime(match.datetime()),
                Boolean.TRUE.equals(match.isResult()),
                goals == null ? null : goals.h(),
                goals == null ? null : goals.a(),
                xg == null ? null : xg.h(),
                xg == null ? null : xg.a(),
                forecast == null ? null : forecast.w(),
                forecast == null ? null : forecast.d(),
                forecast == null ? null : forecast.l());
    }

    private ProviderTeamMatchStats toTeamMatchStats(
            String teamId, UnderstatDtos.TeamHistoryMatch match) {
        UnderstatDtos.Ppda ppda = match.ppda();
        UnderstatDtos.Ppda ppdaAllowed = match.ppdaAllowed();
        return new ProviderTeamMatchStats(
                teamId,
                match.id(),
                match.homeAway(),
                match.date(),
                match.xG(),
                match.xGA(),
                match.npxG(),
                match.npxGA(),
                ppda == null ? null : ppda.att(),
                ppda == null ? null : ppda.def(),
                ppdaAllowed == null ? null : ppdaAllowed.att(),
                ppdaAllowed == null ? null : ppdaAllowed.def(),
                match.deep(),
                match.deepAllowed(),
                match.scored(),
                match.missed(),
                match.xpts(),
                match.result(),
                match.wins(),
                match.draws(),
                match.loses(),
                match.pts(),
                match.npxGD());
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, SOURCE_DATETIME);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private BigDecimal average(List<BigDecimal> values) {
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), DECIMAL_SCALE, RoundingMode.HALF_UP);
    }
}
