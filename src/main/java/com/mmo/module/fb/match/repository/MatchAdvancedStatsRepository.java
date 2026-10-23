package com.mmo.module.fb.match.repository;

import com.mmo.module.fb.match.entity.MatchAdvancedStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchAdvancedStatsRepository extends JpaRepository<MatchAdvancedStats, Long> {

    Optional<MatchAdvancedStats> findByMatchIdAndTeamId(Long matchId, Long teamId);

    long countByMatchId(Long matchId);

    List<MatchAdvancedStats> findAllByMatchId(Long matchId);
}
