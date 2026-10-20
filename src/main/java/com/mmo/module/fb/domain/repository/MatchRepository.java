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
    
    @Query("""
            SELECT m FROM Match m
            JOIN FETCH m.season season
            JOIN FETCH season.league
            JOIN FETCH m.homeTeam
            JOIN FETCH m.awayTeam
            WHERE m.matchTime BETWEEN :startTime AND :endTime
              AND m.status = :status
            """)
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
              AND m.season.league.id = :leagueId
              AND m.status = :status
              AND m.homeScore IS NOT NULL
              AND m.awayScore IS NOT NULL
            ORDER BY m.matchTime DESC, m.id DESC
            """)
    List<Match> findRecentFinishedMatchesForTeamInLeague(@Param("teamId") Long teamId,
                                                         @Param("leagueId") Long leagueId,
                                                         @Param("status") MatchStatus status,
                                                         Pageable pageable);

    @Query("""
            SELECT m FROM Match m
            WHERE m.season.league.id = :leagueId
              AND m.status = :status
              AND m.homeScore IS NOT NULL
              AND m.awayScore IS NOT NULL
            ORDER BY m.matchTime DESC, m.id DESC
            """)
    List<Match> findRecentFinishedMatchesInLeague(@Param("leagueId") Long leagueId,
                                                  @Param("status") MatchStatus status,
                                                  Pageable pageable);

    @Query("""
            SELECT m FROM Match m
            WHERE (m.homeTeam.id = :teamId OR m.awayTeam.id = :teamId)
              AND m.season.id = :seasonId
              AND m.status = :status
              AND m.homeScore IS NOT NULL
              AND m.awayScore IS NOT NULL
            ORDER BY m.matchTime DESC, m.id DESC
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

    @Query("""
            SELECT DISTINCT m FROM Match m
            JOIN m.externalIds externalId
            WHERE m.status = :status
              AND externalId.provider = :provider
              AND (m.statisticsLastAttemptAt IS NULL OR m.statisticsLastAttemptAt < :retryBefore)
              AND (
                    NOT EXISTS (
                        SELECT homeStats.id FROM MatchAdvancedStats homeStats
                        WHERE homeStats.match = m AND homeStats.team = m.homeTeam
                    )
                    OR NOT EXISTS (
                        SELECT awayStats.id FROM MatchAdvancedStats awayStats
                        WHERE awayStats.match = m AND awayStats.team = m.awayTeam
                    )
              )
            ORDER BY m.matchTime DESC
            """)
    List<Match> findFinishedMatchesMissingAdvancedStats(@Param("status") MatchStatus status,
                                                       @Param("provider") Provider provider,
                                                       @Param("retryBefore") Instant retryBefore,
                                                       Pageable pageable);
    
    @Query("SELECT DISTINCT m.externalId FROM Match m WHERE m.season.league = :league")
    List<String> findDistinctExternalIdsByLeague(@Param("league") com.mmo.module.fb.domain.entity.League league);
    
    @Query("SELECT m FROM Match m WHERE m.status = 'FINISHED' AND (m.homeXG IS NULL OR m.awayXG IS NULL)")
    List<Match> findByStatusAndHomeXGIsNullAndAwayXGIsNull();
    
    @Query("SELECT m FROM Match m WHERE m.status = :status AND m.homeXG IS NULL AND m.awayXG IS NULL ORDER BY m.matchTime ASC")
    List<Match> findTop30ByStatusAndHomeXGIsNullAndAwayXGIsNullOrderByMatchTimeAsc(@Param("status") MatchStatus status);

    /**
     * Aggregated data coverage by league and season. Uses a projection to avoid loading
     * every Match entity just to build a readiness report.
     */
    @Query("""
            SELECT s.id AS seasonId,
                   l.name AS leagueName,
                   s.year AS seasonYear,
                   COUNT(m.id) AS totalMatches,
                   SUM(CASE WHEN m.status = :finished THEN 1 ELSE 0 END) AS finishedMatches,
                   SUM(CASE WHEN m.status = :finished
                                 AND m.homeScore IS NOT NULL
                                 AND m.awayScore IS NOT NULL
                            THEN 1 ELSE 0 END) AS finishedWithScores,
                   SUM(CASE WHEN m.status = :finished
                                 AND m.homeXG IS NOT NULL
                                 AND m.awayXG IS NOT NULL
                            THEN 1 ELSE 0 END) AS finishedWithExpectedGoals,
                   SUM(CASE WHEN m.status <> :finished
                                 AND m.matchTime > CURRENT_TIMESTAMP
                            THEN 1 ELSE 0 END) AS upcomingMatches
            FROM Match m
            JOIN m.season s
            JOIN s.league l
            GROUP BY s.id, l.name, s.year
            ORDER BY l.name ASC, s.year DESC
            """)
    List<SeasonDataReadinessProjection> findSeasonDataReadiness(
            @Param("finished") MatchStatus finished);

    interface SeasonDataReadinessProjection {
        Long getSeasonId();
        String getLeagueName();
        String getSeasonYear();
        Long getTotalMatches();
        Long getFinishedMatches();
        Long getFinishedWithScores();
        Long getFinishedWithExpectedGoals();
        Long getUpcomingMatches();
    }
}
