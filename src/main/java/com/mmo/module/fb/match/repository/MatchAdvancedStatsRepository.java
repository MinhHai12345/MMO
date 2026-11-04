package com.mmo.module.fb.match.repository;

import com.mmo.module.fb.match.entity.MatchAdvancedStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import com.mmo.module.fb.match.enums.MatchStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

@Repository
public interface MatchAdvancedStatsRepository extends JpaRepository<MatchAdvancedStats, Long> {

    Optional<MatchAdvancedStats> findByMatchIdAndTeamId(Long matchId, Long teamId);

    long countByMatchId(Long matchId);

    List<MatchAdvancedStats> findAllByMatchId(Long matchId);
    /**
     * Latest completed match-level team statistics, optionally restricted to one competition.
     * Every row is anchored to a finished, scored fixture and fetched with its context to avoid
     * lazy-loading/N+1 queries in the analytics layer.
     */
    @Query("""
            SELECT s FROM MatchAdvancedStats s
            JOIN FETCH s.match m
            JOIN FETCH m.season season
            JOIN FETCH season.league
            JOIN FETCH m.homeTeam
            JOIN FETCH m.awayTeam
            WHERE s.team.id = :teamId
              AND m.status = :finished
              AND m.homeScore IS NOT NULL
              AND m.awayScore IS NOT NULL
              AND (:leagueId IS NULL OR season.league.id = :leagueId)
            ORDER BY m.matchTime DESC, m.id DESC
            """)
    List<MatchAdvancedStats> findRecentFinishedTeamStats(
            @Param("teamId") Long teamId,
            @Param("leagueId") Long leagueId,
            @Param("finished") MatchStatus finished,
            Pageable pageable);
}
