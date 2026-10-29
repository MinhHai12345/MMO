package com.mmo.module.fb.ingest.provider.understats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderstatDtos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnderstatDtosTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesDirectLeagueApiResponse() throws Exception {
        String json = """
                {
                  "dates": [{
                    "id": "31180",
                    "isResult": true,
                    "h": {"id": "88", "title": "Arsenal", "short_title": "ARS"},
                    "a": {"id": "77", "title": "Coventry", "short_title": "COV"},
                    "goals": {"h": 3, "a": 0},
                    "xG": {"h": "1.85424", "a": "0.558336"},
                    "datetime": "2026-10-20 19:00:00",
                    "forecast": {"w": "0.71", "d": "0.18", "l": "0.11"}
                  }],
                  "teams": {
                    "88": {
                      "id": "88",
                      "title": "Arsenal",
                      "history": [{
                        "id": "31180",
                        "h_a": "h",
                        "xG": "1.85424",
                        "xGA": "0.558336",
                        "npxG": "1.85424",
                        "npxGA": "0.558336",
                        "ppda": {"att": 180, "def": 12},
                        "ppda_allowed": {"att": 220, "def": 10},
                        "deep": 8,
                        "deep_allowed": 3,
                        "scored": 3,
                        "missed": 0,
                        "xpts": "2.71",
                        "result": "w",
                        "date": "2026-10-20 19:00:00",
                        "wins": 1,
                        "draws": 0,
                        "loses": 0,
                        "pts": 3,
                        "npxGD": "1.295904"
                      }]
                    }
                  },
                  "players": []
                }
                """;

        UnderstatDtos.LeagueData data = objectMapper.readValue(json, UnderstatDtos.LeagueData.class);

        assertNotNull(data.dates());
        assertEquals(1, data.dates().size());
        assertEquals("31180", data.dates().get(0).id());
        assertEquals("Arsenal", data.dates().get(0).h().title());
        assertEquals(0, data.dates().get(0).goals().a());

        UnderstatDtos.LeagueTeam arsenal = data.teams().get("88");
        assertNotNull(arsenal);
        assertEquals(1, arsenal.history().size());
        assertEquals("w", arsenal.history().get(0).result());
        assertEquals(180, arsenal.history().get(0).ppda().att());
        assertTrue(arsenal.history().get(0).xG().doubleValue() > 1.8);
    }
}
