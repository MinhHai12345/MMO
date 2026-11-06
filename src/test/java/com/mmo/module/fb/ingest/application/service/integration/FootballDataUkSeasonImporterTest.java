package com.mmo.module.fb.ingest.application.service.integration;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void fingerprintsCsvRowsIndependentOfMapInsertionOrder() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("DATE", "01/08/26");
        first.put("HOME", "Arsenal");
        Map<String, String> second = new LinkedHashMap<>();
        second.put("HOME", "Arsenal");
        second.put("DATE", "01/08/26");

        assertEquals(
                FootballDataUkSeasonImporter.csvFingerprint(List.of(first)),
                FootballDataUkSeasonImporter.csvFingerprint(List.of(second)));
        assertNotEquals(
                FootballDataUkSeasonImporter.csvFingerprint(List.of(first)),
                FootballDataUkSeasonImporter.csvFingerprint(List.of(Map.of("DATE", "02/08/26", "HOME", "Arsenal"))));
    }

    @Test
    void validatesResumeCursorAgainstSourceFingerprintAndRowCount() {
        String fingerprint = "abc123";
        assertEquals(200, FootballDataUkSeasonImporter.parseCursorOffset(
                "abc123:200", fingerprint, 500));
        assertEquals(-1, FootballDataUkSeasonImporter.parseCursorOffset(
                "different:200", fingerprint, 500));
        assertEquals(-1, FootballDataUkSeasonImporter.parseCursorOffset(
                "abc123:501", fingerprint, 500));
        assertEquals(-1, FootballDataUkSeasonImporter.parseCursorOffset(
                "abc123:not-a-number", fingerprint, 500));
    }
}
