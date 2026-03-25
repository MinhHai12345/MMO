package com.mmo.module.fb.repository;

import com.mmo.module.fb.entity.PerformanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PerformanceHistoryRepository extends JpaRepository<PerformanceHistory, Long> {

    @Query("SELECT ph FROM PerformanceHistory ph " +
           "JOIN ph.team t " +
           "WHERE t.id = :teamId " +
           "AND ph.date >= :seasonStart ")
    List<PerformanceHistory> findByTeamIdAndDateGreaterThan(@Param("teamId") Long teamId,
                                                            @Param("seasonStart") LocalDateTime seasonStart);

    List<PerformanceHistory> findTop10ByTeamIdOrderByDateDesc(@Param("teamId") Long teamId);
}
