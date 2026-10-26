package com.mmo.module.fb.ingest.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.provider.model.ProviderStanding;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaStandingsData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class SofaScoreStandingsMapper {

    public List<ProviderStanding> toProviderStandings(List<SofaStandingsData.StandingGroupDTO> groups) {
        if (groups == null) return List.of();
        return groups.stream()
                .filter(Objects::nonNull)
                .filter(group -> group.getRows() != null)
                .flatMap(group -> group.getRows().stream()
                        .filter(Objects::nonNull)
                        .map(row -> new ProviderStanding(
                                row.getTeam() == null || row.getTeam().getId() == null
                                        ? null : row.getTeam().getId().toString(),
                                row.getPosition(), row.getPoints(), parseGoalDifference(row.getScoreDiffFormatted()),
                                null, group.getName(), row.getMatches(), row.getWins(), row.getDraws(),
                                row.getLosses(), row.getScoresFor(), row.getScoresAgainst())))
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
