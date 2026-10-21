package com.mmo.module.fb.ingest.service.impl;

import com.mmo.AppProperties;
import com.mmo.module.fb.domain.entity.League;
import com.mmo.module.fb.domain.entity.LeagueExternalId;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.entity.TeamExternalId;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.domain.repository.TeamExternalIdRepository;
import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import com.mmo.module.fb.ingest.provider.spi.FixtureProvider;
import com.mmo.module.fb.ingest.provider.spi.ProviderRegistry;
import com.mmo.module.fb.ingest.service.FixturePersistenceService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FixtureIngestionServiceImplTest {

    @Test
    void continuesPersistingFixturesAfterOnePersistenceFailure() {
        ProviderRegistry providerRegistry = mock(ProviderRegistry.class);
        TeamIngestionService teamIngestionService = mock(TeamIngestionService.class);
        SeasonRepository seasonRepository = mock(SeasonRepository.class);
        LeagueExternalIdRepository leagueExternalIdRepository = mock(LeagueExternalIdRepository.class);
        TeamExternalIdRepository teamExternalIdRepository = mock(TeamExternalIdRepository.class);
        FixturePersistenceService fixturePersistenceService = mock(FixturePersistenceService.class);
        FixtureProvider fixtureProvider = mock(FixtureProvider.class);

        AppProperties properties = new AppProperties();
        properties.getFootballData().setEnabled(true);

        League league = new League();
        league.setId(10L);
        Season season = new Season();
        season.setId(20L);
        season.setLeague(league);
        season.setYear("2025");
        season.setCurrent(true);

        LeagueExternalId leagueMapping = new LeagueExternalId();
        leagueMapping.setLeague(league);
        leagueMapping.setProvider(Provider.FOOTBALL_DATA);
        leagueMapping.setExternalId("100");

        Team home = new Team();
        home.setId(30L);
        Team away = new Team();
        away.setId(40L);
        TeamExternalId homeMapping = new TeamExternalId();
        homeMapping.setTeam(home);
        homeMapping.setProvider(Provider.FOOTBALL_DATA);
        homeMapping.setExternalId("home");
        TeamExternalId awayMapping = new TeamExternalId();
        awayMapping.setTeam(away);
        awayMapping.setProvider(Provider.FOOTBALL_DATA);
        awayMapping.setExternalId("away");

        ProviderFixture failedFixture = new ProviderFixture(
                "fixture-1", "home", "away", Instant.parse("2025-08-01T15:00:00Z"),
                "1", null, null, "SCHEDULED", null, null);
        ProviderFixture goodFixture = new ProviderFixture(
                "fixture-2", "home", "away", Instant.parse("2025-08-08T15:00:00Z"),
                "2", null, null, "SCHEDULED", null, null);

        when(teamExternalIdRepository.findAllByProvider(Provider.FOOTBALL_DATA))
                .thenReturn(List.of(homeMapping, awayMapping));
        when(seasonRepository.findByIsCurrentTrueAndLeagueIsNotNullAndYearIsNotNull())
                .thenReturn(List.of(season));
        when(leagueExternalIdRepository.findAllByProvider(Provider.FOOTBALL_DATA))
                .thenReturn(List.of(leagueMapping));
        when(providerRegistry.fixture(Provider.FOOTBALL_DATA)).thenReturn(fixtureProvider);
        when(fixtureProvider.fetchFixtures(any())).thenReturn(List.of(failedFixture, goodFixture));
        when(fixturePersistenceService.persistFixture(
                eq(20L), eq(30L), eq(40L), eq(Provider.FOOTBALL_DATA), eq(failedFixture)))
                .thenThrow(new IllegalStateException("simulated database failure"));
        when(fixturePersistenceService.persistFixture(
                eq(20L), eq(30L), eq(40L), eq(Provider.FOOTBALL_DATA), eq(goodFixture)))
                .thenReturn(true);

        FixtureIngestionServiceImpl service = new FixtureIngestionServiceImpl(
                providerRegistry, properties, teamIngestionService, seasonRepository,
                leagueExternalIdRepository, teamExternalIdRepository, fixturePersistenceService);

        assertThrows(IllegalStateException.class,
                () -> service.syncFixturesForProvider(Provider.FOOTBALL_DATA, null, null));
        verify(fixturePersistenceService, times(1))
                .persistFixture(20L, 30L, 40L, Provider.FOOTBALL_DATA, failedFixture);
        verify(fixturePersistenceService, times(1))
                .persistFixture(20L, 30L, 40L, Provider.FOOTBALL_DATA, goodFixture);
    }
}
