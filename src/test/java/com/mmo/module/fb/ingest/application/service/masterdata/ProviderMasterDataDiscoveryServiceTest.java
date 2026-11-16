package com.mmo.module.fb.ingest.application.service.masterdata;

import com.mmo.module.fb.ingest.application.model.masterdata.ProviderMasterDataDiscoveryReport;
import com.mmo.module.fb.ingest.application.model.provider.ProviderLeague;
import com.mmo.module.fb.ingest.application.model.provider.ProviderSeason;
import com.mmo.module.fb.ingest.application.model.provider.ProviderTeam;
import com.mmo.module.fb.ingest.application.model.provider.SeasonQuery;
import com.mmo.module.fb.ingest.application.model.provider.TeamQuery;
import com.mmo.module.fb.ingest.application.port.out.provider.LeagueIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.provider.SeasonIngestionPort;
import com.mmo.module.fb.ingest.application.port.out.provider.TeamIngestionPort;
import com.mmo.module.fb.ingest.enums.Provider;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProviderMasterDataDiscoveryServiceTest {

    @Test
    void discoversLeagueSeasonsAndExplicitTeamQueryWithoutPersisting() {
        LeagueIngestionPort leaguePort = mock(LeagueIngestionPort.class);
        SeasonIngestionPort seasonPort = mock(SeasonIngestionPort.class);
        TeamIngestionPort teamPort = mock(TeamIngestionPort.class);

        when(leaguePort.getProvider()).thenReturn(Provider.SOFA_SCORE);
        when(leaguePort.fetchLeagues(List.of(17L))).thenReturn(List.of(
                new ProviderLeague("17", "EPL", "Premier League", "England", null,
                        List.of(new ProviderSeason("100", "2025/2026", true,
                                LocalDate.of(2025, 8, 1), LocalDate.of(2026, 5, 31))))));
        when(teamPort.getProvider()).thenReturn(Provider.SOFA_SCORE);
        when(teamPort.fetchTeams(new TeamQuery(17L, "2025/2026", null))).thenReturn(List.of(
                new ProviderTeam("42", "Arsenal", "ARS", null, null, null, null)));

        ProviderMasterDataDiscoveryService service = new ProviderMasterDataDiscoveryService(
                List.of(leaguePort), List.of(), List.of(teamPort));
        var request = new ProviderMasterDataDiscoveryService.DiscoveryRequest(
                List.of(Provider.SOFA_SCORE), List.of(17L),
                List.of(new ProviderMasterDataDiscoveryReport.TeamQuery(
                        Provider.SOFA_SCORE, 17L, "2025/2026", null)));

        ProviderMasterDataDiscoveryReport report = service.discover(request);

        assertThat(report.status()).isEqualTo("COMPLETED");
        assertThat(report.providers()).hasSize(1);
        assertThat(report.providers().get(0).leagues()).hasSize(1);
        assertThat(report.providers().getFirst().seasons()).hasSize(1);
        assertThat(report.providers().getFirst().teams()).extracting(ProviderTeam::externalId)
                .containsExactly("42");
        verify(leaguePort).fetchLeagues(List.of(17L));
        verify(teamPort).fetchTeams(new TeamQuery(17L, "2025/2026", null));
    }

    @Test
    void reportsProviderFailureAndContinuesWithOtherProviders() {
        LeagueIngestionPort failingPort = mock(LeagueIngestionPort.class);
        LeagueIngestionPort workingPort = mock(LeagueIngestionPort.class);
        when(failingPort.getProvider()).thenReturn(Provider.SOFA_SCORE);
        when(failingPort.fetchLeagues(null)).thenThrow(new IllegalStateException("source unavailable"));
        when(workingPort.getProvider()).thenReturn(Provider.UNDER_STATS);
        when(workingPort.fetchLeagues(null)).thenReturn(List.of());

        ProviderMasterDataDiscoveryService service = new ProviderMasterDataDiscoveryService(
                List.of(failingPort, workingPort), List.of(), List.of());

        ProviderMasterDataDiscoveryReport report = service.discover(
                new ProviderMasterDataDiscoveryService.DiscoveryRequest(
                        List.of(Provider.SOFA_SCORE, Provider.UNDER_STATS), null, null));

        assertThat(report.status()).isEqualTo("PARTIAL");
        assertThat(report.providers()).filteredOn(result -> result.provider() == Provider.SOFA_SCORE)
                .singleElement().extracting(ProviderMasterDataDiscoveryReport.ProviderResult::status)
                .isEqualTo("FAILED");
        assertThat(report.providers()).filteredOn(result -> result.provider() == Provider.UNDER_STATS)
                .singleElement().extracting(ProviderMasterDataDiscoveryReport.ProviderResult::status)
                .isEqualTo("PARTIAL");
    }
}
