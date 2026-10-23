package com.mmo.module.fb.match.repository;

import com.mmo.module.fb.domain.entity.H2HRecord;
import com.mmo.module.fb.match.entity.H2HRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface H2HRecordRepository extends JpaRepository<H2HRecord, Long> {
    
    Optional<H2HRecord> findByHomeTeamIdAndAwayTeamId(Long homeTeamId, Long awayTeamId);
}
