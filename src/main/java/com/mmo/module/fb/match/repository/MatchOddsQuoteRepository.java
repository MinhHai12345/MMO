package com.mmo.module.fb.match.repository;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.match.entity.MatchOddsQuote;
import com.mmo.module.fb.match.enums.MatchOddsMarketType;
import com.mmo.module.fb.match.enums.MatchOddsPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MatchOddsQuoteRepository extends JpaRepository<MatchOddsQuote, Long> {

    Optional<MatchOddsQuote> findByMatchIdAndBookmakerIdAndProviderAndMarketTypeAndMarketPeriodAndLineAndOutcome(
            Long matchId, Long bookmakerId, Provider provider, MatchOddsMarketType marketType,
            MatchOddsPeriod marketPeriod, BigDecimal line, String outcome);

    List<MatchOddsQuote> findAllByMatchId(Long matchId);

    List<MatchOddsQuote> findAllByMatchIdAndMarketTypeAndMarketPeriod(
            Long matchId, MatchOddsMarketType marketType, MatchOddsPeriod marketPeriod);

    List<MatchOddsQuote> findAllByMatchIdAndProvider(Long matchId, Provider provider);
}
