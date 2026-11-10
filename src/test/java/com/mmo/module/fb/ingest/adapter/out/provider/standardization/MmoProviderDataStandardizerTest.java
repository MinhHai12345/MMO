package com.mmo.module.fb.ingest.adapter.out.provider.standardization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
    void standardizesCurrentClubsAcrossMajorFootballMarkets() {
        assertSame("PSV", "PSV Eindhoven", "PSV");
        assertSame("Feyenoord", "Feyenoord Rotterdam", "Feyenoord");
        assertSame("Benfica", "SL Benfica", "Sport Lisboa e Benfica");
        assertSame("Porto", "FC Porto", "Futebol Clube do Porto");
        assertSame("Beşiktaş", "Besiktas", "Beşiktaş JK");
        assertSame("İstanbul Başakşehir", "Istanbul Basaksehir", "İstanbul Başakşehir FK");
        assertSame("Union Saint-Gilloise", "Union SG", "Royale Union Saint-Gilloise");
        assertSame("Rangers", "Rangers FC", "Rangers");
        assertSame("Red Bull Salzburg", "FC Red Bull Salzburg", "Red Bull Salzburg");
        assertSame("Panathinaikos", "Panathinaikos FC", "Panathinaikos");
        assertSame("Red Star Belgrade", "FK Crvena Zvezda", "Crvena Zvezda");
        assertSame("Inter Miami", "Inter Miami CF", "Inter Miami");
        assertSame("Los Angeles FC", "LAFC", "Los Angeles FC");
        assertSame("Flamengo", "Clube de Regatas do Flamengo", "Flamengo");
        assertSame("River Plate", "Club Atlético River Plate", "River Plate");
        assertSame("Al Hilal", "Al-Hilal SFC", "Al Hilal");
    }

    @Test
    void keepsSimilarButDistinctClubAndNationalTeamNamesSeparate() {
        assertNotEquals(MmoProviderDataStandardizer.teamNameMatchKey("Marseille"),
                MmoProviderDataStandardizer.teamNameMatchKey("Morocco"));
        assertNotEquals(MmoProviderDataStandardizer.teamNameMatchKey("Celta Vigo"),
                MmoProviderDataStandardizer.teamNameMatchKey("Celtic"));
        assertNotEquals(MmoProviderDataStandardizer.teamNameMatchKey("Auxerre"),
                MmoProviderDataStandardizer.teamNameMatchKey("Ajax"));
        assertNotEquals(MmoProviderDataStandardizer.teamNameMatchKey("Monza"),
                MmoProviderDataStandardizer.teamNameMatchKey("Montpellier"));
        assertNotEquals(MmoProviderDataStandardizer.teamNameMatchKey("Parma"),
                MmoProviderDataStandardizer.teamNameMatchKey("Paraguay"));
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
