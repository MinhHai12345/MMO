package com.mmo.module.fb.domain.league.repository;

import com.mmo.module.fb.domain.league.entity.Season;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeasonRepository extends JpaRepository<Season, Long> {

    @EntityGraph(attributePaths = {"league"})
    List<Season> findByIsCurrentTrueAndLeagueIsNotNullAndYearIsNotNull();

    @EntityGraph(attributePaths = {"league"})
    List<Season> findByIsActiveTrueAndLeagueIsNotNullAndYearIsNotNull();

    List<Season> findByYearInAndLeagueIsNotNullAndYearIsNotNull(List<String> years);

    Optional<Season> findByLeague_IdAndYear(Long leagueId, String year);

    /** Loads the league eagerly for policies that need league metadata from detached match graphs. */
    @Query("select s from Season s join fetch s.league where s.id = ?1")
    Optional<Season> findWithLeagueById(Long seasonId);

    /** Batch-loads policy metadata for detached matches without an N+1 query. */
    @Query("select distinct s from Season s join fetch s.league where s.id in ?1")
    List<Season> findAllWithLeagueByIdIn(java.util.Collection<Long> seasonIds);

    @EntityGraph(attributePaths = {"league"})
    List<Season> findByLeague_Id(Long leagueId);

    @EntityGraph(attributePaths = {"league"})
    @Query("select s from Season s join s.league l where l.isActive = true and s.year is not null")
    List<Season> findAllForActiveLeagues();
}
