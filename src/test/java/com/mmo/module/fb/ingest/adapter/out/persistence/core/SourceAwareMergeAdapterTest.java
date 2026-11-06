package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchFieldProvenance;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.repository.MatchFieldProvenanceRepository;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SourceAwareMergeAdapterTest {

    private Match match2024() {
        League league = new League();
        league.setName("Premier League");
        Season season = new Season();
        season.setYear("2024/2025");
        season.setLeague(league);
        Match match = new Match();
        match.setId(99L);
        match.setSeason(season);
        return match;
    }

    @Test
    void primaryProviderCanRefreshItsOwnedField() {
        MatchFieldProvenanceRepository repository = mock(MatchFieldProvenanceRepository.class);
        when(repository.findByMatchIdAndFieldKey(99L, "home_score")).thenReturn(Optional.empty());
        SourceAwareMergeAdapter service = new SourceAwareMergeAdapter(new DataSourcePolicy(), repository);
        AtomicInteger applied = new AtomicInteger();

        assertTrue(service.merge(match2024(), Provider.FOOTBALL_DATA, Dataset.FIXTURE,
                "home_score", true, null, applied::incrementAndGet));
        assertEquals(1, applied.get());
        verify(repository).save(any(MatchFieldProvenance.class));
    }

    @Test
    void secondaryProviderMayFillButCannotOverwriteExistingUnattributedValue() {
        MatchFieldProvenanceRepository repository = mock(MatchFieldProvenanceRepository.class);
        when(repository.findByMatchIdAndFieldKey(99L, "venue")).thenReturn(Optional.empty());
        SourceAwareMergeAdapter service = new SourceAwareMergeAdapter(new DataSourcePolicy(), repository);
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
        when(repository.findByMatchIdAndFieldKey(99L, "home_xg")).thenReturn(Optional.empty());
        SourceAwareMergeAdapter service = new SourceAwareMergeAdapter(new DataSourcePolicy(), repository);
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
        when(repository.findByMatchIdAndFieldKey(99L, "home_score")).thenReturn(Optional.of(current));
        SourceAwareMergeAdapter service = new SourceAwareMergeAdapter(new DataSourcePolicy(), repository);
        AtomicInteger applied = new AtomicInteger();

        assertFalse(service.merge(match2024(), Provider.FOOTBALL_DATA, Dataset.FIXTURE,
                "home_score", true, Instant.parse("2026-01-01T00:00:00Z"), applied::incrementAndGet));
        assertEquals(0, applied.get());
        verify(repository, never()).save(any());
    }
}
