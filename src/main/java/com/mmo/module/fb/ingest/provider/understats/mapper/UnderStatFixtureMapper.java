package com.mmo.module.fb.ingest.provider.understats.mapper;

import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderStatDtos;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Maps UnderStat match rows to MMO's provider-neutral fixture model. */
@Component
public class UnderStatFixtureMapper {
    private static final DateTimeFormatter SOURCE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<ProviderFixture> toProviderFixtures(
            UnderStatDtos.LeagueData response, LocalDate from, LocalDate to) {
        if (response == null || response.dates() == null || response.dates().isEmpty()) {
            return List.of();
        }
        return response.dates().stream()
                .map(this::toProviderFixture)
                .filter(java.util.Objects::nonNull)
                .filter(fixture -> inRange(fixture, from, to))
                .toList();
    }

    private ProviderFixture toProviderFixture(UnderStatDtos.Match source) {
        if (source == null || !StringUtils.hasText(source.id())
                || source.h() == null || source.a() == null
                || !StringUtils.hasText(source.h().id()) || !StringUtils.hasText(source.a().id())) {
            return null;
        }
        Instant kickoff = parseKickoff(source.datetime());
        if (kickoff == null) return null;

        UnderStatDtos.Goals goals = source.goals();
        boolean finished = Boolean.TRUE.equals(source.isResult());
        return new ProviderFixture(
                source.id().trim(),
                source.h().id().trim(),
                source.a().id().trim(),
                kickoff,
                null,
                null,
                null,
                finished ? "FINISHED" : "SCHEDULED",
                goals == null ? null : goals.h(),
                goals == null ? null : goals.a());
    }

    private static Instant parseKickoff(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            // UnderStat's getLeagueData datetime is a provider-local wall-clock value.
            // Its pages use UTC timestamps; normalize consistently for MMO persistence.
            return LocalDateTime.parse(value.trim(), SOURCE_DATETIME).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static boolean inRange(ProviderFixture fixture, LocalDate from, LocalDate to) {
        LocalDate date = fixture.kickoff().atZone(ZoneOffset.UTC).toLocalDate();
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }
}
