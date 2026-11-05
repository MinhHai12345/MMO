package com.mmo.module.fb.ingest.provider.footballdatauk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FootballDataUkCsvParserTest {
    private final FootballDataUkCsvParser parser = new FootballDataUkCsvParser();

    @Test
    void parsesQuotedCommasAndEscapedQuotes() {
        var rows = parser.parse("Date,HomeTeam,AwayTeam,Referee,HS\n"
                + "\"01/08/24\",\"A, FC\",B,\"Referee \"\"X\"\"\",12\n");
        assertEquals(1, rows.size());
        assertEquals("A, FC", rows.get(0).get("HOMETEAM"));
        assertEquals("Referee \"X\"", rows.get(0).get("REFEREE"));
        assertEquals("12", rows.get(0).get("HS"));
    }

    @Test
    void skipsBlankLinesAndNormalizesHeaders() {
        var rows = parser.parse("\uFEFF Date, hometeam , awayteam , HS \n01/08/24,A,B,9\n\n");
        assertEquals(1, rows.size());
        assertEquals("9", rows.get(0).get("HS"));
    }

    @Test
    void rejectsHtmlOrInvalidHeaders() {
        var exception = assertThrows(FootballDataUkCsvException.class,
                () -> parser.parse("<html>Access denied</html>"));
        assertEquals(FootballDataUkCsvException.Reason.INVALID_CSV, exception.getReason());
    }

    @Test
    void rejectsUnterminatedQuotedField() {
        var exception = assertThrows(FootballDataUkCsvException.class,
                () -> parser.parse("Date,HomeTeam,AwayTeam\n01/08/24,\"A,B\n"));
        assertEquals(FootballDataUkCsvException.Reason.INVALID_CSV, exception.getReason());
    }

    @Test
    void headerOnlyCsvIsAValidEmptySeason() {
        assertTrue(parser.parse("Date,HomeTeam,AwayTeam\n").isEmpty());
    }
}
