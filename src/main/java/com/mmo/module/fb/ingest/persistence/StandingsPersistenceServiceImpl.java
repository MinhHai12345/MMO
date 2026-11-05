package com.mmo.module.fb.ingest.persistence;

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
    private final DataSourcePolicy dataSourcePolicy;

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
