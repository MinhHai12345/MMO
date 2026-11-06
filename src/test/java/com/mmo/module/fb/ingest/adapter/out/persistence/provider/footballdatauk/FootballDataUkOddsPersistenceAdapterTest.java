package com.mmo.module.fb.ingest.adapter.out.persistence.provider.footballdatauk;

import com.mmo.module.fb.domain.match.entity.Bookmaker;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.domain.match.entity.MatchOdds;
import com.mmo.module.fb.domain.match.repository.BookmakerRepository;
import com.mmo.module.fb.domain.match.repository.MatchOddsRepository;
import com.mmo.module.fb.domain.match.service.MatchOddsQuotePersistenceService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FootballDataUkOddsPersistenceAdapterTest {

    @Test
    void mapsOpeningClosingOddsAndAggregateMetadataIntoCore() {
        BookmakerRepository bookmakers = mock(BookmakerRepository.class);
        MatchOddsRepository oddsRepository = mock(MatchOddsRepository.class);
        when(bookmakers.findByName(any())).thenReturn(Optional.empty());
        when(bookmakers.save(any(Bookmaker.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(oddsRepository.findByMatchIdAndBookmakerIdAndMarketName(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(oddsRepository.save(any(MatchOdds.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Match match = new Match();
        match.setId(99L);
        match.setSeason(supportedSeason());
        FootballDataUkOddsPersistenceAdapter mapper = new FootballDataUkOddsPersistenceAdapter(
                bookmakers, oddsRepository, mock(MatchOddsQuotePersistenceService.class), new DataSourcePolicy());
        Map<String, String> row = Map.ofEntries(
                Map.entry("B365H", "2.10"), Map.entry("B365D", "3.30"), Map.entry("B365A", "3.60"),
                Map.entry("B365CH", "2.00"), Map.entry("B365CD", "3.40"), Map.entry("B365CA", "3.80"),
                Map.entry("B365>2.5", "1.90"), Map.entry("B365<2.5", "1.95"),
                Map.entry("B365AH", "-0.5"), Map.entry("B365AHH", "1.91"), Map.entry("B365AHA", "1.99"),
                Map.entry("BBMXH", "2.25"), Map.entry("BBMXD", "3.45"), Map.entry("BBMXA", "3.90"),
                Map.entry("BBMX>2.5", "1.88"), Map.entry("BBMX<2.5", "2.02"),
                Map.entry("BBMXAHH", "1.92"), Map.entry("BBMXAHA", "2.01"),
                Map.entry("BBAHH", "-0.25"), Map.entry("BB1X2", "35"),
                Map.entry("BBOU", "32"), Map.entry("BBAH", "28")
        );

        assertTrue(mapper.persist(match, row) > 0);
        var captor = org.mockito.ArgumentCaptor.forClass(MatchOdds.class);
        verify(oddsRepository, atLeastOnce()).save(captor.capture());
        List<MatchOdds> saved = captor.getAllValues();

        MatchOdds opening = saved.stream().filter(o -> "Bet365".equals(o.getBookmaker().getName())
                && "1X2_OPEN".equals(o.getMarketName())).findFirst().orElseThrow();
        MatchOdds closing = saved.stream().filter(o -> "Bet365".equals(o.getBookmaker().getName())
                && "1X2_CLOSE".equals(o.getMarketName())).findFirst().orElseThrow();
        MatchOdds aggregate = saved.stream().filter(o -> "BetBrain Max".equals(o.getBookmaker().getName())
                && "1X2_OPEN".equals(o.getMarketName())).findFirst().orElseThrow();
        MatchOdds aggregateTotals = saved.stream().filter(o -> "BetBrain Max".equals(o.getBookmaker().getName())
                && "OU25_OPEN".equals(o.getMarketName())).findFirst().orElseThrow();
        MatchOdds aggregateHandicap = saved.stream().filter(o -> "BetBrain Max".equals(o.getBookmaker().getName())
                && "AH_OPEN".equals(o.getMarketName())).findFirst().orElseThrow();

        assertEquals(2.10, opening.getHomeWinOdds().doubleValue(), 0.0001);
        assertEquals(2.00, closing.getHomeWinOdds().doubleValue(), 0.0001);
        assertEquals(35, aggregate.getBookmakerCount());
        assertEquals(32, aggregateTotals.getBookmakerCount());
        assertEquals(28, aggregateHandicap.getBookmakerCount());
        assertEquals(-0.25, aggregateHandicap.getAsianHandicapLine().doubleValue(), 0.0001);
    }
    @Test
    void reusesLegacyBookmakerIdentityWhenCanonicalizingExistingOdds() {
        BookmakerRepository bookmakers = mock(BookmakerRepository.class);
        MatchOddsRepository oddsRepository = mock(MatchOddsRepository.class);
        Bookmaker legacy = Bookmaker.builder().name("B365").sourceType("BOOKMAKER").isActive(true).build();
        legacy.setId(7L);

        when(bookmakers.findByName(any())).thenReturn(Optional.empty());
        when(bookmakers.findByName("B365")).thenReturn(Optional.of(legacy));
        when(bookmakers.save(any(Bookmaker.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(oddsRepository.findByMatchIdAndBookmakerIdAndMarketName(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(oddsRepository.save(any(MatchOdds.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Match match = new Match();
        match.setId(101L);
        match.setSeason(supportedSeason());
        FootballDataUkOddsPersistenceAdapter mapper = new FootballDataUkOddsPersistenceAdapter(
                bookmakers, oddsRepository, mock(MatchOddsQuotePersistenceService.class), new DataSourcePolicy());

        mapper.persist(match, Map.of("B365H", "2.1"));

        assertEquals("Bet365", legacy.getName());
        verify(oddsRepository).findByMatchIdAndBookmakerIdAndMarketName(101L, 7L, "1X2_OPEN");
    }

    private Season supportedSeason() {
        League league = new League();
        league.setId(39L);
        league.setName("Premier League");
        Season season = new Season();
        season.setId(2024L);
        season.setLeague(league);
        season.setYear("2024/2025");
        return season;
    }

}
