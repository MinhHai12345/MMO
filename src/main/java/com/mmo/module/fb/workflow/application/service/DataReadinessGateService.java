package com.mmo.module.fb.workflow.application.service;

import com.mmo.module.fb.analytics.service.DataCoverageAuditService;
import com.mmo.module.fb.domain.match.repository.MatchStatisticsSourceSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataReadinessGateService {
    private final DataCoverageAuditService coverageAuditService;
    private final MatchStatisticsSourceSnapshotRepository snapshotRepository;

    @Value("${mmo.workflow.readiness.minimum-score-coverage:0.95}")
    private double minimumScoreCoverage;
    @Value("${mmo.workflow.readiness.minimum-xg-coverage:0.80}")
    private double minimumXgCoverage;
    @Value("${mmo.workflow.readiness.minimum-team-statistics-coverage:0.80}")
    private double minimumTeamStatisticsCoverage;
    @Value("${mmo.workflow.readiness.maximum-snapshot-age-hours:72}")
    private long maximumSnapshotAgeHours;

    @Transactional(readOnly = true)
    public ReadinessReport evaluate() {
        DataCoverageAuditService.DataCoverageAuditReport audit = coverageAuditService.runAudit();
        Instant now = Instant.now();
        List<SeasonReadiness> results = new ArrayList<>();
        for (DataCoverageAuditService.SeasonCoverage season : audit.seasons()) {
            List<String> issues = new ArrayList<>(season.issues());
            double scoreCoverage = ratio(season.finishedWithScores(), season.finishedMatches());
            double xgCoverage = ratio(season.finishedWithXg(), season.finishedMatches());
            double teamStatsCoverage = season.persistedResources().stream()
                    .filter(resource -> "TEAM_STATISTICS".equals(resource.resource()))
                    .mapToDouble(DataCoverageAuditService.PersistedResourceCoverage::matchCoverageRatio)
                    .findFirst().orElse(0.0);

            if (season.matches() == 0) issues.add("NO_FIXTURES");
            if (scoreCoverage < minimumScoreCoverage) issues.add("INSUFFICIENT_SCORE_COVERAGE");
            if (xgCoverage < minimumXgCoverage) issues.add("INSUFFICIENT_XG_COVERAGE");
            if (teamStatsCoverage < minimumTeamStatisticsCoverage) {
                issues.add("INSUFFICIENT_TEAM_STATISTICS_COVERAGE");
            }

            Instant latestSnapshot = snapshotRepository.findLatestFetchedAtBySeasonId(season.seasonId());
            boolean fresh = latestSnapshot != null;
            if (season.current()) {
                fresh = fresh && !latestSnapshot.isBefore(now.minus(Duration.ofHours(maximumSnapshotAgeHours)));
                if (!fresh) issues.add("STALE_OR_MISSING_STATISTICS_SNAPSHOT");
            } else if (!fresh) {
                issues.add("MISSING_STATISTICS_SNAPSHOT");
            }

            results.add(new SeasonReadiness(season.seasonId(), season.league(), season.season(),
                    season.matches(), season.finishedMatches(), season.finishedWithScores(),
                    season.finishedWithXg(), teamStatsCoverage, latestSnapshot, fresh, issues));
        }
        boolean ready = !results.isEmpty() && results.stream().allMatch(item -> item.issues().isEmpty());
        log.info("Workflow data readiness evaluated: seasons={} ready={} generatedAt={}",
                results.size(), ready, now);
        return new ReadinessReport(ready, now, results);
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    public record ReadinessReport(boolean ready, Instant evaluatedAt, List<SeasonReadiness> seasons) {
        public ReadinessReport {
            seasons = seasons == null ? List.of() : List.copyOf(seasons);
        }
    }

    public record SeasonReadiness(Long seasonId, String league, String season,
                                  long fixtures, long finishedFixtures, long scoredFixtures,
                                  long fixturesWithXg, double teamStatisticsCoverage,
                                  Instant latestStatisticsSnapshotAt, boolean fresh,
                                  List<String> issues) {
        public SeasonReadiness {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }
}
