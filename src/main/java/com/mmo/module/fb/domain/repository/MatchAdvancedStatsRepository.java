package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.MatchAdvancedStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchAdvancedStatsRepository extends JpaRepository<MatchAdvancedStats, Long> {
}
