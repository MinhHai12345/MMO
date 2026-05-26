package com.mmo.module.fb.repository;

import com.mmo.module.fb.entity.MatchPrediction;
import com.mmo.module.fb.entity.enums.MatchPredictionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchPredictionRepository extends JpaRepository<MatchPrediction, Long> {

    @EntityGraph(attributePaths = {"match"})
    List<MatchPrediction> findByStatus(MatchPredictionStatus status);
}
