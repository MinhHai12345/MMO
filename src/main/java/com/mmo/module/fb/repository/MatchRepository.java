package com.mmo.module.fb.repository;

import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    @Query("SELECT m FROM Match m WHERE m.league = :league " +
            "AND m.matchOdds IS NULL " +
            "AND m.sofaScoreId IS NOT NULL")
    List<Match> findMatchesByLeagueAndMatchOddsIsNull(@Param("league") League league);

    @Query("SELECT m.sofaScoreId FROM Match m WHERE m.sofaScoreId IS NOT NULL")
    Set<Long> findAllSofaScoreIdIsNull();

    List<Match> findByStatusAndHomeXGIsNullAndAwayXGIsNull(MatchStatus status);

    @Query("SELECT m FROM Match m LEFT JOIN FETCH m.matchOdds mo WHERE m.sofaScoreId IN (:sofaScoreIds) " +
           " AND m.status = com.mmo.module.fb.entity.enums.MatchStatus.UPCOMING")
    List<Match> findBySofaScoreIdIn(@Param("sofaScoreIds") Set<Long> sofaScoreIds);

    @Query("SELECT m FROM Match m LEFT JOIN FETCH m.league " +
           " WHERE m.status = com.mmo.module.fb.entity.enums.MatchStatus.PROCESSING " +
           " AND m.notifiedPredict = false ")
    List<Match> findProcessMatch();
}
