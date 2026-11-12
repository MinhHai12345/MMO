package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.MatchesResponseDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.OddsResponseDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SofaScorePageSpecsTest {

    @Test
    void dailyTournamentPageDeclaresRequiredEventsAndOptionalOddsTogether() {
        SofaScorePageSpec spec = SofaScorePageSpecs.dailyEventsWithOdds(
                "https://www.sofascore.com/", 17L, "2026-11-12");

        assertEquals("https://www.sofascore.com/football/unique-tournament/17", spec.loadPageUrl());
        assertEquals(2, spec.endpointSpecs().size());
        assertTrue(spec.singleRequiredEndpoint().endpoint()
                .endsWith("/unique-tournament/17/scheduled-events/2026-11-12"));

        SofaScoreEndpointSpec odds = spec.endpoint(SofaScorePageSpecs.dailyOddsEndpoint("2026-11-12"));
        assertFalse(odds.required());
        assertEquals(OddsResponseDTO.class, odds.responseType());
        assertEquals(MatchesResponseDTO.class, spec.singleRequiredEndpoint().responseType());
    }

    @Test
    void footballCatalogueDoesNotWaitForOddsWhenOnlyLeaguesAreRequested() {
        SofaScorePageSpec spec = SofaScorePageSpecs.footballCatalogue(
                "https://www.sofascore.com", "vn", "2026-11-12");

        assertEquals(1, spec.endpointSpecs().size());
        assertEquals("config/default-unique-tournaments/VN/football",
                spec.singleRequiredEndpoint().endpoint());
    }

    @Test
    void lineupsPageTreatsLineupsAsOptionalButRequiresTheMatchResponse() {
        SofaScorePageSpec spec = SofaScorePageSpecs.lineups("https://www.sofascore", 123L);

        assertEquals("event/123", spec.singleRequiredEndpoint().endpoint());
        assertFalse(spec.endpoint(SofaScorePageSpecs.lineupsEndpoint(123L)).required());
    }

    @Test
    void pageSpecRequiresAtLeastOneRequiredEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> SofaScorePageSpec.of(
                "https://www.sofascore.com/football",
                SofaScoreEndpointSpec.optional("/api/v1/odds/2026-11-12", OddsResponseDTO.class)));
    }

    @Test
    void pageSpecRejectsDuplicateNormalizedEndpointMatchers() {
        assertThrows(IllegalArgumentException.class, () -> SofaScorePageSpec.of(
                "https://www.sofascore.com/football",
                SofaScoreEndpointSpec.required("api/v1/event/10", MatchesResponseDTO.class),
                SofaScoreEndpointSpec.optional("/api/v1/event/10/", OddsResponseDTO.class)));
    }
}
