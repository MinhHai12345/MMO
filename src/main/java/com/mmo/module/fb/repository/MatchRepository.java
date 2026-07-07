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

    @Query("SELECT DISTINCT m.sofaScoreId FROM Match m WHERE m.league = :league")
    Set<Long> findDistinctSofaScoreIdsAndLeague(@Param("league") League league);

    List<Match> findTop30ByStatusAndHomeXGIsNullAndAwayXGIsNullAndXgRetryCountLessThanOrderByXgRetryCountAsc(MatchStatus matchStatus, int retryCount);

    @Query("SELECT m FROM Match m WHERE m.sofaScoreId IN (:sofaScoreIds) " +
           " AND m.status = com.mmo.module.fb.entity.enums.MatchStatus.UPCOMING")
    List<Match> findBySofaScoreIdIn(@Param("sofaScoreIds") Set<Long> sofaScoreIds);

}
