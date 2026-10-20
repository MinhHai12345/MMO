package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.AnalyticsDataReadinessService;
import com.mmo.module.fb.analytics.service.AnalyticsDataReadinessService.DataReadinessReport;
import com.mmo.module.fb.analytics.service.AnalyticsDataReadinessService.SeasonReadiness;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.domain.repository.MatchRepository.SeasonDataReadinessProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsDataReadinessServiceImpl implements AnalyticsDataReadinessService {

    private static final long MIN_GOALS_HISTORY = 10;
    private static final long MIN_EXPECTED_GOALS_HISTORY = 10;

    private final MatchRepository matchRepository;

    @Override
    @Transactional(readOnly = true)
    public DataReadinessReport getReadiness() {
        List<SeasonDataReadinessProjection> rows =
                matchRepository.findSeasonDataReadiness(MatchStatus.FINISHED);

        List<SeasonReadiness> breakdown = new ArrayList<>(rows.size());
        long totalMatches = 0;
        long finishedMatches = 0;
        long finishedWithScores = 0;
        long finishedWithExpectedGoals = 0;
        long upcomingMatches = 0;

        for (SeasonDataReadinessProjection row : rows) {
            long total = count(row.getTotalMatches());
            long finished = count(row.getFinishedMatches());
            long scored = count(row.getFinishedWithScores());
            long withXg = count(row.getFinishedWithExpectedGoals());
            long upcoming = count(row.getUpcomingMatches());

            List<String> issues = new ArrayList<>();
            if (scored < MIN_GOALS_HISTORY) {
                issues.add("Insufficient completed matches with valid scores; found " + scored
                        + ", minimum recommended is " + MIN_GOALS_HISTORY + ".");
            }
            if (withXg < MIN_EXPECTED_GOALS_HISTORY) {
                issues.add("Expected-goals model has limited history; found " + withXg
                        + " matches with xG for both teams, minimum recommended is "
                        + MIN_EXPECTED_GOALS_HISTORY + ".");
            }
            if (total == 0) {
                issues.add("No fixtures are stored for this season.");
            }

            breakdown.add(new SeasonReadiness(
                    row.getSeasonId(),
                    row.getLeagueName(),
                    row.getSeasonYear(),
                    total,
                    finished,
                    scored,
                    withXg,
                    upcoming,
                    ratio(scored, finished),
                    ratio(withXg, finished),
                    readinessLabel(scored, MIN_GOALS_HISTORY),
                    readinessLabel(withXg, MIN_EXPECTED_GOALS_HISTORY),
                    issues));

            totalMatches += total;
            finishedMatches += finished;
            finishedWithScores += scored;
            finishedWithExpectedGoals += withXg;
            upcomingMatches += upcoming;
        }

        return new DataReadinessReport(
                Instant.now(),
                breakdown.size(),
                totalMatches,
                finishedMatches,
                finishedWithScores,
                finishedWithExpectedGoals,
                upcomingMatches,
                breakdown);
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private String readinessLabel(long samples, long minimum) {
        if (samples == 0) {
            return "INSUFFICIENT_DATA";
        }
        if (samples < minimum) {
            return "LIMITED_HISTORY";
        }
        return "READY";
    }
}
