package com.mmo.module.fb.analytics.controller;

import com.mmo.module.fb.analytics.service.PreMatchAnalysisService;
import com.mmo.module.fb.analytics.service.PreMatchAnalysisService.PreMatchReport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only pre-match intelligence assembled from the football Core database. */
@RestController
@RequestMapping("/api/fb/analytics/matches")
@RequiredArgsConstructor
public class PreMatchAnalysisController {

    private final PreMatchAnalysisService preMatchAnalysisService;

    @GetMapping("/{matchId}/pre-match-analysis")
    public PreMatchReport getPreMatchAnalysis(@PathVariable Long matchId) {
        return preMatchAnalysisService.analyze(matchId);
    }
}
