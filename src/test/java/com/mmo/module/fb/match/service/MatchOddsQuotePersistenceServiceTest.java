package com.mmo.module.fb.match.service;

import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.entity.Bookmaker;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchOddsQuote;
import com.mmo.module.fb.domain.match.enums.MatchOddsMarketType;
import com.mmo.module.fb.domain.match.enums.MatchOddsPeriod;
import com.mmo.module.fb.domain.match.repository.MatchOddsQuoteRepository;
import com.mmo.module.fb.domain.match.repository.OddsHistoryRepository;
import com.mmo.module.fb.domain.match.service.MatchOddsQuotePersistenceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MatchOddsQuotePersistenceServiceTest {

    @Test
    void storesNormalizedQuoteAndHistoryForOverUnderMarket() {
        MatchOddsQuoteRepository quotes = mock(MatchOddsQuoteRepository.class);
        OddsHistoryRepository history = mock(OddsHistoryRepository.class);
        when(quotes.findByMatchIdAndBookmakerIdAndProviderAndMarketTypeAndMarketPeriodAndLineAndOutcome(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(quotes.save(any(MatchOddsQuote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Match match = new Match();
        match.setId(11L);
        Bookmaker bookmaker = Bookmaker.builder().name("Bet365").build();
        bookmaker.setId(22L);

        MatchOddsQuotePersistenceService service = new MatchOddsQuotePersistenceService(quotes, history);
        service.upsert(match, bookmaker, MatchProvider.FOOTBALL_DATA_UK,
                "OU25_OPEN", "Over 2.5", new BigDecimal("2.5"), new BigDecimal("1.91"));

        var captor = org.mockito.ArgumentCaptor.forClass(MatchOddsQuote.class);
        verify(quotes).save(captor.capture());
        MatchOddsQuote saved = captor.getValue();
        assertEquals(MatchOddsMarketType.OVER_UNDER, saved.getMarketType());
        assertEquals(MatchOddsPeriod.PRE_MATCH_OPEN, saved.getMarketPeriod());
        assertEquals("OVER", saved.getOutcome());
        assertEquals(0, new BigDecimal("2.500").compareTo(saved.getLine()));
        assertEquals(0, new BigDecimal("1.91").compareTo(saved.getOdd()));
        verify(history).save(any());
    }

    @Test
    void doesNotCreateDuplicateHistoryWhenPriceHasNotChanged() {
        MatchOddsQuoteRepository quotes = mock(MatchOddsQuoteRepository.class);
        OddsHistoryRepository history = mock(OddsHistoryRepository.class);
        MatchOddsQuote existing = MatchOddsQuote.builder()
                .marketType(MatchOddsMarketType.MATCH_RESULT)
                .marketPeriod(MatchOddsPeriod.PRE_MATCH_OPEN)
                .line(BigDecimal.ZERO.setScale(3))
                .outcome("HOME")
                .odd(new BigDecimal("2.10"))
                .build();
        when(quotes.findByMatchIdAndBookmakerIdAndProviderAndMarketTypeAndMarketPeriodAndLineAndOutcome(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(existing));

        Match match = new Match();
        match.setId(1L);
        Bookmaker bookmaker = Bookmaker.builder().name("Bookmaker").build();
        bookmaker.setId(2L);

        new MatchOddsQuotePersistenceService(quotes, history).upsert(
                match, bookmaker, MatchProvider.API_FOOTBALL,
                "Match Winner", "Home", null, new BigDecimal("2.100"));

        verify(quotes).save(existing);
        assertNotNull(existing.getFetchedAt());
        verify(history, never()).save(any());
    }

    @Test
    void ignoresInvalidOddsAndMissingIdentity() {
        MatchOddsQuoteRepository quotes = mock(MatchOddsQuoteRepository.class);
        OddsHistoryRepository history = mock(OddsHistoryRepository.class);
        MatchOddsQuotePersistenceService service = new MatchOddsQuotePersistenceService(quotes, history);

        service.upsert(new Match(), new Bookmaker(), MatchProvider.API_FOOTBALL,
                "Match Winner", "Home", null, BigDecimal.ZERO);

        verifyNoInteractions(quotes, history);
    }
}
