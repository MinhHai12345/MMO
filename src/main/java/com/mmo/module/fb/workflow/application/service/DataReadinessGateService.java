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
        return evaluateAt(Instant.now());
    }

    ReadinessReport evaluateAt(Instant now) {
        validateConfiguration();
        DataCoverageAuditService.DataCoverageAuditReport audit = coverageAuditService.runAudit();
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
            if (latestSnapshot == null) {
                issues.add(season.current()
                        ? "STALE_OR_MISSING_STATISTICS_SNAPSHOT"
                        : "MISSING_STATISTICS_SNAPSHOT");
                fresh = false;
            } else if (latestSnapshot.isAfter(now)) {
                issues.add("FUTURE_STATISTICS_SNAPSHOT");
                fresh = false;
            } else if (season.current()
                    && latestSnapshot.isBefore(now.minus(Duration.ofHours(maximumSnapshotAgeHours)))) {
                issues.add("STALE_OR_MISSING_STATISTICS_SNAPSHOT");
                fresh = false;
            }

            results.add(new SeasonReadiness(season.seasonId(), season.league(), season.season(),
                    season.matches(), season.finishedMatches(), season.finishedWithScores(),
                    season.finishedWithXg(), teamStatsCoverage, latestSnapshot, fresh, issues));
        }
        List<String> reportIssues = results.isEmpty() ? List.of("NO_ACTIVE_SEASONS") : List.of();
        boolean ready = reportIssues.isEmpty()
                && results.stream().allMatch(item -> item.issues().isEmpty());
        log.info("Workflow data readiness evaluated: seasons={} ready={} generatedAt={} issues={}",
                results.size(), ready, now, reportIssues);
        return new ReadinessReport(ready, now, results, reportIssues);
    }

    private void validateConfiguration() {
        validateCoverageThreshold("minimum-score-coverage", minimumScoreCoverage);
        validateCoverageThreshold("minimum-xg-coverage", minimumXgCoverage);
        validateCoverageThreshold("minimum-team-statistics-coverage", minimumTeamStatisticsCoverage);
        if (maximumSnapshotAgeHours <= 0) {
            throw new IllegalStateException("Workflow readiness maximum-snapshot-age-hours must be greater than zero.");
        }
    }

    private void validateCoverageThreshold(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalStateException("Workflow readiness " + name + " must be between 0.0 and 1.0.");
        }
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    public record ReadinessReport(boolean ready, Instant evaluatedAt, List<SeasonReadiness> seasons,
                                  List<String> issues) {
        public ReadinessReport(boolean ready, Instant evaluatedAt, List<SeasonReadiness> seasons) {
            this(ready, evaluatedAt, seasons, List.of());
        }

        public ReadinessReport {
            seasons = seasons == null ? List.of() : List.copyOf(seasons);
            issues = issues == null ? List.of() : List.copyOf(issues);
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
