package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.MatchPrediction;
import com.mmo.module.fb.domain.enums.MatchStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatchPredictionRepository extends JpaRepository<MatchPrediction, Long> {

    Optional<MatchPrediction> findByMatchId(Long matchId);

    @Query("""
            SELECT p FROM MatchPrediction p
            JOIN FETCH p.match m
            WHERE m.status = :matchStatus
              AND m.homeScore IS NOT NULL
              AND m.awayScore IS NOT NULL
              AND p.predictionStatus = 'PENDING'
              AND p.winHomeProb IS NOT NULL
              AND p.drawProb IS NOT NULL
              AND p.winAwayProb IS NOT NULL
              AND NOT EXISTS (
                    SELECT performance.id FROM PredictionPerformance performance
                    WHERE performance.prediction = p
              )
            ORDER BY m.matchTime ASC
            """)
    List<MatchPrediction> findUnevaluatedPredictions(@Param("matchStatus") MatchStatus matchStatus,
                                                     Pageable pageable);

    /**
     * Recent pre-window history used to fit calibration. The caller deliberately bounds
     * this query to MAX_SAMPLES; unlike the test window, training history may be capped.
     */
    @Query("""
            SELECT p FROM MatchPrediction p
            JOIN FETCH p.match m
            WHERE m.status = :matchStatus
              AND m.homeScore IS NOT NULL
              AND m.awayScore IS NOT NULL
              AND m.matchTime < :fromInclusive
              AND p.calculatedAt IS NOT NULL
              AND p.calculatedAt < m.matchTime
              AND p.winHomeProb IS NOT NULL
              AND p.drawProb IS NOT NULL
              AND p.winAwayProb IS NOT NULL
            ORDER BY m.matchTime DESC, p.id DESC
            """)
    List<MatchPrediction> findWalkForwardTrainingCandidates(
            @Param("fromInclusive") Instant fromInclusive,
            @Param("matchStatus") MatchStatus matchStatus,
            Pageable pageable);

    /**
     * Return every eligible prediction in the requested test window. The service pages
     * this query until exhaustion so a busy window cannot be silently truncated.
     */
    @Query("""
            SELECT p FROM MatchPrediction p
            JOIN FETCH p.match m
            WHERE m.status = :matchStatus
              AND m.homeScore IS NOT NULL
              AND m.awayScore IS NOT NULL
              AND m.matchTime >= :fromInclusive
              AND m.matchTime < :toExclusive
              AND p.calculatedAt IS NOT NULL
              AND p.calculatedAt < m.matchTime
              AND p.winHomeProb IS NOT NULL
              AND p.drawProb IS NOT NULL
              AND p.winAwayProb IS NOT NULL
            ORDER BY m.matchTime ASC, p.id ASC
            """)
    List<MatchPrediction> findWalkForwardTestCandidates(
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive,
            @Param("matchStatus") MatchStatus matchStatus,
            Pageable pageable);

    List<MatchPrediction> findByIsPremiumTrue();
}
