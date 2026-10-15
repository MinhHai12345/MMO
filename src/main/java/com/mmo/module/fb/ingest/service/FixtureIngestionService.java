package com.mmo.module.fb.ingest.service;

import com.mmo.module.fb.domain.enums.Provider;

import java.time.LocalDate;

public interface FixtureIngestionService {
    void syncCurrentSeasonFixtures(LocalDate from, LocalDate to);
    void syncFixturesForProvider(Provider selectedProvider, LocalDate from, LocalDate to);
}
