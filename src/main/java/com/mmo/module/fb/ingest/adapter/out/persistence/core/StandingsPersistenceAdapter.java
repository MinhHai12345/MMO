package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.domain.league.entity.LeagueStanding;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.league.enums.StandingType;
import com.mmo.module.fb.domain.league.repository.LeagueStandingRepository;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.domain.team.repository.TeamRepository;
import com.mmo.module.fb.ingest.application.model.provider.ProviderStanding;
import com.mmo.module.fb.ingest.application.port.out.StandingsPersistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class StandingsPersistenceAdapter implements StandingsPersistencePort {

    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final LeagueStandingRepository standingRepository;
    private final DataSourcePolicy dataSourcePolicy;

    @Override
    @Transactional
    public int upsertStandings(Provider provider, Long seasonId, java.util.List<StandingsPersistencePort.StandingRow> rows) {
        if (seasonId == null || rows == null || rows.isEmpty()) return 0;
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalArgumentException("Season not found: " + seasonId));
        if (!dataSourcePolicy.isPrimary(provider, Dataset.STANDINGS, season)) return 0;

        java.util.Set<Long> teamIds = rows.stream()
                .filter(java.util.Objects::nonNull)
                .filter(row -> row.teamId() != null && row.standing() != null)
                .map(StandingsPersistencePort.StandingRow::teamId)
                .collect(java.util.stream.Collectors.toSet());
        if (teamIds.isEmpty()) return 0;

        java.util.Map<Long, Team> teams = teamRepository.findAllById(teamIds).stream()
                .collect(java.util.stream.Collectors.toMap(Team::getId, team -> team));
        java.util.Map<String, LeagueStanding> existing = standingRepository
                .findAllBySeasonIdAndTeamIdIn(seasonId, teamIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        standing -> key(standing.getTeam().getId(), standing.getStandingType()),
                        standing -> standing, (left, right) -> left));
        java.util.Map<String, LeagueStanding> pending = new java.util.LinkedHashMap<>();
        Instant syncedAt = Instant.now();

        for (StandingsPersistencePort.StandingRow item : rows) {
            if (item == null || item.teamId() == null || item.standing() == null) continue;
            Team team = teams.get(item.teamId());
            if (team == null) continue;
            ProviderStanding row = item.standing();
            StandingType type = row.standingType() == null ? StandingType.TOTAL : row.standingType();
            String key = key(item.teamId(), type);
            LeagueStanding standing = pending.get(key);
            if (standing == null) {
                standing = existing.get(key);
                if (standing == null) {
                    standing = LeagueStanding.builder()
                            .season(season).team(team).standingType(type).build();
                }
            }
            standing.setPosition(row.position());
            standing.setPoints(row.points());
            standing.setGoalDifference(row.goalDifference());
            standing.setForm(row.form());
            standing.setGroupName(row.groupName());
            standing.setPlayed(row.played());
            standing.setWon(row.won());
            standing.setDrawn(row.drawn());
            standing.setLost(row.lost());
            standing.setGoalsFor(row.goalsFor());
            standing.setGoalsAgainst(row.goalsAgainst());
            standing.setLastSyncedAt(syncedAt);
            pending.put(key, standing);
        }
        if (pending.isEmpty()) return 0;
        standingRepository.saveAll(pending.values());
        return pending.size();
    }

    private static String key(Long teamId, StandingType type) {
        return teamId + ":" + type.name();
    }


    @Transactional
    public void upsertStanding(Provider provider, Long seasonId, Long teamId, ProviderStanding row) {
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalArgumentException("Season not found: " + seasonId));
        if (!dataSourcePolicy.isPrimary(provider, Dataset.STANDINGS, season)) return;
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        StandingType type = row.standingType() == null ? StandingType.TOTAL : row.standingType();
        LeagueStanding standing = standingRepository
                .findBySeasonIdAndTeamIdAndStandingType(seasonId, teamId, type)
                .orElseGet(() -> LeagueStanding.builder()
                        .season(season)
                        .team(team)
                        .standingType(type)
                        .build());

        standing.setPosition(row.position());
        standing.setPoints(row.points());
        standing.setGoalDifference(row.goalDifference());
        standing.setForm(row.form());
        standing.setGroupName(row.groupName());
        standing.setPlayed(row.played());
        standing.setWon(row.won());
        standing.setDrawn(row.drawn());
        standing.setLost(row.lost());
        standing.setGoalsFor(row.goalsFor());
        standing.setGoalsAgainst(row.goalsAgainst());
        standing.setLastSyncedAt(Instant.now());
        standingRepository.save(standing);
    }
}
