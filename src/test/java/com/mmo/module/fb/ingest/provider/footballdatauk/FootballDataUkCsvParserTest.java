package com.mmo.module.fb.ingest.provider.footballdatauk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FootballDataUkCsvParserTest {
    private final FootballDataUkCsvParser parser = new FootballDataUkCsvParser();

    @Test
    void parsesQuotedCommasAndEscapedQuotes() {
        var rows = parser.parse("HomeTeam,AwayTeam,Referee,HS\n"
                + "\"A, FC\",B,\"Referee \"\"X\"\"\",12\n");
        assertEquals(1, rows.size());
        assertEquals("A, FC", rows.get(0).get("HOMETEAM"));
        assertEquals("Referee \"X\"", rows.get(0).get("REFEREE"));
        assertEquals("12", rows.get(0).get("HS"));
    }

    @Test
    void skipsBlankLinesAndNormalizesHeaders() {
        var rows = parser.parse("\uFEFF hometeam , awayteam , HS \nA,B,9\n\n");
        assertEquals(1, rows.size());
        assertEquals("9", rows.get(0).get("HS"));
    }
}
