package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchFieldProvenance;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.repository.MatchFieldProvenanceRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.ingest.application.model.FieldMergeCommand;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SourceAwareMergeAdapterTest {

    private final SeasonRepository seasonRepository = mock(SeasonRepository.class);

    private MatchRepository mockMatchRepository() {
        MatchRepository repository = mock(MatchRepository.class);
        when(repository.lockAllByIds(anyCollection())).thenReturn(List.of());
        return repository;
    }

    private Match match2024() {
        League league = new League();
        league.setName("Premier League");
        Season season = new Season();
        league.setId(1L);
        season.setId(2024L);
        season.setYear("2024/2025");
        season.setLeague(league);
        when(seasonRepository.findAllWithLeagueByIdIn(anyCollection())).thenReturn(List.of(season));
        Match match = new Match();
        match.setId(99L);
        match.setSeason(season);
        return match;
    }

    @Test
    void primaryProviderCanRefreshItsOwnedField() {
        MatchFieldProvenanceRepository repository = mock(MatchFieldProvenanceRepository.class);
        when(repository.findAllByMatchIdInAndFieldKeyIn(anyList(), anyList())).thenReturn(List.of());
        MatchRepository matchRepository = mock(MatchRepository.class);
        when(matchRepository.lockAllByIds(anyCollection())).thenReturn(List.of());
        SourceAwareMergeAdapter service = new SourceAwareMergeAdapter(new DataSourcePolicy(), matchRepository, seasonRepository, repository);
        AtomicInteger applied = new AtomicInteger();

        assertTrue(service.merge(match2024(), Provider.FOOTBALL_DATA, Dataset.FIXTURE,
                "home_score", true, null, applied::incrementAndGet));
        assertEquals(1, applied.get());
        verify(repository).saveAll(anyList());
        verify(repository, never()).findByMatchIdAndFieldKey(anyLong(), anyString());
    }

    @Test
    void secondaryProviderMayFillButCannotOverwriteExistingUnattributedValue() {
        MatchFieldProvenanceRepository repository = mock(MatchFieldProvenanceRepository.class);
        when(repository.findAllByMatchIdInAndFieldKeyIn(anyList(), anyList())).thenReturn(List.of());
        SourceAwareMergeAdapter service = new SourceAwareMergeAdapter(new DataSourcePolicy(), mockMatchRepository(), seasonRepository, repository);
        AtomicInteger applied = new AtomicInteger();

        assertTrue(service.merge(match2024(), Provider.FOOTBALL_DATA_UK, Dataset.FIXTURE,
                "venue", false, null, applied::incrementAndGet));
        assertFalse(service.merge(match2024(), Provider.FOOTBALL_DATA_UK, Dataset.FIXTURE,
                "venue", true, null, applied::incrementAndGet));
        assertEquals(1, applied.get());
    }

    @Test
    void understatCanFillMissingXgButCannotOverwriteExistingPrimaryValue() {
        MatchFieldProvenanceRepository repository = mock(MatchFieldProvenanceRepository.class);
        when(repository.findAllByMatchIdInAndFieldKeyIn(anyList(), anyList())).thenReturn(List.of());
        SourceAwareMergeAdapter service = new SourceAwareMergeAdapter(new DataSourcePolicy(), mockMatchRepository(), seasonRepository, repository);
        AtomicInteger applied = new AtomicInteger();

        assertTrue(service.merge(match2024(), Provider.UNDER_STATS, Dataset.MATCH_XG,
                "home_xg", false, null, applied::incrementAndGet));
        assertFalse(service.merge(match2024(), Provider.UNDER_STATS, Dataset.MATCH_XG,
                "home_xg", true, null, applied::incrementAndGet));
        assertEquals(1, applied.get());
    }

    @Test
    void rejectsStaleRevisionFromSameProvider() {
        MatchFieldProvenanceRepository repository = mock(MatchFieldProvenanceRepository.class);
        MatchFieldProvenance current = MatchFieldProvenance.builder()
                .match(match2024()).fieldKey("home_score").datasetKey("FIXTURE")
                .provider(MatchProvider.FOOTBALL_DATA)
                .sourceUpdatedAt(Instant.parse("2026-01-02T00:00:00Z")).build();
        when(repository.findAllByMatchIdInAndFieldKeyIn(anyList(), anyList())).thenReturn(List.of(current));
        SourceAwareMergeAdapter service = new SourceAwareMergeAdapter(new DataSourcePolicy(), mockMatchRepository(), seasonRepository, repository);
        AtomicInteger applied = new AtomicInteger();

        assertFalse(service.merge(match2024(), Provider.FOOTBALL_DATA, Dataset.FIXTURE,
                "home_score", true, Instant.parse("2026-01-01T00:00:00Z"), applied::incrementAndGet));
        assertEquals(0, applied.get());
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void prefetchesAndPersistsProvenanceOnceForMultipleFields() {
        MatchFieldProvenanceRepository repository = mock(MatchFieldProvenanceRepository.class);
        when(repository.findAllByMatchIdInAndFieldKeyIn(anyList(), anyList())).thenReturn(List.of());
        SourceAwareMergeAdapter service = new SourceAwareMergeAdapter(new DataSourcePolicy(), mockMatchRepository(), seasonRepository, repository);
        Match match = match2024();
        AtomicInteger applied = new AtomicInteger();
        List<FieldMergeCommand> commands = List.of(
                new FieldMergeCommand(match, Provider.FOOTBALL_DATA, Dataset.FIXTURE,
                        "home_score", true, null, applied::incrementAndGet),
                new FieldMergeCommand(match, Provider.FOOTBALL_DATA, Dataset.FIXTURE,
                        "away_score", true, null, applied::incrementAndGet),
                new FieldMergeCommand(match, Provider.FOOTBALL_DATA, Dataset.FIXTURE,
                        "status", true, null, applied::incrementAndGet));

        assertEquals(3, service.mergeAll(commands));
        assertEquals(3, applied.get());
        verify(repository, times(1)).findAllByMatchIdInAndFieldKeyIn(anyList(), anyList());
        verify(repository, times(1)).saveAll(anyList());
    }
}
