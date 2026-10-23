package com.mmo.module.fb.league.repository;

import com.mmo.module.fb.league.entity.LeagueStanding;
import com.mmo.module.fb.league.enums.StandingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeagueStandingRepository extends JpaRepository<LeagueStanding, Long> {

    Optional<LeagueStanding> findBySeasonIdAndTeamId(Long seasonId, Long teamId);

    Optional<LeagueStanding> findBySeasonIdAndTeamIdAndStandingType(
            Long seasonId, Long teamId, StandingType standingType);
}
