package com.mmo.module.fb.ingest.application.port.out;

/** Provider-specific Core and enrichment workflow exposed to the application layer. */
public interface FootballDataUkIngestionPort {
    boolean syncInitialData();
    void syncCurrentSeasonData();
    boolean syncInitialEnrichment();
    void syncCurrentSeasonEnrichment();
}
