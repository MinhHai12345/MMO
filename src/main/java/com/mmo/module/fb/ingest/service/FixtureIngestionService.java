package com.mmo.module.fb.ingest.service;

public interface FixtureIngestionService {
    void syncCurrentSeasonFixtures(java.time.LocalDate from, java.time.LocalDate to);
    void syncFixturesForProvider(com.mmo.module.fb.domain.enums.Provider selectedProvider, java.time.LocalDate from, java.time.LocalDate to);
}
