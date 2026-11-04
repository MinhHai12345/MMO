package com.mmo.module.fb.ingest.application.port.out;

/** Enriches canonical matches with provider-neutral match statistics. */
public interface MatchStatisticsIngestionPort {
    void storeMatchStatistics();
}
