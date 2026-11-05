package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;

import java.time.Instant;

/** Outbound port for field-level, source-aware writes to canonical match data. */
public interface SourceAwareMergePort {
    boolean merge(Match match, Provider provider, Dataset dataset, String fieldKey,
                  boolean currentValuePresent, Instant sourceUpdatedAt, Runnable applyValue);
}
