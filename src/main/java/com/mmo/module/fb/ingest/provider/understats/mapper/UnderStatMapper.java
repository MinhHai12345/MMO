package com.mmo.module.fb.ingest.provider.understats.mapper;

import com.mmo.module.fb.ingest.provider.model.MmoProviderDataStandardizer;
import com.mmo.module.fb.ingest.provider.model.ProviderMatchStatistics;
import com.mmo.module.fb.ingest.provider.model.ProviderPlayerSeasonStats;
import com.mmo.module.fb.ingest.provider.model.ProviderTeamMatchStats;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderStatDtos;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Converts Understat transport values to MMO's shared domain vocabulary. */
@Component
public class UnderStatMapper {
    private static final DateTimeFormatter SOURCE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String leagueName(String value) {
        return MmoProviderDataStandardizer.leagueName(value);
    }

    public String teamName(String value) {
        return MmoProviderDataStandardizer.teamName(value);
    }

    public String playerName(String value) {
        return MmoProviderDataStandardizer.displayText(value);
    }

    public String position(String value) {
        return MmoProviderDataStandardizer.displayText(value);
    }

    public String teamLookupKey(String value) {
        String normalized = teamName(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    public ProviderPlayerSeasonStats toPlayerSeasonStats(UnderStatDtos.LeaguePlayer player) {
        if (player == null || !StringUtils.hasText(player.id()) || !StringUtils.hasText(player.playerName())) {
            return null;
        }
        return new ProviderPlayerSeasonStats(
                player.id().trim(), playerName(player.playerName()), teamName(player.teamTitle()),
                position(player.position()), parseInteger(player.games()), parseInteger(player.time()),
                parseInteger(player.goals()), parseDecimal(player.xg()), parseInteger(player.assists()),
                parseDecimal(player.xa()), parseInteger(player.shots()), parseInteger(player.keyPasses()),
                parseInteger(player.yellowCards()), parseInteger(player.redCards()),
                parseInteger(player.npg()), parseDecimal(player.npxg()),
                parseDecimal(player.xgChain()), parseDecimal(player.xgBuildup()));
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return new java.math.BigDecimal(value.trim()).intValueExact();
        } catch (NumberFormatException | ArithmeticException ignored) {
            return null;
        }
    }

    private java.math.BigDecimal parseDecimal(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return new java.math.BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public ProviderMatchStatistics toMatchStatistics(UnderStatDtos.Match match) {
        if (match == null || !StringUtils.hasText(match.id())) return null;
        UnderStatDtos.TeamRef home = match.h();
        UnderStatDtos.TeamRef away = match.a();
        UnderStatDtos.Goals goals = match.goals();
        UnderStatDtos.Xg xg = match.xg();
        UnderStatDtos.Forecast forecast = match.forecast();
        return new ProviderMatchStatistics(
                match.id(),
                home == null ? null : home.id(),
                home == null ? null : teamName(home.title()),
                home == null ? null : MmoProviderDataStandardizer.teamShortName(home.shortTitle(), home.title()),
                away == null ? null : away.id(),
                away == null ? null : teamName(away.title()),
                away == null ? null : MmoProviderDataStandardizer.teamShortName(away.shortTitle(), away.title()),
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

    public ProviderTeamMatchStats toTeamMatchStats(String teamExternalId, UnderStatDtos.TeamHistoryMatch match) {
        if (match == null) return null;
        UnderStatDtos.Ppda ppda = match.ppda();
        UnderStatDtos.Ppda ppdaAllowed = match.ppdaAllowed();
        return new ProviderTeamMatchStats(
                teamExternalId, match.id(), MmoProviderDataStandardizer.displayText(match.homeAway()),
                MmoProviderDataStandardizer.displayText(match.date()), match.xG(), match.xGA(),
                match.npxG(), match.npxGA(), ppda == null ? null : ppda.att(),
                ppda == null ? null : ppda.def(), ppdaAllowed == null ? null : ppdaAllowed.att(),
                ppdaAllowed == null ? null : ppdaAllowed.def(), match.deep(), match.deepAllowed(),
                match.scored(), match.missed(), match.xpts(),
                MmoProviderDataStandardizer.displayText(match.result()), match.wins(), match.draws(),
                match.loses(), match.pts(), match.npxGD());
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return LocalDateTime.parse(value, SOURCE_DATETIME);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
