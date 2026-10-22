package com.mmo.module.fb.analytics.service;

import java.util.List;

public interface ContentDraftService {

    ContentDraft generate(Long matchId, DraftFormat format);

    enum DraftFormat {
        PRE_MATCH,
        POST_MATCH,
        LONG_VIDEO,
        SHORTS
    }

    record ContentDraft(
            Long matchId,
            DraftFormat format,
            String status,
            String title,
            String openingHook,
            List<String> outline,
            List<String> shortHooks,
            List<String> evidence,
            List<String> caveats,
            QualityReview qualityReview) {
        public ContentDraft {
            outline = outline == null ? List.of() : List.copyOf(outline);
            shortHooks = shortHooks == null ? List.of() : List.copyOf(shortHooks);
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            caveats = caveats == null ? List.of() : List.copyOf(caveats);
        }
    }

    record QualityReview(
            String decision,
            int score,
            List<String> blockers,
            List<String> warnings) {
        public QualityReview {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
