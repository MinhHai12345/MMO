package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.MatchPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchPredictionRepository extends JpaRepository<MatchPrediction, Long> {
    
    Optional<MatchPrediction> findByMatchId(Long matchId);
    List<MatchPrediction> findByIsPremiumTrue();
}
