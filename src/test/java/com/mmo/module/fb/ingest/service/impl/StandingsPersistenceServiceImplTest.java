package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.domain.repository.LeagueStandingRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.domain.repository.TeamRepository;
import com.mmo.module.fb.ingest.provider.model.ProviderStanding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StandingsPersistenceServiceImplTest {
    @Mock SeasonRepository seasonRepository;
    @Mock TeamRepository teamRepository;
    @Mock LeagueStandingRepository standingRepository;

    @Test
    void shouldFailFastWhenSeasonDoesNotExist() {
        when(seasonRepository.findById(11L)).thenReturn(java.util.Optional.empty());
        StandingsPersistenceServiceImpl service = new StandingsPersistenceServiceImpl(
                seasonRepository, teamRepository, standingRepository);
        ProviderStanding row = new ProviderStanding("team-1", 1, 30, 20, "WWDWW", null,
                10, 9, 3, 0, 25, 5);

        assertThrows(IllegalArgumentException.class, () -> service.upsertStanding(11L, 21L, row));

        verify(seasonRepository).findById(11L);
        verifyNoInteractions(teamRepository, standingRepository);
    }
}