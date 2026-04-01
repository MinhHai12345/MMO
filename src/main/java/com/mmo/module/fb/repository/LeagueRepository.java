package com.mmo.module.fb.repository;

import com.mmo.module.fb.entity.League;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeagueRepository extends JpaRepository<League, Long> {

    @NotNull
    @EntityGraph(attributePaths = {"currentSeason"})
    List<League> findAll();

}
