package com.mmo.module.fb.repository;

import com.mmo.module.fb.entity.MatchPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchPredictionRepository extends JpaRepository<MatchPrediction, Long> {
}
