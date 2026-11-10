package com.mmo.module.fb.ingest.adapter.out.provider.standardization;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MmoProviderDataStandardizerTest {

    @Test
    void standardizesFootballDataUkTeamNameVariants() {
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("FC Koln", "FC Köln"),
                Map.entry("St Pauli", "St. Pauli"),
                Map.entry("Fortuna Dusseldorf", "Fortuna Düsseldorf"),
                Map.entry("Greuther Furth", "Greuther Fürth"),
                Map.entry("TSG 1899 Hoffenheim", "Hoffenheim"),
                Map.entry("Bolton Wanderers FC", "Bolton Wanderers"),
                Map.entry("Bolton", "Bolton Wanderers"),
                Map.entry("Lincoln City FC", "Lincoln City"),
                Map.entry("Lincoln", "Lincoln City"),
                Map.entry("Millwall FC", "Millwall"),
                Map.entry("Norwich City FC", "Norwich City"),
                Map.entry("Portsmouth FC", "Portsmouth"),
                Map.entry("Nimes", "Nîmes")
        );

        aliases.forEach((source, expected) ->
                assertEquals(expected, MmoProviderDataStandardizer.standardTeamName(source), source));
    }

    @Test
    void resolvesProviderAliasesToTheSameConservativeIdentityKey() {
        assertEquals(
                MmoProviderDataStandardizer.teamNameMatchKey("Bolton Wanderers"),
                MmoProviderDataStandardizer.teamNameMatchKey("Bolton Wanderers FC")
        );
        assertEquals(
                MmoProviderDataStandardizer.teamNameMatchKey("Lincoln City"),
                MmoProviderDataStandardizer.teamNameMatchKey("Lincoln City FC")
        );
        assertEquals(
                MmoProviderDataStandardizer.teamNameMatchKey("FC Köln"),
                MmoProviderDataStandardizer.teamNameMatchKey("FC Koln")
        );
        org.junit.jupiter.api.Assertions.assertNotEquals(
                MmoProviderDataStandardizer.teamNameMatchKey("Ajaccio"),
                MmoProviderDataStandardizer.teamNameMatchKey("Ajaccio GFCO")
        );
    }

    @Test
    void preservesNationalTeamsAndDistinctAjaccioClubs() {
        assertEquals("Algeria", MmoProviderDataStandardizer.standardTeamName("Algeria"));
        assertEquals("Cape Verde Islands", MmoProviderDataStandardizer.standardTeamName("Cape Verde Islands"));
        assertEquals("Congo DR", MmoProviderDataStandardizer.standardTeamName("Congo DR"));
        assertEquals("Ajaccio", MmoProviderDataStandardizer.standardTeamName("Ajaccio"));
        assertEquals("Ajaccio GFCO", MmoProviderDataStandardizer.standardTeamName("Ajaccio GFCO"));
    }

    @Test
    void accentAndPunctuationVariantsShareTheSameMatchKey() {
        assertEquals(
                MmoProviderDataStandardizer.teamNameMatchKey("FC Köln"),
                MmoProviderDataStandardizer.teamNameMatchKey("FC Koln")
        );
        assertEquals(
                MmoProviderDataStandardizer.teamNameMatchKey("St. Pauli"),
                MmoProviderDataStandardizer.teamNameMatchKey("St Pauli")
        );
    }
}
