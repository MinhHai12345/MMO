package com.mmo.module.fb.domain.league.repository;

import com.mmo.module.fb.domain.league.entity.LeagueStanding;
import com.mmo.module.fb.domain.league.enums.StandingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface LeagueStandingRepository extends JpaRepository<LeagueStanding, Long> {

    Optional<LeagueStanding> findBySeasonIdAndTeamId(Long seasonId, Long teamId);

    List<LeagueStanding> findAllBySeasonIdAndTeamIdIn(Long seasonId, java.util.Collection<Long> teamIds);

    Optional<LeagueStanding> findBySeasonIdAndTeamIdAndStandingType(
            Long seasonId, Long teamId, StandingType standingType);
}
