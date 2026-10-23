package com.mmo.module.fb.player.repository;

import com.mmo.module.fb.player.entity.PlayerMatchStats;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlayerMatchStatsRepository extends JpaRepository<PlayerMatchStats, Long> {

    @EntityGraph(attributePaths = {"player", "player.team", "team"})
    List<PlayerMatchStats> findAllByMatchIdOrderByIdAsc(Long matchId);

    long countByMatchId(Long matchId);
}
