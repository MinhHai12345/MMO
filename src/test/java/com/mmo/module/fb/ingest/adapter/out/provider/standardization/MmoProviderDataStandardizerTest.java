package com.mmo.module.fb.ingest.adapter.out.provider.standardization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MmoProviderDataStandardizerTest {

    @Test
    void standardizesObservedFootballDataUkAliasesToOneCanonicalName() {
        assertSame("Athletic Club", "Ath Bilbao", "Athletic Club");
        assertSame("Atletico Madrid", "Ath Madrid", "Atletico Madrid");
        assertSame("Borussia Dortmund", "Dortmund", "Borussia Dortmund");
        assertSame("Eintracht Frankfurt", "Ein Frankfurt", "Eintracht Frankfurt");
        assertSame("Real Betis", "Betis", "Real Betis");
        assertSame("Bayer Leverkusen", "Leverkusen", "Bayer Leverkusen");
        assertSame("Borussia Mönchengladbach", "M'gladbach", "Borussia Mönchengladbach");
        assertSame("Mainz 05", "Mainz", "Mainz 05");
        assertSame("Paris Saint-Germain", "Paris SG", "Paris Saint-Germain");
        assertSame("AS Roma", "Roma", "AS Roma");
        assertSame("Real Sociedad", "Sociedad", "Real Sociedad");
        assertSame("Saint-Étienne", "St Etienne", "Saint-Étienne");
        assertSame("VfB Stuttgart", "Stuttgart", "VfB Stuttgart");
        assertSame("Real Valladolid", "Valladolid", "Real Valladolid");
        assertSame("Rayo Vallecano", "Vallecano", "Rayo Vallecano");
        assertSame("Werder Bremen", "SV Werder Bremen", "Werder Bremen");
        assertSame("West Bromwich Albion", "West Bromwich Albion FC", "West Bromwich Albion");
        assertSame("Wrexham", "Wrexham AFC", "Wrexham");
    }

    @Test
    void doesNotMergeDistinctAjaccioClubs() {
        assertEquals("Ajaccio", MmoProviderDataStandardizer.standardTeamName("Ajaccio"));
        assertEquals("Ajaccio GFCO", MmoProviderDataStandardizer.standardTeamName("Ajaccio GFCO"));
    }

    @Test
    void normalizesAccentAndPunctuationVariants() {
        assertEquals("Almería", MmoProviderDataStandardizer.standardTeamName("Almeria"));
        assertEquals("Málaga", MmoProviderDataStandardizer.standardTeamName("Malaga"));
        assertEquals("bodo glimt", MmoProviderDataStandardizer.teamNameMatchKey("FK Bodø/Glimt"));
    }

    private static void assertSame(String expected, String first, String second) {
        assertEquals(expected, MmoProviderDataStandardizer.standardTeamName(first));
        assertEquals(expected, MmoProviderDataStandardizer.standardTeamName(second));
    }
}
