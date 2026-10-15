package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.domain.entity.League;
import com.mmo.module.fb.domain.entity.LeagueExternalId;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.LeagueRepository;
import com.mmo.module.fb.domain.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaguePersistenceServiceImplTest {

    @Mock
    private LeagueRepository leagueRepository;

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private LeagueExternalIdRepository leagueExternalIdRepository;

    @Mock
    private SeasonExternalIdRepository seasonExternalIdRepository;

    @InjectMocks
    private LeaguePersistenceServiceImpl service;

    @Test
    void shouldNotReassignExistingProviderIdForCanonicalLeague() {
        League existingLeague = new League();
        existingLeague.setId(10L);
        existingLeague.setName("Premier League");

        LeagueExternalId existingMapping = LeagueExternalId.builder()
                .league(existingLeague)
                .provider(Provider.API_FOOTBALL)
                .externalId("old-provider-id")
                .build();

        when(leagueRepository.findByIsActive(true)).thenReturn(List.of(existingLeague));
        when(leagueExternalIdRepository.findByProviderAndExternalId(
                Provider.API_FOOTBALL, "new-provider-id")).thenReturn(Optional.empty());
        when(leagueExternalIdRepository.findByLeagueIdAndProvider(10L, Provider.API_FOOTBALL))
                .thenReturn(Optional.of(existingMapping));

        service.persistLeagues(Provider.API_FOOTBALL,
                List.of(new ProviderLeague("new-provider-id", "Premier League", "England", null, List.of())));

        verify(leagueRepository, never()).save(any(League.class));
        verify(leagueExternalIdRepository, never()).save(any(LeagueExternalId.class));
        verifyNoInteractions(seasonRepository, seasonExternalIdRepository);
    }
}
