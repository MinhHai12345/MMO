package com.mmo.module.fb.ingest.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO;

import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SeasonsResponseDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.TournamentsResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SofaScoreLeagueMapperTest {

    private final SofaScoreLeagueMapper mapper = new SofaScoreLeagueMapper();

    @Test
    void mapsTournamentAndUsesNewestSeasonWhenCurrentFlagIsMissing() {
        SofaScoreResponseDTO.UniqueTournamentDTO tournament = tournament(17L, "Premier League");
        SofaScoreResponseDTO.SeasonDTO newest = season(900L, "2026/2027", "2026", false);
        SofaScoreResponseDTO.SeasonDTO previous = season(899L, "2025/2026", "2025", false);

        ProviderLeague mapped = mapper.toProviderLeague(tournament, List.of(newest, previous));

        assertNotNull(mapped);
        assertEquals("17", mapped.externalId());
        assertEquals("Premier League", mapped.name());
        assertEquals(2, mapped.seasons().size());
        assertEquals("2026", mapped.seasons().get(0).year());
        assertTrue(mapped.seasons().get(0).current());
        assertFalse(mapped.seasons().get(1).current());
    }

    @Test
    void preservesExplicitCurrentSeasonFlag() {
        SofaScoreResponseDTO.UniqueTournamentDTO tournament = tournament(17L, "Premier League");
        SofaScoreResponseDTO.SeasonDTO older = season(899L, "2025/2026", "2025", false);
        SofaScoreResponseDTO.SeasonDTO current = season(900L, "2026/2027", "2026", true);

        ProviderLeague mapped = mapper.toProviderLeague(tournament, List.of(older, current));

        assertFalse(mapped.seasons().get(0).current());
        assertTrue(mapped.seasons().get(1).current());
    }

    @Test
    void rejectsTournamentWithoutProviderIdentity() {
        assertNull(mapper.toProviderLeague(null, List.of()));
        assertNull(mapper.toProviderLeague(tournament(null, "Premier League"), List.of()));
    }

    private SofaScoreResponseDTO.UniqueTournamentDTO tournament(Long id, String name) {
        return new SofaScoreResponseDTO.UniqueTournamentDTO(id, name, null, null, null, null, null);
    }

    private SofaScoreResponseDTO.SeasonDTO season(Long id, String name, String year, boolean current) {
        return new SofaScoreResponseDTO.SeasonDTO(id, name, year, current);
    }
}
