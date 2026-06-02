package com.mmo.module.fb.repository;

import com.mmo.module.fb.entity.MatchPrediction;
import com.mmo.module.fb.entity.enums.MatchPredictionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface MatchPredictionRepository extends JpaRepository<MatchPrediction, Long> {

    @EntityGraph(attributePaths = {"match"})
    List<MatchPrediction> findByStatus(MatchPredictionStatus status);

    // Job 1: Lấy tất cả các trận có "Value" (hasValue = true) trong ngày để làm Dashboard
    @Query("SELECT mp FROM MatchPrediction mp " +
           "WHERE mp.kickoffTime BETWEEN :startOfDay AND :endOfDay " +
           "AND mp.hasValue = true " +
           "ORDER BY mp.isPremium ASC, mp.kickoffTime ASC")
    List<MatchPrediction> findDailyValueMatches(@Param("startOfDay") LocalDateTime startOfDay,
                                                @Param("endOfDay") LocalDateTime endOfDay);

    // Job 2: Lấy danh sách các trận được chỉ định làm bài viết phân tích chi tiết (Insight)
    @Query("SELECT mp FROM MatchPrediction mp " +
           "WHERE mp.kickoffTime BETWEEN :startOfDay AND :endOfDay " +
           "AND (mp.status = :statusFree OR mp.status = :statusVip)")
    List<MatchPrediction> findMatchesForDetailedInsight(@Param("startOfDay") LocalDateTime startOfDay,
                                                        @Param("endOfDay") LocalDateTime endOfDay,
                                                        @Param("statusFree") MatchPredictionStatus statusFree,
                                                        @Param("statusVip") MatchPredictionStatus statusVip);

    List<MatchPrediction> findByStatusAndKickoffTimeBetween(MatchPredictionStatus status, LocalDateTime start, LocalDateTime end);

    List<MatchPrediction> findByStatusInAndKickoffTimeBetween(Collection<MatchPredictionStatus> statuses, LocalDateTime start, LocalDateTime end);
}
