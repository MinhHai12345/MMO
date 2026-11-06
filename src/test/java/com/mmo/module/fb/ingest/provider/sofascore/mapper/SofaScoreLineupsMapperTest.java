package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreLineupsResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SofaScoreLineupsMapperTest {

    private final SofaScoreLineupsMapper mapper = new SofaScoreLineupsMapper();

    @Test
    void mapsRawSofaScoreLineupsToProviderNeutralPlayerMetrics() {
        var player = new SofaScoreLineupsResponseDTO.PlayerDTO(
                123L, "Player One", "P1", "player-one", "Midfielder", "VN", 180, 72, 8);
        var lineupPlayer = new SofaScoreLineupsResponseDTO.LineupPlayerDTO(
                player, 8, "M", false, false,
                Map.of("minutesPlayed", 90, "goals", 1, "expectedGoals", 0.0,
                        "accuratePassesPercentage", 87.5, "keyPasses", 3));
        var source = new SofaScoreLineupsResponseDTO(
                new SofaScoreLineupsResponseDTO.LineupTeamDTO(List.of(lineupPlayer), "4-3-3"),
                new SofaScoreLineupsResponseDTO.LineupTeamDTO(List.of(), "4-4-2"));

        var result = mapper.toModel(source);

        assertEquals(1, result.home().size());
        assertEquals("123", result.home().get(0).externalPlayerId());
        assertEquals("Midfielder", result.home().get(0).position());
        assertEquals(90, result.home().get(0).statistics().minutesPlayed());
        assertEquals(1, result.home().get(0).statistics().goals());
        assertEquals(0, result.home().get(0).statistics().xg().signum());
        assertEquals(87.5, result.home().get(0).statistics().passAccuracy().doubleValue());
        assertEquals(3, result.home().get(0).statistics().keyPasses());
        assertTrue(result.away().isEmpty());
    }

    @Test
    void ignoresLineupPlayersWithoutAValidProviderIdentity() {
        var invalid = new SofaScoreLineupsResponseDTO.LineupPlayerDTO(
                null, 8, "M", false, false, Map.of("goals", 1));
        var result = mapper.toModel(new SofaScoreLineupsResponseDTO(
                new SofaScoreLineupsResponseDTO.LineupTeamDTO(List.of(invalid), "4-3-3"), null));
        assertTrue(result.isEmpty());
    }
}
