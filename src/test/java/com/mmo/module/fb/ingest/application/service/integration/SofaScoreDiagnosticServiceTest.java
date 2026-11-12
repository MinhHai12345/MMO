package com.mmo.module.fb.ingest.application.service.integration;

import com.mmo.module.fb.ingest.application.model.SofaScoreDiagnosticRequest;
import com.mmo.module.fb.ingest.application.model.provider.*;
import com.mmo.module.fb.ingest.application.port.out.provider.*;
import com.mmo.module.fb.ingest.enums.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SofaScoreDiagnosticServiceTest {

    private LeagueIngestionPort leagueProvider;
    private TeamIngestionPort teamProvider;
    private FixtureIngestionPort fixtureProvider;
    private PlayerLineupsIngestionPort lineupsProvider;
    private SofaScoreDiagnosticService service;

    @BeforeEach
    void setUp() {
        leagueProvider = mock(LeagueIngestionPort.class);
        teamProvider = mock(TeamIngestionPort.class);
        fixtureProvider = mock(FixtureIngestionPort.class);
        lineupsProvider = mock(PlayerLineupsIngestionPort.class);

        when(leagueProvider.getProvider()).thenReturn(Provider.SOFA_SCORE);
        when(teamProvider.getProvider()).thenReturn(Provider.SOFA_SCORE);
        when(fixtureProvider.getProvider()).thenReturn(Provider.SOFA_SCORE);
        when(lineupsProvider.getProvider()).thenReturn(Provider.SOFA_SCORE);

        service = new SofaScoreDiagnosticService(
                List.of(leagueProvider), List.of(teamProvider),
                List.of(fixtureProvider), List.of(lineupsProvider));
    }

    @Test
    void checksSofaScoreStagesWithoutInvokingPersistence() {
        when(leagueProvider.fetchLeagues(List.of(17L))).thenReturn(List.of(
                new ProviderLeague("17", "bundesliga", "Bundesliga", "Germany", null,
                        List.of(new ProviderSeason("2026/2027", "2026/2027", true, null, null)))));
        when(teamProvider.fetchTeams(any())).thenReturn(List.of(
                new ProviderTeam("10", "Team A", "A", null, null, null, null)));
        when(fixtureProvider.fetchFixtures(any())).thenReturn(List.of(
                new ProviderFixture("100", "10", "11", null, null, null, null, "NOT_STARTED", null, null)));
        when(lineupsProvider.fetchLineups("100")).thenReturn(Optional.of(
                new ProviderLineups(List.of(), List.of())));

        var result = service.diagnose(new SofaScoreDiagnosticRequest(
                17L, "2026/2027", LocalDate.parse("2026-11-01"),
                LocalDate.parse("2026-11-07"), 100L));

        assertEquals("SOFA_SCORE", result.provider());
        assertEquals("PARTIAL", result.status());
        assertEquals(List.of("TOURNAMENT_AND_SEASONS", "TEAMS", "FIXTURES", "LINEUPS"),
                result.stages().stream().map(stage -> stage.name()).toList());
        assertEquals("SUCCESS", result.stages().get(0).status());
        assertEquals("EMPTY", result.stages().get(3).status());
        verify(leagueProvider).fetchLeagues(List.of(17L));
        verify(teamProvider).fetchTeams(any());
        verify(fixtureProvider).fetchFixtures(any());
        verify(lineupsProvider).fetchLineups("100");
    }

    @Test
    void stopsDependentChecksWhenTournamentLookupFails() {
        when(leagueProvider.fetchLeagues(List.of(17L)))
                .thenThrow(new IllegalStateException("SofaScore unavailable"));

        var result = service.diagnose(new SofaScoreDiagnosticRequest(
                17L, "2026/2027", LocalDate.parse("2026-11-01"),
                LocalDate.parse("2026-11-07"), null));

        assertEquals("FAILED", result.status());
        assertEquals("FAILED", result.stages().get(0).status());
        assertEquals("SKIPPED", result.stages().get(1).status());
        assertEquals("SKIPPED", result.stages().get(2).status());
        verifyNoInteractions(teamProvider, fixtureProvider, lineupsProvider);
    }

    @Test
    void rejectsDateRangesLongerThanThirtyOneDays() {
        assertThrows(IllegalArgumentException.class, () -> service.diagnose(
                new SofaScoreDiagnosticRequest(17L, "2026/2027",
                        LocalDate.parse("2026-01-01"), LocalDate.parse("2026-02-01"), null)));

        verifyNoInteractions(leagueProvider, teamProvider, fixtureProvider, lineupsProvider);
    }
}
