package com.mmo.module.fb.ingest.provider.understats.mapper;

import com.mmo.module.fb.ingest.provider.model.MmoProviderDataStandardizer;
import com.mmo.module.fb.ingest.provider.model.ProviderMatchStatistics;
import com.mmo.module.fb.ingest.provider.model.ProviderTeamMatchStats;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderstatDtos;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Converts Understat transport DTOs into provider-neutral MMO models.
 *
 * <p>Only values are normalized here; Understat IDs are preserved exactly as
 * supplied by the source and must be resolved through explicit external-ID mappings.</p>
 */
@Component
public class UnderstatMapper {

    private static final DateTimeFormatter SOURCE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ProviderMatchStatistics toMatchStatistics(UnderstatDtos.Match match) {
        if (match == null || !StringUtils.hasText(match.id())) {
            return null;
        }

        UnderstatDtos.TeamRef home = match.h();
        UnderstatDtos.TeamRef away = match.a();
        UnderstatDtos.Goals goals = match.goals();
        UnderstatDtos.Xg xg = match.xg();
        UnderstatDtos.Forecast forecast = match.forecast();

        return new ProviderMatchStatistics(
                match.id(),
                home == null ? null : home.id(),
                home == null ? null : MmoProviderDataStandardizer.teamName(home.title()),
                home == null ? null : MmoProviderDataStandardizer.teamShortName(home.shortTitle(), home.title()),
                away == null ? null : away.id(),
                away == null ? null : MmoProviderDataStandardizer.teamName(away.title()),
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

    public ProviderTeamMatchStats toTeamMatchStats(
            String teamExternalId, UnderstatDtos.TeamHistoryMatch match) {
        if (match == null) {
            return null;
        }

        UnderstatDtos.Ppda ppda = match.ppda();
        UnderstatDtos.Ppda ppdaAllowed = match.ppdaAllowed();
        return new ProviderTeamMatchStats(
                teamExternalId,
                match.id(),
                MmoProviderDataStandardizer.displayText(match.homeAway()),
                MmoProviderDataStandardizer.displayText(match.date()),
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
                MmoProviderDataStandardizer.displayText(match.result()),
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
}
