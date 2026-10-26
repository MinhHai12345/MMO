package com.mmo.module.fb.ingest.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO;

import com.mmo.module.fb.ingest.provider.model.ProviderStanding;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class SofaScoreStandingsMapper {

    public List<ProviderStanding> toProviderStandings(List<SofaScoreResponseDTO.StandingGroupDTO> groups) {
        if (groups == null) return List.of();
        return groups.stream()
                .filter(Objects::nonNull)
                .filter(group -> group.rows() != null)
                .flatMap(group -> group.rows().stream()
                        .filter(Objects::nonNull)
                        .map(row -> new ProviderStanding(
                                row.team() == null || row.team().id() == null
                                        ? null : row.team().id().toString(),
                                row.position(), row.points(), parseGoalDifference(row.scoreDiffFormatted()),
                                null, group.name(), row.matches(), row.wins(), row.draws(),
                                row.losses(), row.scoresFor(), row.scoresAgainst())))
                .filter(standing -> standing.teamExternalId() != null)
                .toList();
    }

    private Integer parseGoalDifference(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.valueOf(value.replace("+", ""));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
