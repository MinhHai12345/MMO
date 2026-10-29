package com.mmo.module.fb.ingest.provider.understats.mapper;

import com.mmo.module.fb.ingest.provider.model.ProviderMatchStatistics;
import com.mmo.module.fb.ingest.provider.model.ProviderTeamMatchStats;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderstatDtos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UnderstatMapperTest {

    private final UnderstatMapper mapper = new UnderstatMapper();

    @Test
    void mapsMatchDtoIntoProviderNeutralModelAndStandardizesTeamNames() {
        UnderstatDtos.Match source = new UnderstatDtos.Match(
                "match-123",
                true,
                new UnderstatDtos.TeamRef("home-1", "  Manchester   City ", " Man City "),
                new UnderstatDtos.TeamRef("away-2", "Arsenal", "ARS"),
                new UnderstatDtos.Goals(2, 1),
                new UnderstatDtos.Xg(new BigDecimal("1.85"), new BigDecimal("0.92")),
                "2026-10-20 19:00:00",
                new UnderstatDtos.Forecast(
                        new BigDecimal("0.60"), new BigDecimal("0.25"), new BigDecimal("0.15")));

        ProviderMatchStatistics mapped = mapper.toMatchStatistics(source);

        assertThat(mapped.externalMatchId()).isEqualTo("match-123");
        assertThat(mapped.homeTeamExternalId()).isEqualTo("home-1");
        assertThat(mapped.homeTeamName()).isEqualTo("Manchester City");
        assertThat(mapped.homeTeamShortName()).isEqualTo("Man City");
        assertThat(mapped.awayTeamName()).isEqualTo("Arsenal");
        assertThat(mapped.kickoff()).isNotNull();
        assertThat(mapped.result()).isTrue();
        assertThat(mapped.homeScore()).isEqualTo(2);
        assertThat(mapped.homeXg()).isEqualByComparingTo("1.85");
    }

    @Test
    void handlesOptionalMatchSectionsAndInvalidDate() {
        UnderstatDtos.Match source = new UnderstatDtos.Match(
                "match-124", false, null, null, null, null, "not-a-date", null);

        ProviderMatchStatistics mapped = mapper.toMatchStatistics(source);

        assertThat(mapped).isNotNull();
        assertThat(mapped.kickoff()).isNull();
        assertThat(mapped.homeTeamName()).isNull();
        assertThat(mapped.homeScore()).isNull();
        assertThat(mapped.homeXg()).isNull();
        assertThat(mapped.result()).isFalse();
    }

    @Test
    void mapsTeamHistoryStatsAndKeepsSourceIds() {
        UnderstatDtos.TeamHistoryMatch source = new UnderstatDtos.TeamHistoryMatch(
                " h ", new BigDecimal("1.2"), new BigDecimal("0.7"),
                new BigDecimal("1.0"), new BigDecimal("0.5"),
                new UnderstatDtos.Ppda(10, 8), new UnderstatDtos.Ppda(9, 7),
                4, 2, 2, 1, new BigDecimal("1.5"), " w ", "2026-10-20",
                1, 0, 0, 3, new BigDecimal("0.5"), "match-123");

        ProviderTeamMatchStats mapped = mapper.toTeamMatchStats("understat-team-9", source);

        assertThat(mapped.externalTeamId()).isEqualTo("understat-team-9");
        assertThat(mapped.externalMatchId()).isEqualTo("match-123");
        assertThat(mapped.homeAway()).isEqualTo("h");
        assertThat(mapped.result()).isEqualTo("w");
        assertThat(mapped.ppdaAtt()).isEqualTo(10);
        assertThat(mapped.xg()).isEqualByComparingTo("1.2");
    }

    @Test
    void returnsNullForNullSourceDtos() {
        assertThat(mapper.toMatchStatistics(null)).isNull();
        assertThat(mapper.toTeamMatchStats("team-1", null)).isNull();
    }
}
