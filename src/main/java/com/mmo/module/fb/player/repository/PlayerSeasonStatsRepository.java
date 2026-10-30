package com.mmo.module.fb.player.repository;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.player.entity.PlayerSeasonStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlayerSeasonStatsRepository extends JpaRepository<PlayerSeasonStats, Long> {
    Optional<PlayerSeasonStats> findByPlayerIdAndSeasonIdAndProvider(Long playerId, Long seasonId, Provider provider);
}
