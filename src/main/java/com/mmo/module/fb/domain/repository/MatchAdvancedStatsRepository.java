package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.MatchAdvancedStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MatchAdvancedStatsRepository extends JpaRepository<MatchAdvancedStats, Long> {

    Optional<MatchAdvancedStats> findByMatchIdAndTeamId(Long matchId, Long teamId);

    long countByMatchId(Long matchId);

    java.util.List<MatchAdvancedStats> findAllByMatchId(Long matchId);
}
