package com.mmo.module.fb.ingest.adapter.out.provider.sofascore;

import com.microsoft.playwright.Page;
import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client.SofaScoreBrowserSessionManager;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client.SofaScoreClient;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.EventDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.MatchesResponseDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.SeasonDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.TeamDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.mapper.SofaScoreTeamMapper;
import com.mmo.module.fb.ingest.application.model.provider.ProviderTeam;
import com.mmo.module.fb.ingest.application.model.provider.TeamQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SofaScoreTeamProviderTest {

    @Mock
    private SofaScoreClient sofaScoreClient;
    @Mock
    private SofaScoreBrowserSessionManager browserSessionManager;
    @Mock
    private Page page;

    private SofaScoreTeamProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SofaScoreTeamProvider(new SofaScoreTeamMapper(), sofaScoreClient, new AppProperties());
        ReflectionTestUtils.setField(provider, "browserSessionManager", browserSessionManager);
        when(browserSessionManager.openPage()).thenReturn(page);
    }

    @Test
    void shouldDiscoverTeamsFromSeasonEventsWhenStandingsAreUnavailable() {
        when(sofaScoreClient.fetchSeasonByTournamentId(17L, page))
                .thenReturn(List.of(new SeasonDTO(2026L, "2025/2026", "2025/2026", true)));
        when(sofaScoreClient.fetchStandingTotal(17L, 2026L, page)).thenReturn(List.of());

        TeamDTO home = team(101L, "Home FC");
        TeamDTO away = team(202L, "Away FC");
        EventDTO event = new EventDTO(9001L, "home-away", 1_700_000_000L, false,
                null, null, home, away, null, null, null, null);
        when(sofaScoreClient.fetchSeasonMatchesPage(17L, 2026L, "last", 0, page))
                .thenReturn(new MatchesResponseDTO(List.of(event), false));
        when(sofaScoreClient.fetchSeasonMatchesPage(17L, 2026L, "next", 0, page))
                .thenReturn(new MatchesResponseDTO(List.of(event), false));

        List<ProviderTeam> teams = provider.fetchTeams(new TeamQuery(17L, "2025/2026"));

        assertThat(teams).extracting(ProviderTeam::externalId).containsExactly("101", "202");
        assertThat(teams).extracting(ProviderTeam::name).containsExactly("Home FC", "Away FC");
        verify(sofaScoreClient).fetchSeasonMatchesPage(17L, 2026L, "last", 0, page);
        verify(sofaScoreClient).fetchSeasonMatchesPage(17L, 2026L, "next", 0, page);
        verify(browserSessionManager).closePage(page);
    }

    @Test
    void shouldPreferStandingsAndAvoidEventFallbackWhenTeamsExist() {
        when(sofaScoreClient.fetchSeasonByTournamentId(17L, page))
                .thenReturn(List.of(new SeasonDTO(2026L, "2025/2026", "2025/2026", true)));
        when(sofaScoreClient.fetchStandingTotal(17L, 2026L, page))
                .thenReturn(List.of(new com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto
                        .SofaScoreResponseDTO.StandingGroupDTO(1L, "total", "Overall",
                        List.of(new com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto
                                .SofaScoreResponseDTO.StandingRowDTO(1, 1, 1, 0, 0, 3, 2, 0, "+2",
                                team(101L, "Home FC"))))));

        List<ProviderTeam> teams = provider.fetchTeams(new TeamQuery(17L, "2025/2026"));

        assertThat(teams).extracting(ProviderTeam::externalId).containsExactly("101");
        verify(sofaScoreClient, never()).fetchSeasonMatchesPage(anyLong(), anyLong(), anyString(), anyInt(), any());
    }

    private TeamDTO team(Long id, String name) {
        return new TeamDTO(id, name, name.toLowerCase().replace(' ', '-'), name, null,
                null, null, null);
    }
}
