package com.mmo.module.fb.ingest.service;

/** Application use case for enriching canonical matches with historical and player statistics. */
public interface MatchEnrichmentService {
    void enrichFinishedMatches();
}
