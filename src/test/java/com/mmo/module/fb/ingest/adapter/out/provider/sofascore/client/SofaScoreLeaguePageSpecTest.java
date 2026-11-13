package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SofaScoreLeaguePageSpecTest {

    @Test
    void definesFootballPageWithRegionCatalogueAndDynamicSeasonEndpoint() {
        SofaScoreLeaguePageSpec spec =
                SofaScorePageSpecs.footballLeagueCatalogue("https://www.sofascore.com/", "VN");

        assertEquals("https://www.sofascore.com/football", spec.loadPageUrl());
        assertEquals("/api/v1/config/top-unique-tournaments/VN/football", spec.catalogueEndpoint());
        assertTrue(spec.isCatalogueUrl(
                "https://www.sofascore.com/api/v1/config/top-unique-tournaments/VN/football"));
        assertEquals(7L, spec.tournamentIdFromSeasonUrl(
                "https://www.sofascore.com/api/v1/unique-tournament/7/seasons"));
        assertEquals(17L, spec.tournamentIdFromSeasonUrl(
                "https://www.sofascore.com/api/v1/unique-tournament/17/seasons?x=1"));
    }

    @Test
    void ignoresUnrelatedOrMalformedApiUrls() {
        SofaScoreLeaguePageSpec spec =
                SofaScorePageSpecs.footballLeagueCatalogue("https://www.sofascore.com", "VN");

        assertNull(spec.tournamentIdFromSeasonUrl(
                "https://www.sofascore.com/api/v1/unique-tournament/17/standings"));
        assertNull(spec.tournamentIdFromSeasonUrl(
                "https://www.sofascore.com/api/v1/unique-tournament/not-a-number/seasons"));
        assertFalse(spec.isCatalogueUrl(
                "https://www.sofascore.com/api/v1/config/top-unique-tournaments/EN/football"));
    }

    @Test
    void defaultsInvalidRegionToEnglish() {
        SofaScoreLeaguePageSpec spec =
                SofaScorePageSpecs.footballLeagueCatalogue("https://www.sofascore.com", "VNM");

        assertEquals("/api/v1/config/top-unique-tournaments/EN/football", spec.catalogueEndpoint());
    }
}
