package com.mmo.module.fb.analytics.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface DataCoverageAuditService {
    DataCoverageAuditReport runAudit();

    record DataCoverageAuditReport(Instant generatedAt, String status, Totals totals,
                                   ProviderCoverage providers, List<SeasonCoverage> seasons,
                                   List<AuditFinding> findings) {
        public DataCoverageAuditReport {
            seasons = seasons == null ? List.of() : List.copyOf(seasons);
            findings = findings == null ? List.of() : List.copyOf(findings);
        }
    }
    record Totals(long leagues, long activeLeagues, long leaguesWithoutSeasons, long seasons,
                  long currentSeasons, long teams, long teamsWithoutMatches, long matches,
                  long finishedMatches, long finishedWithScores, long finishedMissingScores,
                  long finishedWithXg, long finishedMissingXg, long standingsRows,
                  long totalStandingRows, long homeStandingRows, long awayStandingRows,
                  long duplicateFixtureGroups, long duplicateFixtureExcessRows) {}
    record ProviderCoverage(String leagueProvider, String teamProvider, String fixtureProvider,
                            String standingsProvider, Map<String, MappingCoverage> mappingCoverage) {
        public ProviderCoverage {
            mappingCoverage = mappingCoverage == null ? Map.of() : Map.copyOf(mappingCoverage);
        }
    }
    record MappingCoverage(long totalEntities, long mappedEntities, long missingMappings) {}
    record SeasonCoverage(Long seasonId, String league, String season, boolean current,
                          long matches, long finishedMatches, long finishedWithScores,
                          long finishedMissingScores, long finishedWithXg, long standingsRows,
                          long totalStandingRows, long homeStandingRows, long awayStandingRows,
                          long teamsInStandings, List<String> issues) {
        public SeasonCoverage {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }
    record AuditFinding(String severity, String code, String message, long affectedCount, String context) {}
}
