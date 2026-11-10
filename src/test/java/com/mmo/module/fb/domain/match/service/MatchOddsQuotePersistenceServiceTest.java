package com.mmo.module.fb.domain.match.service;

import com.mmo.module.fb.domain.match.entity.Bookmaker;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchOddsQuote;
import com.mmo.module.fb.domain.match.entity.OddsHistory;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.repository.MatchOddsQuoteRepository;
import com.mmo.module.fb.domain.match.repository.OddsHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchOddsQuotePersistenceServiceTest {

    @Mock
    private MatchOddsQuoteRepository quoteRepository;

    @Mock
    private OddsHistoryRepository historyRepository;

    @InjectMocks
    private MatchOddsQuotePersistenceService service;

    @Test
    void bulkUpsertPrefetchesAndPersistsQuotesForWholeFixtureChunk() {
        Match first = match(101L);
        Match second = match(102L);
        Map<Bookmaker, List<MatchOddsQuotePersistenceService.QuoteInput>> firstQuotes =
                quoteValues(new BigDecimal("2.10"));
        Map<Bookmaker, List<MatchOddsQuotePersistenceService.QuoteInput>> secondQuotes =
                quoteValues(new BigDecimal("3.20"));

        when(quoteRepository.findAllByMatchIdInAndProvider(
                List.of(101L, 102L), MatchProvider.FOOTBALL_DATA_UK)).thenReturn(List.of());
        when(quoteRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<MatchOddsQuote> values = invocation.getArgument(0);
            List<MatchOddsQuote> saved = new ArrayList<>();
            values.forEach(saved::add);
            return saved;
        });
        when(historyRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<OddsHistory> values = invocation.getArgument(0);
            List<OddsHistory> saved = new ArrayList<>();
            values.forEach(saved::add);
            return saved;
        });

        service.upsertAll(List.of(
                new MatchOddsQuotePersistenceService.QuoteBatchInput(
                        first, MatchProvider.FOOTBALL_DATA_UK, firstQuotes),
                new MatchOddsQuotePersistenceService.QuoteBatchInput(
                        second, MatchProvider.FOOTBALL_DATA_UK, secondQuotes)));

        verify(quoteRepository).findAllByMatchIdInAndProvider(
                List.of(101L, 102L), MatchProvider.FOOTBALL_DATA_UK);
        verify(quoteRepository).saveAll(any());
        verify(historyRepository).saveAll(any());
    }

    private static Match match(Long id) {
        Match match = new Match();
        match.setId(id);
        return match;
    }

    private static Map<Bookmaker, List<MatchOddsQuotePersistenceService.QuoteInput>> quoteValues(BigDecimal odd) {
        Bookmaker bookmaker = Bookmaker.builder().name("Bet365").build();
        bookmaker.setId(7L);
        Map<Bookmaker, List<MatchOddsQuotePersistenceService.QuoteInput>> values = new LinkedHashMap<>();
        values.put(bookmaker, List.of(new MatchOddsQuotePersistenceService.QuoteInput(
                "1X2_OPEN", "HOME", null, odd)));
        return values;
    }
}
