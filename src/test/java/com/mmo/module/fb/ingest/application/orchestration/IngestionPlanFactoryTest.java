package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IngestionPlanFactoryTest {

    @Test
    void buildsPrimaryAndFallbackUnitsPerDatasetAndSeason() {
        SeasonRepository repository = mock(SeasonRepository.class);
        when(repository.findAllForActiveLeagues()).thenReturn(List.of(
                season(1L, 39L, "Premier League", "2021/2022", true),
                season(2L, 39L, "Premier League", "2024/2025", true),
                season(3L, 2L, "UEFA Champions League", "2024/2025", true),
                season(4L, 39L, "Premier League", "2023/2024", false)
        ));
        IngestionPlanFactory factory = new IngestionPlanFactory(repository, new DataSourcePolicy());

        IngestionPlan plan = factory.activeSeasonPlan();

        assertTrue(plan.has(Dataset.TEAM, Provider.FOOTBALL_DATA_UK,
                IngestionWorkUnit.Role.PRIMARY));
        assertTrue(plan.has(Dataset.FIXTURE, Provider.FOOTBALL_DATA_UK,
                IngestionWorkUnit.Role.PRIMARY));
        assertTrue(plan.has(Dataset.TEAM, Provider.FOOTBALL_DATA,
                IngestionWorkUnit.Role.PRIMARY));
        assertTrue(plan.has(Dataset.FIXTURE, Provider.FOOTBALL_DATA,
                IngestionWorkUnit.Role.PRIMARY));
        assertTrue(plan.has(Dataset.FIXTURE, Provider.FOOTBALL_DATA_UK,
                IngestionWorkUnit.Role.SECONDARY));
        assertTrue(plan.has(Dataset.STANDINGS, Provider.FOOTBALL_DATA,
                IngestionWorkUnit.Role.PRIMARY));
        assertFalse(plan.units().stream().anyMatch(unit -> unit.seasonId().equals(3L)));
        assertFalse(plan.units().stream().anyMatch(unit -> unit.seasonId().equals(4L)));
        assertFalse(plan.has(Dataset.UPCOMING_ODDS, Provider.API_FOOTBALL,
                IngestionWorkUnit.Role.PRIMARY));
    }

    @Test
    void historicalPlanIncludesInactiveSeasonsButStillHonorsPolicy() {
        SeasonRepository repository = mock(SeasonRepository.class);
        when(repository.findAllForActiveLeagues()).thenReturn(List.of(
                season(1L, 39L, "Premier League", "2018/2019", false)
        ));
        IngestionPlan plan = new IngestionPlanFactory(repository, new DataSourcePolicy()).historicalPlan();

        assertTrue(plan.has(Dataset.FIXTURE, Provider.FOOTBALL_DATA_UK,
                IngestionWorkUnit.Role.PRIMARY));
        assertFalse(plan.has(Dataset.STANDINGS, Provider.FOOTBALL_DATA,
                IngestionWorkUnit.Role.PRIMARY));
    }

    private Season season(Long id, Long leagueId, String leagueName, String year, boolean active) {
        League league = mock(League.class);
        when(league.getId()).thenReturn(leagueId);
        when(league.getName()).thenReturn(leagueName);
        Season season = mock(Season.class);
        when(season.getId()).thenReturn(id);
        when(season.getLeague()).thenReturn(league);
        when(season.getYear()).thenReturn(year);
        when(season.isActive()).thenReturn(active);
        return season;
    }
}
