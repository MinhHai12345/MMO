package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.domain.entity.LeagueStanding;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.repository.LeagueStandingRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.domain.repository.TeamRepository;
import com.mmo.module.fb.ingest.provider.model.ProviderStanding;
import com.mmo.module.fb.ingest.service.StandingsPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class StandingsPersistenceServiceImpl implements StandingsPersistenceService {

    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final LeagueStandingRepository standingRepository;

    @Transactional
    public void upsertStanding(Long seasonId, Long teamId, ProviderStanding row) {
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalArgumentException("Season not found: " + seasonId));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        LeagueStanding standing = standingRepository.findBySeasonIdAndTeamId(seasonId, teamId)
                .orElseGet(() -> LeagueStanding.builder()
                        .league(season.getLeague())
                        .season(season)
                        .team(team)
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
