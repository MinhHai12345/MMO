package com.mmo.module.fb.ingest.provider.footballdatauk;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FootballDataUkMatchDataProviderTest {

    @Test
    void buildsProviderSeasonUrlAndParsesCsvRows() {
        FootballDataUkCsvClient client = mock(FootballDataUkCsvClient.class);
        when(client.getCsv("https://football-data.co.uk/mmz4281/2526/E0.csv"))
                .thenReturn("Date,HomeTeam,AwayTeam,FTHG,FTAG\n01/08/25,Arsenal,Chelsea,2,1");

        FootballDataUkMatchDataProvider provider =
                new FootballDataUkMatchDataProvider(client, new FootballDataUkCsvParser());

        List<Map<String, String>> rows = provider.fetchSeason(
                "https://www.football-data.co.uk/", "2025", "e0");

        assertEquals(1, rows.size());
        assertEquals("Arsenal", rows.get(0).get("HOMETEAM"));
        assertEquals("Chelsea", rows.get(0).get("AWAYTEAM"));
        verify(client).getCsv("https://football-data.co.uk/mmz4281/2526/E0.csv");
    }
}
