package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    
    @Query("SELECT m FROM Match m WHERE m.matchTime BETWEEN :startTime AND :endTime AND m.status = :status")
    List<Match> findByMatchTimeBetweenAndStatus(@Param("startTime") Instant startTime, 
                                                @Param("endTime") Instant endTime, 
                                                @Param("status") String status);
    
    @Query("SELECT m FROM Match m WHERE (m.homeTeam = :team OR m.awayTeam = :team) AND m.status = :status ORDER BY m.matchTime DESC")
    List<Match> findTop10ByHomeTeamOrAwayTeamAndStatusOrderByMatchTimeDesc(@Param("team") Team team, 
                                                                            @Param("status") String status);
    
    @Query("SELECT m FROM Match m WHERE (m.homeTeam = :homeTeam AND m.awayTeam = :awayTeam) OR (m.homeTeam = :awayTeam AND m.awayTeam = :homeTeam) AND m.status = :status")
    List<Match> findByHomeTeamAndAwayTeamOrHomeTeamAndAwayTeamAndStatus(@Param("homeTeam") Team homeTeam,
                                                                          @Param("awayTeam") Team awayTeam,
                                                                          @Param("homeTeam2") Team homeTeam2,
                                                                          @Param("awayTeam2") Team awayTeam2,
                                                                          @Param("status") String status);
    
    List<Match> findByStatus(String status);
    
    @Query("SELECT DISTINCT m.externalId FROM Match m WHERE m.league = :league")
    List<String> findDistinctExternalIdsByLeague(@Param("league") com.mmo.module.fb.domain.entity.League league);
    
    @Query("SELECT m FROM Match m WHERE m.status = 'FINISHED' AND (m.homeXG IS NULL OR m.awayXG IS NULL)")
    List<Match> findByStatusAndHomeXGIsNullAndAwayXGIsNull();
    
    @Query("SELECT m FROM Match m WHERE m.status = :status AND m.homeXG IS NULL AND m.awayXG IS NULL ORDER BY m.matchTime ASC")
    List<Match> findTop30ByStatusAndHomeXGIsNullAndAwayXGIsNullOrderByMatchTimeAsc(@Param("status") String status);
}
