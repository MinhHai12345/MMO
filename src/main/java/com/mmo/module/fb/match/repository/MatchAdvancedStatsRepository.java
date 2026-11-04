package com.mmo.module.fb.match.repository;

import com.mmo.module.fb.match.entity.MatchAdvancedStats;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchAdvancedStatsRepository extends JpaRepository<MatchAdvancedStats, Long> {

    @EntityGraph(attributePaths = {"match", "team"})
    Optional<MatchAdvancedStats> findByMatchIdAndTeamId(Long matchId, Long teamId);

    long countByMatchId(Long matchId);

    @EntityGraph(attributePaths = {"team"})
    List<MatchAdvancedStats> findAllByMatchId(Long matchId);

    boolean existsByMatchIdAndTeamId(Long matchId, Long teamId);
}
