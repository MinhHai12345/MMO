package com.mmo.module.fb.league.repository;

import com.mmo.module.fb.league.entity.Season;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeasonRepository extends JpaRepository<Season, Long> {

    List<Season> findByIsCurrentTrueAndLeagueIsNotNullAndYearIsNotNull();

    List<Season> findByYearInAndLeagueIsNotNullAndYearIsNotNull(List<String> years);

    Optional<Season> findByLeague_IdAndYear(Long leagueId, String year);
}
