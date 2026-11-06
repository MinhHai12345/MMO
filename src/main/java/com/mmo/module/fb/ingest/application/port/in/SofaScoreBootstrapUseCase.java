package com.mmo.module.fb.ingest.application.port.in;

/**
 * Coordinates the initial SofaScore import through the shared provider-neutral ingestion
 * and persistence pipeline.
 */
public interface SofaScoreBootstrapUseCase {

    /**
     * Imports SofaScore leagues/seasons, current-season teams and fixtures when the local
     * provider mappings have not been initialized yet.
     */
    void syncInitialDataIfRequired();
}
