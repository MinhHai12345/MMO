package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.GroupDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.PeriodStatisticsDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.StatisticItemDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SofaScoreMatchStatisticsMapperTest {

    @Test
    void mapsAllPeriodStatisticsToProviderNeutralCanonicalKeys() {
        SofaScoreMatchStatisticsMapper mapper = new SofaScoreMatchStatisticsMapper();
        PeriodStatisticsDTO all = new PeriodStatisticsDTO("ALL", List.of(
                new GroupDTO("Match overview", List.of(
                        new StatisticItemDTO("Expected Goals", "expectedGoals",
                                "1.42", "0.83", new BigDecimal("1.42"), new BigDecimal("0.83"),
                                null, "positive", "event"),
                        new StatisticItemDTO("Ball Possession", "ballPossession",
                                "58%", "42%", null, null, null, "positive", "event")
                ))
        ));

        var result = mapper.toProviderStatistics("99881", List.of(all)).orElseThrow();

        assertEquals(new BigDecimal("1.42"), result.home().get("expectedgoals"));
        assertEquals(new BigDecimal("0.83"), result.away().get("expectedgoals"));
        assertEquals(new BigDecimal("58"), result.home().get("ballpossession"));
        assertEquals(new BigDecimal("42"), result.away().get("ballpossession"));
    }
}
