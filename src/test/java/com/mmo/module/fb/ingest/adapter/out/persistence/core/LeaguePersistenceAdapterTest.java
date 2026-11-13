package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.LeagueExternalId;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.league.repository.LeagueRepository;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.ingest.adapter.out.persistence.identity.ExternalIdMappingAdapter;
import com.mmo.module.fb.ingest.application.model.provider.ProviderLeague;
import com.mmo.module.fb.ingest.application.model.provider.ProviderSeason;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeaguePersistenceAdapterTest {

    private LeagueRepository leagueRepository;
    private SeasonRepository seasonRepository;
    private LeagueExternalIdRepository leagueExternalIdRepository;
    private ExternalIdMappingAdapter externalIdMappingAdapter;
    private DataSourcePolicy dataSourcePolicy;
    private LeaguePersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        leagueRepository = mock(LeagueRepository.class);
        seasonRepository = mock(SeasonRepository.class);
        leagueExternalIdRepository = mock(LeagueExternalIdRepository.class);
        externalIdMappingAdapter = mock(ExternalIdMappingAdapter.class);
        dataSourcePolicy = mock(DataSourcePolicy.class);
        adapter = new LeaguePersistenceAdapter(
                leagueRepository,
                seasonRepository,
                leagueExternalIdRepository,
                externalIdMappingAdapter,
                dataSourcePolicy);
    }

    @Test
    void shouldPersistSofaScoreLeagueAndSeasonMappingsWithoutChangingCanonicalMetadata() {
        League canonicalLeague = mock(League.class);
        when(canonicalLeague.getId()).thenReturn(10L);
        when(canonicalLeague.getCountry()).thenReturn("England");
        when(leagueExternalIdRepository.findByProviderAndExternalId(
                Provider.SOFA_SCORE, "tournament-123")).thenReturn(Optional.empty());
        when(leagueExternalIdRepository.findByLeagueIdAndProvider(
                10L, Provider.SOFA_SCORE)).thenReturn(Optional.empty());
        when(leagueRepository.findAllByNameIgnoreCase("Premier League"))
                .thenReturn(List.of(canonicalLeague));

        Season canonicalSeason = mock(Season.class);
        when(canonicalSeason.getId()).thenReturn(20L);
        when(canonicalSeason.getYear()).thenReturn("2026/2027");
        when(seasonRepository.findByLeague_IdAndYear(10L, "2026")).thenReturn(Optional.empty());
        when(seasonRepository.findByLeague_Id(10L)).thenReturn(List.of(canonicalSeason));
        when(dataSourcePolicy.isPrimary(Provider.SOFA_SCORE, Dataset.SEASON, "2026"))
                .thenReturn(false);

        LeagueExternalId leagueMapping = mock(LeagueExternalId.class);
        when(externalIdMappingAdapter.registerLeague(
                canonicalLeague, Provider.SOFA_SCORE, "tournament-123")).thenReturn(leagueMapping);
        when(leagueExternalIdRepository.save(leagueMapping)).thenReturn(leagueMapping);

        ProviderSeason providerSeason = new ProviderSeason(
                "season-456", "2026", true, null, null);
        ProviderLeague providerLeague = new ProviderLeague(
                "tournament-123", "123", "Premier League", "England", null,
                List.of(providerSeason));

        adapter.persistLeagues(Provider.SOFA_SCORE, List.of(providerLeague));

        verify(externalIdMappingAdapter).registerLeague(
                canonicalLeague, Provider.SOFA_SCORE, "tournament-123");
        verify(leagueExternalIdRepository).save(leagueMapping);
        verify(externalIdMappingAdapter).registerSeason(
                canonicalSeason, Provider.SOFA_SCORE, "season-456");
        verify(leagueRepository, never()).save(any(League.class));
        verify(seasonRepository, never()).save(any(Season.class));
    }

    @Test
    void shouldNotCreateMappingWhenSofaScoreLeagueMatchIsAmbiguous() {
        League firstCandidate = mock(League.class);
        League secondCandidate = mock(League.class);
        when(firstCandidate.getId()).thenReturn(10L);
        when(firstCandidate.getCountry()).thenReturn("England");
        when(secondCandidate.getId()).thenReturn(11L);
        when(secondCandidate.getCountry()).thenReturn("England");
        when(leagueExternalIdRepository.findByProviderAndExternalId(
                Provider.SOFA_SCORE, "tournament-123")).thenReturn(Optional.empty());
        when(leagueRepository.findAllByNameIgnoreCase("Premier League"))
                .thenReturn(List.of(firstCandidate, secondCandidate));

        ProviderLeague providerLeague = new ProviderLeague(
                "tournament-123", "123", "Premier League", "England", null, List.of());

        adapter.persistLeagues(Provider.SOFA_SCORE, List.of(providerLeague));

        verify(externalIdMappingAdapter, never()).registerLeague(
                any(League.class), eq(Provider.SOFA_SCORE), anyString());
        verify(leagueRepository, never()).save(any(League.class));
    }
}
