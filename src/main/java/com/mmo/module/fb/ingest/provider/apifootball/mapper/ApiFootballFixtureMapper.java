package com.mmo.module.fb.ingest.provider.apifootball.mapper;

import com.mmo.core.converter.AbstractMapper;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.FixtureItem;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;

/** Converts API-Football's response schema into the source-neutral fixture model. */
@Component
public class ApiFootballFixtureMapper extends AbstractMapper<FixtureItem, ProviderFixture> {

    public ProviderFixture toModel(FixtureItem item) {
        return map(item, null);
    }

    @Override
    public ProviderFixture map(FixtureItem item, ProviderFixture target) {
        if (item == null || item.fixture() == null || item.fixture().id() == null
                || item.teams() == null || item.teams().home() == null
                || item.teams().away() == null
                || item.teams().home().id() == null || item.teams().away().id() == null) {
            return null;
        }

        Instant kickoff = parseInstant(item.fixture().date());
        Long timestamp = item.fixture().timestamp();
        if (timestamp == null && kickoff != null) timestamp = kickoff.getEpochSecond();

        return new ProviderFixture(
                item.fixture().id().toString(),
                item.teams().home().id().toString(),
                item.teams().away().id().toString(),
                kickoff,
                timestamp,
                item.league() == null ? null : item.league().round(),
                item.fixture().venue() == null ? null : item.fixture().venue().name(),
                item.fixture().referee(),
                item.fixture().status() == null ? null : item.fixture().status().shortName(),
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
