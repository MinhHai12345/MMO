package com.mmo.module.fb.repository;

import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    @Query("SELECT m FROM Match m WHERE m.league = :league " +
            "AND m.matchOdds IS NULL " +
            "AND m.sofaScoreId IS NOT NULL")
    List<Match> findMatchesByLeagueAndMatchOddsIsNull(@Param("league") League league);
}
