package com.mmo.module.fb.ingest.adapter.out.provider.sofascore;

import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.SeasonDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SofaScoreSeasonMatcherTest {

    @Test
    void resolvesSeasonUsingCanonicalStartYear() {
        SeasonDTO oldSeason = new SeasonDTO(1L, "2024/2025", "2024/2025", false);
        SeasonDTO targetSeason = new SeasonDTO(2L, "2026/2027", "2026/2027", true);

        assertEquals(2L, SofaScoreSeasonMatcher.findMatchingSeason(
                List.of(oldSeason, targetSeason), "26/27").orElseThrow().id());
    }

    @Test
    void returnsFirstNonNullSeasonWhenNoSeasonWasRequested() {
        SeasonDTO expected = new SeasonDTO(2L, "2026/2027", "2026/2027", true);

        assertEquals(expected, SofaScoreSeasonMatcher.findMatchingSeason(
                java.util.Arrays.asList(null, expected), null).orElseThrow());
    }

    @Test
    void returnsEmptyWhenRequestedSeasonDoesNotExist() {
        assertTrue(SofaScoreSeasonMatcher.findMatchingSeason(
                List.of(new SeasonDTO(1L, "2024/2025", "2024/2025", false)), "2026/2027").isEmpty());
    }
}
