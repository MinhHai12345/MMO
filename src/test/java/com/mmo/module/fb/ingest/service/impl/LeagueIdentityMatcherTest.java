package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.league.entity.League;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeagueIdentityMatcherTest {

    @Test
    void matchesProviderAliasAndAccentInsensitiveName() {
        League canonical = league("LaLiga", "Spain");

        assertTrue(LeagueIdentityMatcher.matches(canonical,
                new ProviderLeague("EPL-ES-1", "Primera División", "Spain", null, List.of())));
    }

    @Test
    void rejectsSameNameWhenKnownCountriesConflict() {
        League canonical = league("Premier League", "England");

        assertFalse(LeagueIdentityMatcher.matches(canonical,
                new ProviderLeague("123", "Premier League", "Scotland", null, List.of())));
    }

    @Test
    void allowsMissingCountryWhenNameIsUnambiguous() {
        League canonical = league("Premier League", "England");

        assertTrue(LeagueIdentityMatcher.matches(canonical,
                new ProviderLeague("EPL", "English Premier League", null, null, List.of())));
    }

    @Test
    void doesNotMatchUnrelatedLeagueNames() {
        assertFalse(LeagueIdentityMatcher.matches(league("Premier League", "England"),
                new ProviderLeague("999", "Championship", "England", null, List.of())));
    }

    private static League league(String name, String country) {
        League league = new League();
        league.setName(name);
        league.setCountry(country);
        return league;
    }
}
