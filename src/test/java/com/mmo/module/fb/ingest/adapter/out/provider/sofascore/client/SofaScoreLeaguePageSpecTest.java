package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SofaScoreLeaguePageSpecTest {

    @Test
    void declaresFootballCatalogueAndDynamicSeasonEndpointOnSharedPageSpec() {
        SofaScorePageSpec spec = SofaScorePageSpecs.footballLeagueCatalogue(
                "https://www.sofascore.com/", "VN");

        assertEquals("https://www.sofascore.com/football", spec.loadPageUrl());
        assertEquals(2, spec.endpointSpecs().size());
        assertTrue(spec.endpointSpecs().get(0).matchesUrl(
                "https://www.sofascore.com/api/v1/config/top-unique-tournaments/VN/football"));

        SofaScoreEndpointSpec seasons = spec.endpointSpecs().get(1);
        assertTrue(seasons.dynamic());
        assertEquals(7L, seasons.captureLong(
                "https://www.sofascore.com/api/v1/unique-tournament/7/seasons", 1));
        assertEquals(17L, seasons.captureLong(
                "https://www.sofascore.com/api/v1/unique-tournament/17/seasons?x=1", 1));
    }

    @Test
    void ignoresUnrelatedOrMalformedDynamicEndpointUrls() {
        SofaScoreEndpointSpec seasons = SofaScorePageSpecs.footballLeagueCatalogue(
                "https://www.sofascore.com", "VN").endpointSpecs().get(1);

        assertFalse(seasons.matchesUrl(
                "https://www.sofascore.com/api/v1/unique-tournament/17/standings"));
        assertNull(seasons.captureLong(
                "https://www.sofascore.com/api/v1/unique-tournament/not-a-number/seasons", 1));
    }

    @Test
    void defaultsInvalidRegionToEnglish() {
        SofaScorePageSpec spec = SofaScorePageSpecs.footballLeagueCatalogue(
                "https://www.sofascore.com", "VNM");

        assertTrue(spec.endpointSpecs().get(0).matchesUrl(
                "https://www.sofascore.com/api/v1/config/top-unique-tournaments/EN/football"));
    }
}
