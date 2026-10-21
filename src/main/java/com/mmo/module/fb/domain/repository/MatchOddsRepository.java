package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.MatchOdds;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchOddsRepository extends JpaRepository<MatchOdds, Long> {
    Optional<MatchOdds> findByMatchIdAndBookmakerIdAndMarketName(Long matchId, Long bookmakerId, String marketName);

    List<MatchOdds> findAllByMatchId(Long matchId);
}
