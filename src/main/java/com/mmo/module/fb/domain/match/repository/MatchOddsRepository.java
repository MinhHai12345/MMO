package com.mmo.module.fb.domain.match.repository;

import com.mmo.module.fb.domain.match.entity.MatchOdds;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchOddsRepository extends JpaRepository<MatchOdds, Long> {

    Optional<MatchOdds> findByMatchIdAndBookmakerIdAndMarketName(
            Long matchId, Long bookmakerId, String marketName);

    @EntityGraph(attributePaths = {"bookmaker"})
    List<MatchOdds> findAllByMatchId(Long matchId);

    boolean existsByMatchIdAndBookmakerIdAndMarketName(
            Long matchId, Long bookmakerId, String marketName);
}
