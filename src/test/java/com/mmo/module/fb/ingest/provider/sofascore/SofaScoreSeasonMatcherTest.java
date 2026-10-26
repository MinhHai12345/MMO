package com.mmo.module.fb.ingest.provider.sofascore;

import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.SeasonDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SofaScoreSeasonMatcherTest {

    @Test
    void matchesCanonicalStartYearToSofaScoreSeasonLabels() {
        assertTrue(SofaScoreSeasonMatcher.matches("2026",
                new SeasonDTO(1L, "Bundesliga 2026/2027", "26/27", false)));
        assertTrue(SofaScoreSeasonMatcher.matches("2025",
                new SeasonDTO(2L, "2025/2026", "2025", false)));
    }

    @Test
    void canonicalizesTwoDigitSeasonLabels() {
        assertEquals("2026", SofaScoreSeasonMatcher.canonicalStartYear("26/27"));
        assertEquals("2025", SofaScoreSeasonMatcher.canonicalStartYear("Bundesliga 2025/2026"));
    }

    @Test
    void doesNotMatchDifferentSeason() {
        assertFalse(SofaScoreSeasonMatcher.matches("2024",
                new SeasonDTO(2L, "Bundesliga 2026/2027", "26/27", false)));
    }

    @Test
    void nullRequestedSeasonMeansProviderMayChooseCurrentSeason() {
        assertTrue(SofaScoreSeasonMatcher.matches(null,
                new SeasonDTO(1L, "Bundesliga 2026/2027", "26/27", true)));
    }
}
