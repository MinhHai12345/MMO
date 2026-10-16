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
     * Only predictions timestamped strictly before kickoff are eligible.
     * Include already-evaluated predictions: their results are training labels for later folds.
     */
    @Query("""
            SELECT p FROM MatchPrediction p
            JOIN FETCH p.match m
            WHERE m.status = :matchStatus
              AND m.homeScore IS NOT NULL
              AND m.awayScore IS NOT NULL
              AND m.matchTime < :throughExclusive
              AND p.calculatedAt IS NOT NULL
              AND p.calculatedAt < m.matchTime
              AND p.winHomeProb IS NOT NULL
              AND p.drawProb IS NOT NULL
              AND p.winAwayProb IS NOT NULL
            ORDER BY m.matchTime ASC
            """)
    List<MatchPrediction> findWalkForwardCandidates(
            @Param("throughExclusive") Instant throughExclusive,
            @Param("matchStatus") MatchStatus matchStatus,
            Pageable pageable);

    List<MatchPrediction> findByIsPremiumTrue();
}
