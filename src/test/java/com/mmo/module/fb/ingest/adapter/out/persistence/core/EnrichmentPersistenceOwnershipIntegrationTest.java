package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.match.repository.MatchAdvancedStatsRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.domain.match.repository.BookmakerRepository;
import com.mmo.module.fb.domain.match.repository.MatchOddsRepository;
import com.mmo.module.fb.domain.match.service.MatchStatisticsSnapshotPersistenceService;
import com.mmo.module.fb.domain.match.service.MatchOddsQuotePersistenceService;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.ingest.adapter.out.persistence.provider.footballdatauk.FootballDataUkMatchStatisticsPersistenceAdapter;
import com.mmo.module.fb.ingest.adapter.out.persistence.provider.footballdatauk.FootballDataUkOddsPersistenceAdapter;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class EnrichmentPersistenceOwnershipIntegrationTest {

    @Test
    void ukStatisticsAndOddsCannotWriteUnsupportedCompetition() {
        League league = new League();
        league.setId(999L);
        league.setName("UEFA Champions League");
        Season season = new Season();
        season.setId(2024L);
        season.setYear("2024/2025");
        season.setLeague(league);
        Match match = new Match();
        match.setId(77L);
        match.setSeason(season);

        MatchAdvancedStatsRepository statsRepository = mock(MatchAdvancedStatsRepository.class);
        MatchStatisticsSnapshotPersistenceService snapshotService =
                mock(MatchStatisticsSnapshotPersistenceService.class);
        DataSourcePolicy policy = new DataSourcePolicy();
        FootballDataUkMatchStatisticsPersistenceAdapter statsService =
                new FootballDataUkMatchStatisticsPersistenceAdapter(
                        statsRepository, snapshotService, policy,
                        new SourceAwareMergeAdapter(policy, mock(MatchRepository.class),
                                mock(com.mmo.module.fb.domain.match.repository.MatchFieldProvenanceRepository.class)));

        BookmakerRepository bookmakerRepository = mock(BookmakerRepository.class);
        MatchOddsRepository oddsRepository = mock(MatchOddsRepository.class);
        MatchOddsQuotePersistenceService quoteService = mock(MatchOddsQuotePersistenceService.class);
        FootballDataUkOddsPersistenceAdapter oddsService = new FootballDataUkOddsPersistenceAdapter(
                bookmakerRepository, oddsRepository, quoteService, new DataSourcePolicy());

        assertEquals(0, statsService.persist(match, Map.of("HS", "10", "AS", "8")));
        assertEquals(0, oddsService.persist(match, Map.of("B365H", "2.0", "B365D", "3.0", "B365A", "4.0")));

        MatchRepository matchRepository = mock(MatchRepository.class);
        MatchXgPersistenceAdapter xgService = new MatchXgPersistenceAdapter(matchRepository, policy,
                new SourceAwareMergeAdapter(policy, matchRepository,
                        mock(com.mmo.module.fb.domain.match.repository.MatchFieldProvenanceRepository.class)));
        assertFalse(xgService.upsert(Provider.FOOTBALL_DATA_UK, match,
                new BigDecimal("0.75"), new BigDecimal("0.25")));

        verifyNoInteractions(statsRepository, snapshotService);
        verifyNoInteractions(bookmakerRepository, oddsRepository, quoteService, matchRepository);
    }
}
