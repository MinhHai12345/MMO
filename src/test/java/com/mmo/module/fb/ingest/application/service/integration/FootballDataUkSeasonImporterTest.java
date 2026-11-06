package com.mmo.module.fb.ingest.application.service.integration;


import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FootballDataUkSeasonImporterTest {

    @Test
    void selectsCurrentFootballSeasonForIncrementalImport() {
        assertEquals(2026, FootballDataUkSeasonImporter.currentSeasonStartYear(
                LocalDate.of(2026, 11, 4)));
        assertEquals(2025, FootballDataUkSeasonImporter.currentSeasonStartYear(
                LocalDate.of(2026, 3, 31)));
        assertEquals(2026, FootballDataUkSeasonImporter.currentSeasonStartYear(
                LocalDate.of(2026, 7, 1)));
    }
}
