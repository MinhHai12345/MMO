package com.mmo.module.fb.analytics.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface PostMatchAnalysisService {

    PostMatchInsight analyze(Long matchId);

    record PostMatchInsight(
            Long matchId,
            Instant kickoff,
            String competition,
            String homeTeam,
            String awayTeam,
            Integer homeScore,
            Integer awayScore,
            BigDecimal homeXg,
            BigDecimal awayXg,
            String resultSummary,
            List<String> evidenceBasedFindings,
            List<String> editorialAngles,
            List<String> dataWarnings,
            PlayerAnalyticsService.PlayerMatchInsight playerAnalytics) {
        public PostMatchInsight {
            evidenceBasedFindings = evidenceBasedFindings == null ? List.of() : List.copyOf(evidenceBasedFindings);
            editorialAngles = editorialAngles == null ? List.of() : List.copyOf(editorialAngles);
            dataWarnings = dataWarnings == null ? List.of() : List.copyOf(dataWarnings);
        }
    }
}
