package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.League;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeagueRepository extends JpaRepository<League, Long> {

    @NotNull
    List<League> findAll();

    @NotNull
    Optional<League> findById(@NotNull Long id);

    @NotNull
    @EntityGraph(attributePaths = {"currentSeason"})
    List<League> findByIsActiveTrue();

}
