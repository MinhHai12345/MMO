package com.mmo.module.fb.ingest.application.port.out;

/** Imports historical match/team metrics from the configured historical statistics provider. */
public interface HistoricalStatisticsIngestionPort {
    void syncActiveSeasons();
}
