package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.domain.repository.TeamRepository;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FixturePersistenceServiceImplTest {
    @Mock SeasonRepository seasonRepository;
    @Mock TeamRepository teamRepository;
    @Mock MatchRepository matchRepository;
    @Mock MatchExternalIdRepository matchExternalIdRepository;

    @Test
    void shouldRejectBlankProviderFixtureIdWithoutDatabaseAccess() {
        FixturePersistenceServiceImpl service = new FixturePersistenceServiceImpl(seasonRepository, teamRepository,
                matchRepository, matchExternalIdRepository);
        ProviderFixture fixture = new ProviderFixture(" ", "home", "away", null, null,
                null, null, null, null, null, null);

        assertFalse(service.persistFixture(1L, 2L, 3L, Provider.API_FOOTBALL, fixture));

        verifyNoInteractions(seasonRepository, teamRepository, matchRepository, matchExternalIdRepository);
    }

    @Test
    void shouldReturnFalseWhenSeasonIsMissing() {
        when(seasonRepository.findById(1L)).thenReturn(java.util.Optional.empty());
        FixturePersistenceServiceImpl service = new FixturePersistenceServiceImpl(seasonRepository, teamRepository,
                matchRepository, matchExternalIdRepository);
        ProviderFixture fixture = new ProviderFixture("fixture-1", "home", "away", null, null,
                null, null, null, null, null, null);

        assertFalse(service.persistFixture(1L, 2L, 3L, Provider.API_FOOTBALL, fixture));

        verify(seasonRepository).findById(1L);
        verifyNoInteractions(teamRepository, matchRepository, matchExternalIdRepository);
    }
}