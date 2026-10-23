package com.mmo.module.fb.match.repository;

import com.mmo.module.fb.domain.entity.OddsHistory;
import com.mmo.module.fb.match.entity.OddsHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OddsHistoryRepository extends JpaRepository<OddsHistory, Long> {
}
