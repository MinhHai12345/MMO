package com.mmo.module.fb.workflow.application.service;

import com.mmo.module.fb.analytics.service.DataCoverageAuditService;
import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchStatisticsSourceSnapshot;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.enums.MatchStatus;
import com.mmo.module.fb.domain.match.repository.MatchStatisticsSourceSnapshotRepository;
import com.mmo.module.fb.domain.team.entity.Team;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
        "mmo.workflow.readiness.minimum-score-coverage=0.95",
        "mmo.workflow.readiness.minimum-xg-coverage=0.80",
        "mmo.workflow.readiness.minimum-team-statistics-coverage=0.80",
        "mmo.workflow.readiness.maximum-snapshot-age-hours=72"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DataReadinessGateService.class)
@EnabledIfEnvironmentVariable(named = "MMO_MYSQL_TEST_URL", matches = "^jdbc:mysql:.*$")
class DataReadinessGateMySqlIntegrationTest {

    @Autowired
    private DataReadinessGateService readinessGateService;

    @Autowired
    private MatchStatisticsSourceSnapshotRepository snapshotRepository;

    @Autowired
    private EntityManager entityManager;

    @MockBean
    private DataCoverageAuditService coverageAuditService;

    private Long seasonId;
    private Match match;
    private Team homeTeam;

    @BeforeEach
    void persistSeasonFixtureGraph() {
        League league = League.builder()
                .name("Readiness Integration League")
                .slug("readiness-integration-league")
                .country("Test")
                .isActive(true)
                .build();
        entityManager.persist(league);

        Season season = Season.builder()
                .league(league)
                .year("2026")
                .isCurrent(true)
                .isActive(true)
                .build();
        entityManager.persist(season);

        homeTeam = Team.builder().name("Readiness Home").slug("readiness-home").build();
        Team awayTeam = Team.builder().name("Readiness Away").slug("readiness-away").build();
        entityManager.persist(homeTeam);
        entityManager.persist(awayTeam);

        match = Match.builder()
                .season(season)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .matchTime(Instant.parse("2026-11-01T15:00:00Z"))
                .status(MatchStatus.FINISHED)
                .homeScore(2)
                .awayScore(1)
                .homeXG(1.7)
                .awayXG(0.8)
                .build();
        entityManager.persist(match);
        entityManager.flush();
        seasonId = season.getId();
    }

    @Test
    void shouldPassWhenCurrentSeasonHasFreshSnapshotAndCoverageMeetsThresholds() {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        stubAudit(10, 10, 0.90);
        persistSnapshot(now.minusSeconds(24 * 60 * 60L));

        DataReadinessGateService.ReadinessReport report = readinessGateService.evaluateAt(now);

        assertTrue(report.ready());
        assertTrue(report.issues().isEmpty());
        assertEquals(1, report.seasons().size());
        assertTrue(report.seasons().getFirst().fresh());
        assertTrue(report.seasons().getFirst().issues().isEmpty());
        assertEquals(now.minusSeconds(24 * 60 * 60L),
                report.seasons().getFirst().latestStatisticsSnapshotAt());
    }

    @Test
    void shouldAcceptSnapshotExactlyAtMaximumAgeBoundary() {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        stubAudit(10, 10, 0.90);
        persistSnapshot(now.minusSeconds(72 * 60 * 60L));

        DataReadinessGateService.ReadinessReport report = readinessGateService.evaluateAt(now);

        assertTrue(report.ready());
        assertTrue(report.seasons().getFirst().fresh());
    }

    @Test
    void shouldRejectStaleCurrentSeasonSnapshot() {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        stubAudit(10, 10, 0.90);
        persistSnapshot(now.minusSeconds(72 * 60 * 60L + 1));

        DataReadinessGateService.ReadinessReport report = readinessGateService.evaluateAt(now);

        assertFalse(report.ready());
        assertFalse(report.seasons().getFirst().fresh());
        assertTrue(report.seasons().getFirst().issues().contains("STALE_OR_MISSING_STATISTICS_SNAPSHOT"));
    }

    @Test
    void shouldRejectFutureDatedSnapshotInsteadOfTreatingItAsFresh() {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        stubAudit(10, 10, 0.90);
        persistSnapshot(now.plusSeconds(60));

        DataReadinessGateService.ReadinessReport report = readinessGateService.evaluateAt(now);

        assertFalse(report.ready());
        assertFalse(report.seasons().getFirst().fresh());
        assertTrue(report.seasons().getFirst().issues().contains("FUTURE_STATISTICS_SNAPSHOT"));
    }

    @Test
    void shouldRejectMissingSnapshot() {
        stubAudit(10, 10, 0.90);

        DataReadinessGateService.ReadinessReport report =
                readinessGateService.evaluateAt(Instant.parse("2026-11-10T12:00:00Z"));

        assertFalse(report.ready());
        assertTrue(report.seasons().getFirst().issues().contains("STALE_OR_MISSING_STATISTICS_SNAPSHOT"));
    }

    @Test
    void shouldRejectInsufficientScoreXgAndTeamStatisticsCoverage() {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        stubAudit(9, 7, 0.70);
        persistSnapshot(now.minusSeconds(60));

        DataReadinessGateService.ReadinessReport report = readinessGateService.evaluateAt(now);

        assertFalse(report.ready());
        assertTrue(report.seasons().getFirst().issues().contains("INSUFFICIENT_SCORE_COVERAGE"));
        assertTrue(report.seasons().getFirst().issues().contains("INSUFFICIENT_XG_COVERAGE"));
        assertTrue(report.seasons().getFirst().issues().contains("INSUFFICIENT_TEAM_STATISTICS_COVERAGE"));
    }

    @Test
    void shouldReportWhyReadinessIsBlockedWhenThereAreNoActiveSeasons() {
        when(coverageAuditService.runAudit()).thenReturn(auditReport(List.of()));
        
        DataReadinessGateService.ReadinessReport report =
                readinessGateService.evaluateAt(Instant.parse("2026-11-10T12:00:00Z"));

        assertFalse(report.ready());
        assertTrue(report.seasons().isEmpty());
        assertEquals(List.of("NO_ACTIVE_SEASONS"), report.issues());
    }

    @Test
    void shouldUseLatestSnapshotAcrossAllProvidersForTheSeason() {
        Instant now = Instant.parse("2026-11-10T12:00:00Z");
        stubAudit(10, 10, 0.90);
        persistSnapshot(now.minusSeconds(48 * 60 * 60L), MatchProvider.FOOTBALL_DATA);
        persistSnapshot(now.minusSeconds(30), MatchProvider.SOFA_SCORE);

        DataReadinessGateService.ReadinessReport report = readinessGateService.evaluateAt(now);

        assertTrue(report.ready());
        assertEquals(now.minusSeconds(30), report.seasons().getFirst().latestStatisticsSnapshotAt());
        assertEquals(2, snapshotRepository.findAllByMatchId(match.getId()).size());
    }

    @Test
    void shouldRejectInvalidCoverageThresholdConfiguration() {
        ReflectionTestUtils.setField(readinessGateService, "minimumXgCoverage", 1.1);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> readinessGateService.evaluateAt(Instant.parse("2026-11-10T12:00:00Z")));

        assertTrue(exception.getMessage().contains("minimum-xg-coverage"));
    }

    private void stubAudit(long scoredFixtures, long fixturesWithXg, double teamStatisticsCoverage) {
        when(coverageAuditService.runAudit()).thenReturn(auditReport(List.of(
                new DataCoverageAuditService.SeasonCoverage(
                        seasonId, "Readiness Integration League", "2026", true,
                        10, 10, scoredFixtures, 10 - scoredFixtures, fixturesWithXg,
                        20, 10, 5, 5, 2, 10L, 0, 0, "OK",
                        List.of(),
                        List.of(new DataCoverageAuditService.PersistedResourceCoverage(
                                "TEAM_STATISTICS", 10, 20, teamStatisticsCoverage)),
                        List.of()
                )
        )));
    }

    private DataCoverageAuditService.DataCoverageAuditReport auditReport(
            List<DataCoverageAuditService.SeasonCoverage> seasons) {
        return new DataCoverageAuditService.DataCoverageAuditReport(
                Instant.parse("2026-11-10T12:00:00Z"),
                "OK",
                new DataCoverageAuditService.Totals(
                        1, 1, 0, seasons.size(), seasons.size(), 2, 0,
                        10, 10, 10, 0, 10, 0, 20, 10, 5, 5, 0, 0),
                new DataCoverageAuditService.ProviderCoverage(
                        "FOOTBALL_DATA", "FOOTBALL_DATA", "FOOTBALL_DATA",
                        "FOOTBALL_DATA", Map.of()),
                seasons,
                List.of()
        );
    }

    private void persistSnapshot(Instant fetchedAt) {
        persistSnapshot(fetchedAt, MatchProvider.FOOTBALL_DATA);
    }

    private void persistSnapshot(Instant fetchedAt, MatchProvider provider) {
        entityManager.persist(MatchStatisticsSourceSnapshot.builder()
                .match(match)
                .team(homeTeam)
                .provider(provider)
                .snapshotJson("{}")
                .fetchedAt(fetchedAt)
                .build());
        entityManager.flush();
    }
}
