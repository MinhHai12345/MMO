package com.mmo.module.fb.domain.match.repository;

import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.enums.MatchStatus;
import com.mmo.module.fb.domain.team.entity.Team;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "MMO_MYSQL_TEST_URL", matches = "^jdbc:mysql:.*$")
class MatchRepositoryMySqlIntegrationTest {

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long seasonId;
    private Long homeTeamId;
    private Long awayTeamId;

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnvironmentValue("MMO_MYSQL_TEST_URL"));
        registry.add("spring.datasource.username", () -> requiredEnvironmentValue("MMO_MYSQL_TEST_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredEnvironmentValue("MMO_MYSQL_TEST_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @BeforeEach
    void persistCanonicalFixtureContext() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            League league = League.builder()
                    .name("Fixture Lock Integration League")
                    .slug("fixture-lock-integration-league")
                    .country("Test")
                    .isActive(true)
                    .build();
            entityManager.persist(league);

            Season season = Season.builder()
                    .league(league)
                    .year("2026/2027")
                    .isCurrent(true)
                    .isActive(true)
                    .build();
            entityManager.persist(season);

            Team home = Team.builder().name("Fixture Lock Home").slug("fixture-lock-home").isActive(true).build();
            Team away = Team.builder().name("Fixture Lock Away").slug("fixture-lock-away").isActive(true).build();
            entityManager.persist(home);
            entityManager.persist(away);
            entityManager.flush();

            seasonId = season.getId();
            homeTeamId = home.getId();
            awayTeamId = away.getId();
        });
    }

    @Test
    void lockingCandidateReadSeesFixtureCommittedAfterRepeatableReadSnapshot() throws Exception {
        Instant kickoff = Instant.parse("2026-08-15T18:30:00Z");
        CountDownLatch snapshotEstablished = new CountDownLatch(1);
        CountDownLatch fixtureCommitted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<List<Match>> candidates = executor.submit(() ->
                    transactionTemplate().execute(status -> {
                        // Establish the transaction's REPEATABLE_READ snapshot before the other
                        // transaction commits the fixture.
                        matchRepository.count();
                        snapshotEstablished.countDown();
                        await(fixtureCommitted);
                        return matchRepository.lockBatchFixtureCandidates(
                                seasonId, List.of(homeTeamId, awayTeamId),
                                kickoff.minusSeconds(3600), kickoff.plusSeconds(3600));
                    }));

            assertTrue(snapshotEstablished.await(10, TimeUnit.SECONDS),
                    "Reader transaction should establish its snapshot");

            transactionTemplate().executeWithoutResult(status -> {
                Team home = entityManager.getReference(Team.class, homeTeamId);
                Team away = entityManager.getReference(Team.class, awayTeamId);
                Season season = entityManager.getReference(Season.class, seasonId);
                entityManager.persist(Match.builder()
                        .season(season)
                        .homeTeam(home)
                        .awayTeam(away)
                        .matchTime(kickoff)
                        .status(MatchStatus.SCHEDULED)
                        .build());
                entityManager.flush();
            });
            fixtureCommitted.countDown();

            List<Match> found = candidates.get(15, TimeUnit.SECONDS);
            assertEquals(1, found.size(),
                    "PESSIMISTIC_WRITE candidate lookup must see the newly committed canonical fixture");
            assertEquals(seasonId, found.get(0).getSeason().getId());
            assertEquals(homeTeamId, found.get(0).getHomeTeam().getId());
            assertEquals(awayTeamId, found.get(0).getAwayTeam().getId());
        } finally {
            fixtureCommitted.countDown();
            executor.shutdownNow();
        }
    }

    private TransactionTemplate transactionTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return template;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for fixture commit");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for fixture commit", exception);
        }
    }

    private static String requiredEnvironmentValue(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required MySQL integration-test environment variable is missing: " + name);
        }
        return value;
    }
}
