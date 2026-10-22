package com.mmo.module.fb.analytics.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataCoverageAuditServiceImplTest {

    private final DataCoverageAuditServiceImpl service = new DataCoverageAuditServiceImpl(null, null);

    @Test
    void auditsRepeatedPairingsForDomesticDoubleRoundRobinSeason() {
        assertTrue(service.shouldAuditRepeatedPairings("Premier League", 380, 20));
        assertTrue(service.shouldAuditRepeatedPairings("Bundesliga", 306, 18));
    }

    @Test
    void skipsPairingWarningsForChampionshipSeasonContainingPlayoffs() {
        assertFalse(service.shouldAuditRepeatedPairings("Championship", 557, 24));
        assertFalse(service.shouldAuditRepeatedPairings("Championship", 559, 24));
    }

    @Test
    void auditsChampionshipPairingsWhenOnlyRegularSeasonFixturesAreStored() {
        assertTrue(service.shouldAuditRepeatedPairings("Championship", 552, 24));
    }

    @Test
    void skipsPairingWarningsForKnockoutCompetitions() {
        assertFalse(service.shouldAuditRepeatedPairings("UEFA Champions League", 189, 36));
    }

    @Test
    void skipsPairingAuditWhenTeamCountIsInsufficient() {
        assertFalse(service.shouldAuditRepeatedPairings("Premier League", 1, 1));
    }
}
