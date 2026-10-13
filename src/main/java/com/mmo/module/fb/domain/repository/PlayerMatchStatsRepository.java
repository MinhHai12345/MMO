package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.PlayerMatchStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerMatchStatsRepository extends JpaRepository<PlayerMatchStats, Long> {
}
