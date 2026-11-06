package com.mmo.module.fb.ingest.application.model;

import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;

import java.time.Instant;

/** One source-aware field mutation collected for a bounded persistence batch. */
public record FieldMergeCommand(
        Match match,
        Provider provider,
        Dataset dataset,
        String fieldKey,
        boolean currentValuePresent,
        Instant sourceUpdatedAt,
        Runnable applyValue) {
}
