package com.mmo.module.fb.ingest.application.port.out;

/** Imports player-level statistics for completed canonical matches. */
public interface PlayerStatisticsIngestionPort {
    void syncFinishedMatchPlayerStatistics();
}
