package com.mmo.module.fb.content.api;

/** Read-only boundary exposed by content to publication workflows. */
public interface ApprovedContentReader {
    ApprovedContent requireApproved(Long draftId);

    record ApprovedContent(Long draftId, int version, String title) {
        public ApprovedContent {
            if (draftId == null || draftId <= 0) throw new IllegalArgumentException("draftId must be positive");
            if (version < 1) throw new IllegalArgumentException("version must be positive");
        }
    }
}
