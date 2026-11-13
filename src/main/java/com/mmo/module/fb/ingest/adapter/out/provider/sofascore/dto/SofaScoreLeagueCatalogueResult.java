package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto;

import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.SeasonDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.UniqueTournamentDTO;

import java.util.List;
import java.util.Map;

/** Tournament catalogue and the season payloads keyed by their source tournament IDs. */
public record SofaScoreLeagueCatalogueResult(
        List<UniqueTournamentDTO> tournaments,
        Map<Long, List<SeasonDTO>> seasonsByTournamentId) {

    public SofaScoreLeagueCatalogueResult {
        tournaments = tournaments == null ? List.of() : List.copyOf(tournaments);
        seasonsByTournamentId = seasonsByTournamentId == null ? Map.of()
                : seasonsByTournamentId.entrySet().stream().collect(
                        java.util.stream.Collectors.toUnmodifiableMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue() == null ? List.of() : List.copyOf(entry.getValue())));
    }
}
