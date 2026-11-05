package com.mmo.module.fb.domain.match.repository;

import com.mmo.module.fb.domain.match.entity.OddsHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OddsHistoryRepository extends JpaRepository<OddsHistory, Long> {

    @EntityGraph(attributePaths = {"matchOdds", "quote"})
    List<OddsHistory> findAllByMatchOddsIdOrderByRecordedAtAsc(Long matchOddsId, Pageable pageable);

    @EntityGraph(attributePaths = {"quote"})
    List<OddsHistory> findAllByQuoteIdOrderByRecordedAtAsc(Long quoteId, Pageable pageable);
}
