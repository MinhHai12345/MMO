package com.mmo.module.fb.ingest.application.service.integration;

import com.mmo.module.fb.ingest.application.model.provider.ProviderLeague;
import com.mmo.module.fb.ingest.application.model.provider.ProviderSeason;
import com.mmo.module.fb.ingest.application.port.out.provider.LeagueIngestionPort;
import com.mmo.module.fb.ingest.enums.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SofaScoreLeagueLookupServiceTest {

    private LeagueIngestionPort sofaScoreProvider;
    private SofaScoreLeagueLookupService service;

    @BeforeEach
    void setUp() {
        sofaScoreProvider = mock(LeagueIngestionPort.class);
        when(sofaScoreProvider.getProvider()).thenReturn(Provider.SOFA_SCORE);
        service = new SofaScoreLeagueLookupService(List.of(sofaScoreProvider));
    }

    @Test
    void fetchLeagueRequestsOnlyTheSelectedTournament() {
        ProviderLeague expected = new ProviderLeague(
                "17", "17", "Premier League", "England", null,
                List.of(new ProviderSeason("2026/2027", "2026/2027", null)));
        when(sofaScoreProvider.fetchLeagues(List.of(17L))).thenReturn(List.of(expected));

        List<ProviderLeague> result = service.fetchLeague(17L);

        assertEquals(List.of(expected), result);
        verify(sofaScoreProvider).fetchLeagues(List.of(17L));
    }

    @Test
    void fetchLeagueRejectsNonPositiveTournamentId() {
        assertThrows(IllegalArgumentException.class, () -> service.fetchLeague(0L));
    }

    @Test
    void fetchLeagueReturnsEmptyWhenRequestedTournamentIsNotReturned() {
        ProviderLeague other = new ProviderLeague("99", "99", "Other League", "Other", null, List.of());
        when(sofaScoreProvider.fetchLeagues(List.of(17L))).thenReturn(List.of(other));

        assertEquals(List.of(), service.fetchLeague(17L));
    }
}
