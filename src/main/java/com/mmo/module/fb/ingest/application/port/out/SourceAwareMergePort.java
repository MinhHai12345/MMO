package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.ingest.application.model.FieldMergeCommand;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;

import java.time.Instant;
import java.util.List;

public interface SourceAwareMergePort {
    boolean merge(Match match, Provider provider, Dataset dataset, String fieldKey,
                  boolean currentValuePresent, Instant sourceUpdatedAt, Runnable applyValue);

    /** Bounded bulk merge. Adapters may preload provenance and flush it with one write. */
    default int mergeAll(List<FieldMergeCommand> commands) {
        if (commands == null || commands.isEmpty()) return 0;
        int applied = 0;
        for (FieldMergeCommand command : commands) {
            if (command != null && merge(command.match(), command.provider(), command.dataset(),
                    command.fieldKey(), command.currentValuePresent(), command.sourceUpdatedAt(),
                    command.applyValue())) {
                applied++;
            }
        }
        return applied;
    }
}
