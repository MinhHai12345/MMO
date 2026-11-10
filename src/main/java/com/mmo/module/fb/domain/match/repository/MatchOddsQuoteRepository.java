package com.mmo.module.fb.domain.match.repository;

import com.mmo.module.fb.domain.match.entity.MatchOddsQuote;
import com.mmo.module.fb.domain.match.enums.MatchOddsMarketType;
import com.mmo.module.fb.domain.match.enums.MatchOddsPeriod;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface MatchOddsQuoteRepository extends JpaRepository<MatchOddsQuote, Long> {

    Optional<MatchOddsQuote> findByMatchIdAndBookmakerIdAndProviderAndMarketTypeAndMarketPeriodAndLineAndOutcome(
            Long matchId, Long bookmakerId, MatchProvider provider, MatchOddsMarketType marketType,
            MatchOddsPeriod marketPeriod, BigDecimal line, String outcome);

    @EntityGraph(attributePaths = {"bookmaker"})
    List<MatchOddsQuote> findAllByMatchIdInAndProvider(Collection<Long> matchIds, MatchProvider provider);

    @EntityGraph(attributePaths = {"bookmaker"})
    List<MatchOddsQuote> findAllByMatchId(Long matchId);

    @EntityGraph(attributePaths = {"bookmaker"})
    List<MatchOddsQuote> findAllByMatchIdAndMarketTypeAndMarketPeriod(
            Long matchId, MatchOddsMarketType marketType, MatchOddsPeriod marketPeriod);

    @EntityGraph(attributePaths = {"bookmaker"})
    List<MatchOddsQuote> findAllByMatchIdAndProvider(Long matchId, MatchProvider provider);


}
