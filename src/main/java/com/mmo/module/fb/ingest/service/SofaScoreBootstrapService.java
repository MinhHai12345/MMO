package com.mmo.module.fb.ingest.service;

/**
 * Coordinates the initial SofaScore import through the shared provider-neutral ingestion
 * and persistence pipeline.
 */
public interface SofaScoreBootstrapService {

    /**
     * Imports SofaScore leagues/seasons, current-season teams and fixtures when the local
     * provider mappings have not been initialized yet.
     */
    void syncInitialDataIfRequired();
}
