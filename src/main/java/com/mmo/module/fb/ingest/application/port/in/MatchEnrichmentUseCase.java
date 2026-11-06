package com.mmo.module.fb.ingest.application.port.in;

/** Application use case for enriching canonical matches with historical and player statistics. */
public interface MatchEnrichmentUseCase {
    void enrichFinishedMatches();
}
