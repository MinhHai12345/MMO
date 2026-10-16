package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.enums.Provider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    @Query("""
            SELECT m FROM Match m
            WHERE m.season.id = :seasonId
              AND m.homeTeam.id = :homeTeamId
              AND m.awayTeam.id = :awayTeamId
              AND m.matchTime BETWEEN :startTime AND :endTime
            """)
    List<Match> findFixtureCandidates(@Param("seasonId") Long seasonId,
                                      @Param("homeTeamId") Long homeTeamId,
                                      @Param("awayTeamId") Long awayTeamId,
                                      @Param("startTime") Instant startTime,
                                      @Param("endTime") Instant endTime);

    List<Match> findByStatusAndMatchTimeBetweenOrderByMatchTimeAsc(
            MatchStatus status, Instant startTime, Instant endTime);

    @Query("""
            SELECT DISTINCT m FROM Match m
            JOIN m.externalIds externalId
            WHERE m.status = :status
              AND m.matchTime BETWEEN :startTime AND :endTime
              AND externalId.provider = :provider
            ORDER BY m.lastOddsSyncedAt ASC, m.matchTime ASC
            """)
    List<Match> findUpcomingWithProviderMapping(@Param("status") MatchStatus status,
                                                @Param("startTime") Instant startTime,
                                                @Param("endTime") Instant endTime,
                                                @Param("provider") Provider provider,
                                                Pageable pageable);
    
    @Query("SELECT m FROM Match m WHERE m.matchTime BETWEEN :startTime AND :endTime AND m.status = :status")
    List<Match> findByMatchTimeBetweenAndStatus(@Param("startTime") Instant startTime, 
                                                @Param("endTime") Instant endTime, 
                                                @Param("status") MatchStatus status);
    
    @Query("SELECT m FROM Match m WHERE (m.homeTeam = :team OR m.awayTeam = :team) AND m.status = :status ORDER BY m.matchTime DESC")
    List<Match> findTop10ByHomeTeamOrAwayTeamAndStatusOrderByMatchTimeDesc(@Param("team") Team team, 
                                                                            @Param("status") MatchStatus status,
                                                                            Pageable pageable);

    @Query("""
            SELECT m FROM Match m
            WHERE (m.homeTeam.id = :teamId OR m.awayTeam.id = :teamId)
              AND m.status = :status
              AND m.homeScore IS NOT NULL
              AND m.awayScore IS NOT NULL
            ORDER BY m.matchTime DESC
            """)
    List<Match> findRecentFinishedMatchesForTeam(@Param("teamId") Long teamId,
                                                 @Param("status") MatchStatus status,
                                                 Pageable pageable);

    @Query("""
            SELECT m FROM Match m
            WHERE (m.homeTeam.id = :teamId OR m.awayTeam.id = :teamId)
              AND m.season.id = :seasonId
              AND m.status = :status
              AND m.homeScore IS NOT NULL
              AND m.awayScore IS NOT NULL
            ORDER BY m.matchTime DESC
            """)
    List<Match> findRecentFinishedMatchesForTeamInSeason(@Param("teamId") Long teamId,
                                                         @Param("seasonId") Long seasonId,
                                                         @Param("status") MatchStatus status,
                                                         Pageable pageable);
    
    @Query("SELECT m FROM Match m WHERE ((m.homeTeam = :homeTeam AND m.awayTeam = :awayTeam) OR (m.homeTeam = :homeTeam2 AND m.awayTeam = :awayTeam2)) AND m.status = :status")
    List<Match> findByHomeTeamAndAwayTeamOrHomeTeamAndAwayTeamAndStatus(@Param("homeTeam") Team homeTeam,
                                                                          @Param("awayTeam") Team awayTeam,
                                                                          @Param("homeTeam2") Team homeTeam2,
                                                                          @Param("awayTeam2") Team awayTeam2,
                                                                          @Param("status") MatchStatus status);
    
    List<Match> findByStatus(MatchStatus status);
    
    @Query("SELECT DISTINCT m.externalId FROM Match m WHERE m.season.league = :league")
    List<String> findDistinctExternalIdsByLeague(@Param("league") com.mmo.module.fb.domain.entity.League league);
    
    @Query("SELECT m FROM Match m WHERE m.status = 'FINISHED' AND (m.homeXG IS NULL OR m.awayXG IS NULL)")
    List<Match> findByStatusAndHomeXGIsNullAndAwayXGIsNull();
    
    @Query("SELECT m FROM Match m WHERE m.status = :status AND m.homeXG IS NULL AND m.awayXG IS NULL ORDER BY m.matchTime ASC")
    List<Match> findTop30ByStatusAndHomeXGIsNullAndAwayXGIsNullOrderByMatchTimeAsc(@Param("status") MatchStatus status);
}
