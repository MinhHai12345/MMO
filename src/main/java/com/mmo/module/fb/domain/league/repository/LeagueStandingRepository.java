package com.mmo.module.fb.domain.league.repository;

import com.mmo.module.fb.domain.league.entity.LeagueStanding;
import com.mmo.module.fb.domain.league.enums.StandingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeagueStandingRepository extends JpaRepository<LeagueStanding, Long> {

    Optional<LeagueStanding> findBySeasonIdAndTeamId(Long seasonId, Long teamId);

    Optional<LeagueStanding> findBySeasonIdAndTeamIdAndStandingType(
            Long seasonId, Long teamId, StandingType standingType);

    Optional<LeagueStanding> findBySeasonIdAndTeamIdAndStandingTypeAndGroupKey(
            Long seasonId, Long teamId, StandingType standingType, String groupKey);

    List<LeagueStanding> findAllBySeasonId(Long seasonId);

    long countBySeasonId(Long seasonId);
}
