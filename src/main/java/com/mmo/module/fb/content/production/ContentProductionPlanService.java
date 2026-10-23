package com.mmo.module.fb.content.production;

import com.mmo.module.fb.content.service.ContentDraftService.DraftFormat;
import com.mmo.module.fb.content.provenance.ContentClaimProvenance;

import java.util.List;

public interface ContentProductionPlanService {
    ProductionPlan create(Long matchId, DraftFormat format);

    record ProductionPlan(
            Long matchId,
            DraftFormat format,
            String editorialStatus,
            String publicationReadiness,
            List<ContentClaimProvenance> claims,
            List<MediaCue> mediaCues,
            List<CaptionCue> captions,
            List<QualityIssue> qualityIssues,
            String humanReviewRequired) {
        public ProductionPlan {
            claims = claims == null ? List.of() : List.copyOf(claims);
            mediaCues = mediaCues == null ? List.of() : List.copyOf(mediaCues);
            captions = captions == null ? List.of() : List.copyOf(captions);
            qualityIssues = qualityIssues == null ? List.of() : List.copyOf(qualityIssues);
        }
    }

    record MediaCue(int sequence, String segment, String visualSuggestion,
                    String onScreenText, int suggestedDurationSeconds) {}

    record CaptionCue(int startSecond, int endSecond, String text) {}

    record QualityIssue(String severity, String code, String message) {}
}
