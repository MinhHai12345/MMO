package com.mmo.module.fb.ingest.persistence;

import com.mmo.module.fb.domain.league.entity.LeagueStanding;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.enums.StandingType;
import com.mmo.module.fb.domain.league.repository.LeagueStandingRepository;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.team.repository.TeamRepository;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.provider.model.ProviderStanding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StandingsPersistenceServiceImplTest {

    @Mock private SeasonRepository seasonRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private LeagueStandingRepository standingRepository;
    @Mock private DataSourcePolicy dataSourcePolicy;

    private StandingsPersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StandingsPersistenceServiceImpl(
                seasonRepository, teamRepository, standingRepository, dataSourcePolicy);
        when(seasonRepository.findById(10L)).thenReturn(Optional.of(new Season()));
        when(teamRepository.findById(20L)).thenReturn(Optional.of(new Team()));
        when(dataSourcePolicy.isPrimary(Provider.FOOTBALL_DATA,
                com.mmo.module.fb.ingest.policy.Dataset.STANDINGS, any(Season.class))).thenReturn(true);
        when(standingRepository.save(any(LeagueStanding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void usesGroupAsPartOfStandingIdentity() {
        ProviderStanding groupA = standing("Group A");
        ProviderStanding groupB = standing("Group B");

        service.upsertStanding(Provider.FOOTBALL_DATA, 10L, 20L, groupA);
        service.upsertStanding(Provider.FOOTBALL_DATA, 10L, 20L, groupB);

        verify(standingRepository).findBySeasonIdAndTeamIdAndStandingTypeAndGroupKey(
                10L, 20L, StandingType.TOTAL, "GROUP A");
        verify(standingRepository).findBySeasonIdAndTeamIdAndStandingTypeAndGroupKey(
                10L, 20L, StandingType.TOTAL, "GROUP B");

        ArgumentCaptor<LeagueStanding> captor = ArgumentCaptor.forClass(LeagueStanding.class);
        verify(standingRepository, times(2)).save(captor.capture());
        assertEquals("GROUP A", captor.getAllValues().get(0).getGroupKey());
        assertEquals("Group A", captor.getAllValues().get(0).getGroupName());
        assertEquals("GROUP B", captor.getAllValues().get(1).getGroupKey());
    }

    @Test
    void usesEmptyNonNullKeyForUngroupedTables() {
        service.upsertStanding(Provider.FOOTBALL_DATA, 10L, 20L, standing(null));

        verify(standingRepository).findBySeasonIdAndTeamIdAndStandingTypeAndGroupKey(
                10L, 20L, StandingType.TOTAL, "");
        ArgumentCaptor<LeagueStanding> captor = ArgumentCaptor.forClass(LeagueStanding.class);
        verify(standingRepository).save(captor.capture());
        assertEquals("", captor.getValue().getGroupKey());
    }

    private static ProviderStanding standing(String group) {
        return new ProviderStanding("57", 1, 25, 14, "WWDWW", group,
                10, 8, 1, 1, 22, 8, StandingType.TOTAL);
    }
}
