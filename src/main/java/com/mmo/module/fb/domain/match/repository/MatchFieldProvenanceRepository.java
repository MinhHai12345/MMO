package com.mmo.module.fb.domain.match.repository;

import com.mmo.module.fb.domain.match.entity.MatchFieldProvenance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchFieldProvenanceRepository extends JpaRepository<MatchFieldProvenance, Long> {
    Optional<MatchFieldProvenance> findByMatchIdAndFieldKey(Long matchId, String fieldKey);
    List<MatchFieldProvenance> findAllByMatchId(Long matchId);
    List<MatchFieldProvenance> findAllByMatchIdInAndFieldKeyIn(
            List<Long> matchIds, List<String> fieldKeys);
}
