package com.mmo.module.fb.ingest.application.service.integration;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FootballDataUkSeasonImporterFixtureWindowTest {

    @Test
    void candidateWindowUsesSourceKickoffsInsteadOfSeasonMetadataBounds() {
        Instant kickoff = Instant.parse("2026-08-15T18:30:00Z");

        FootballDataUkSeasonImporter.FixtureCandidateWindow window =
                FootballDataUkSeasonImporter.fixtureCandidateWindow(List.of(kickoff));

        assertEquals(kickoff.minus(Duration.ofHours(36)), window.fromInclusive());
        assertEquals(kickoff.plus(Duration.ofHours(36)), window.toInclusive());
    }

    @Test
    void candidateWindowCoversTheCompleteBatchAndMatchingTolerance() {
        Instant firstKickoff = Instant.parse("2026-08-15T18:30:00Z");
        Instant lastKickoff = Instant.parse("2026-08-22T18:30:00Z");

        FootballDataUkSeasonImporter.FixtureCandidateWindow window =
                FootballDataUkSeasonImporter.fixtureCandidateWindow(
                        List.of(firstKickoff, lastKickoff));

        assertEquals(firstKickoff.minus(Duration.ofHours(36)), window.fromInclusive());
        assertEquals(lastKickoff.plus(Duration.ofHours(36)), window.toInclusive());
    }

    @Test
    void candidateWindowIsAbsentWhenBatchHasNoValidKickoffs() {
        assertNull(FootballDataUkSeasonImporter.fixtureCandidateWindow(List.of()));
        assertNull(FootballDataUkSeasonImporter.fixtureCandidateWindow(
                java.util.Arrays.<Instant>asList(null, null)));
    }
}
