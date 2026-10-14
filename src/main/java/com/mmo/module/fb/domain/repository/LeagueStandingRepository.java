package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.LeagueStanding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeagueStandingRepository extends JpaRepository<LeagueStanding, Long> {
    Optional<LeagueStanding> findBySeasonIdAndTeamId(Long seasonId, Long teamId);
}
