package com.mmo.module.fb.domain.service;

import com.mmo.module.fb.domain.entity.LeagueExternalId;
import com.mmo.module.fb.domain.entity.MatchExternalId;
import com.mmo.module.fb.domain.entity.Player;
import com.mmo.module.fb.domain.entity.PlayerExternalId;
import com.mmo.module.fb.domain.entity.SeasonExternalId;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.entity.TeamExternalId;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.repository.PlayerExternalIdRepository;
import com.mmo.module.fb.domain.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.domain.repository.TeamExternalIdRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalIdMappingServiceTest {

    @Mock
    private LeagueExternalIdRepository leagueExternalIds;
    @Mock
    private SeasonExternalIdRepository seasonExternalIds;
    @Mock
    private TeamExternalIdRepository teamExternalIds;
    @Mock
    private MatchExternalIdRepository matchExternalIds;
    @Mock
    private PlayerExternalIdRepository playerExternalIds;

    private ExternalIdMappingService service;

    @BeforeEach
    void setUp() {
        service = new ExternalIdMappingService(
                leagueExternalIds, seasonExternalIds, teamExternalIds, matchExternalIds, playerExternalIds);
    }


    @Test
    void findPlayerTrimsExternalIdAndUsesProviderScopedMapping() {
        Player player = new Player();
        PlayerExternalId mapping = PlayerExternalId.builder()
                .player(player)
                .provider(Provider.API_FOOTBALL)
                .externalId("99")
                .build();
        when(playerExternalIds.findByProviderAndExternalId(Provider.API_FOOTBALL, "99"))
                .thenReturn(Optional.of(mapping));

        assertEquals(Optional.of(player), service.findPlayer(Provider.API_FOOTBALL, " 99 "));
    }

    @Test
    void requiresPersistedPlayerBeforeRegisteringMapping() {
        Player unsavedPlayer = new Player();

        assertThrows(IllegalArgumentException.class,
                () -> service.registerPlayer(unsavedPlayer, Provider.API_FOOTBALL, "99"));
        verifyNoInteractions(playerExternalIds);
    }

    @Test
    void findTeamTrimsExternalIdAndUsesProviderScopedMapping() {
        Team team = new Team();
        TeamExternalId mapping = TeamExternalId.builder()
                .team(team)
                .provider(Provider.API_FOOTBALL)
                .externalId("33")
                .build();
        when(teamExternalIds.findByProviderAndExternalId(Provider.API_FOOTBALL, "33"))
                .thenReturn(Optional.of(mapping));

        assertEquals(Optional.of(team), service.findTeam(Provider.API_FOOTBALL, " 33 "));
    }

    @Test
    void rejectsBlankExternalIdBeforeQueryingRepository() {
        assertThrows(IllegalArgumentException.class,
                () -> service.findTeam(Provider.API_FOOTBALL, "  "));
        verifyNoInteractions(teamExternalIds);
    }

    @Test
    void rejectsNullProviderBeforeQueryingRepository() {
        assertThrows(IllegalArgumentException.class, () -> service.findMatch(null, "123"));
        verifyNoInteractions(matchExternalIds);
    }

    @Test
    void doesNotSilentlyReplaceExistingProviderIdForTeam() {
        Team existingTeam = new Team();
        existingTeam.setId(1L);
        Team requestedTeam = new Team();
        requestedTeam.setId(2L);
        TeamExternalId existingMapping = TeamExternalId.builder()
                .team(existingTeam)
                .provider(Provider.API_FOOTBALL)
                .externalId("old-id")
                .build();
        when(teamExternalIds.findByTeamIdAndProvider(2L, Provider.API_FOOTBALL))
                .thenReturn(Optional.empty());
        when(teamExternalIds.findByProviderAndExternalId(Provider.API_FOOTBALL, "new-id"))
                .thenReturn(Optional.of(existingMapping));

        assertThrows(IllegalStateException.class,
                () -> service.registerTeam(requestedTeam, Provider.API_FOOTBALL, "new-id"));
        verify(teamExternalIds, never()).save(any(TeamExternalId.class));
    }


    @Test
    void rejectsChangingExternalIdForAnEntityWithoutExplicitRemapping() {
        Team team = new Team();
        team.setId(7L);
        TeamExternalId existingMapping = TeamExternalId.builder()
                .team(team)
                .provider(Provider.API_FOOTBALL)
                .externalId("old-id")
                .build();
        when(teamExternalIds.findByTeamIdAndProvider(7L, Provider.API_FOOTBALL))
                .thenReturn(Optional.of(existingMapping));
        when(teamExternalIds.findByProviderAndExternalId(Provider.API_FOOTBALL, "new-id"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.registerTeam(team, Provider.API_FOOTBALL, "new-id"));
        verify(teamExternalIds, never()).save(any(TeamExternalId.class));
    }

    @Test
    void requiresPersistedEntityBeforeRegisteringMapping() {
        Team unsavedTeam = new Team();

        assertThrows(IllegalArgumentException.class,
                () -> service.registerTeam(unsavedTeam, Provider.API_FOOTBALL, "33"));
        verifyNoInteractions(teamExternalIds);
    }
}
