package com.mmo.module.fb.ingest.application.port.in;

/**
 * Replays the supported ingestion pipeline in dependency order.
 *
 * <p>This use case is non-destructive: it does not drop schemas, delete canonical
 * entities, or clear provider checkpoints. Run it against an empty database for a
 * clean rebuild, or against an existing database when an idempotent re-sync is intended.</p>
 */
public interface FullDataRebuildUseCase {

    void rebuildAllData();
}
