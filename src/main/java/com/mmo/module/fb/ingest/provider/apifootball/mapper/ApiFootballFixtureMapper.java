package com.mmo.module.fb.ingest.provider.apifootball.mapper;

import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.FixtureItem;
import com.mmo.module.fb.ingest.provider.model.MmoProviderDataStandardizer;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * Converts API-Football's response schema into the source-neutral fixture model.
 */
@Component
public class ApiFootballFixtureMapper {

    public ProviderFixture toModel(FixtureItem item) {
        if (item == null || item.fixture() == null || item.fixture().id() == null
            || item.teams() == null || item.teams().home() == null
            || item.teams().away() == null
            || item.teams().home().id() == null || item.teams().away().id() == null) {
            return null;
        }

        Instant kickoff = parseInstant(item.fixture().date());
        return new ProviderFixture(
                item.fixture().id().toString(),
                item.teams().home().id().toString(),
                item.teams().away().id().toString(),
                kickoff,
                item.league() == null ? null : MmoProviderDataStandardizer.roundName(item.league().round()),
                item.fixture().venue() == null ? null : MmoProviderDataStandardizer.venueName(item.fixture().venue().name()),
                MmoProviderDataStandardizer.refereeName(item.fixture().referee()),
                item.fixture().status() == null ? null : MmoProviderDataStandardizer.fixtureStatus(item.fixture().status().shortName()),
                item.goals() == null ? null : item.goals().home(),
                item.goals() == null ? null : item.goals().away()
        );
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
