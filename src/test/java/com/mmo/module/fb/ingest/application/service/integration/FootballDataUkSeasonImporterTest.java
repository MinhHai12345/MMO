package com.mmo.module.fb.ingest.application.service.integration;

import org.junit.jupiter.api.Test;

import com.mmo.module.fb.domain.match.entity.Match;

import java.time.Instant;

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
    void matchesRepeatedHomeAwayPairByKickoffInsteadOfSeasonWideUniqueness() {
        Match novemberFixture = Match.builder()
                .matchTime(Instant.parse("2024-11-09T15:00:00Z"))
                .build();
        Match mayFixture = Match.builder()
                .matchTime(Instant.parse("2025-05-13T19:00:00Z"))
                .build();

        assertSame(novemberFixture, FootballDataUkSeasonImporter.closestFixtureCandidate(
                List.of(novemberFixture, mayFixture),
                Instant.parse("2024-11-09T15:00:00Z")).orElseThrow());
        assertSame(mayFixture, FootballDataUkSeasonImporter.closestFixtureCandidate(
                List.of(novemberFixture, mayFixture),
                Instant.parse("2025-05-13T19:00:00Z")).orElseThrow());
    }

    @Test
    void createsDifferentProviderFixtureIdentityForRepeatedPairOnDifferentDates() {
        String novemberFixture = FootballDataUkSeasonImporter.fixtureExternalId(
                "E1", "2024/2025", 1879L, 1883L, LocalDate.of(2024, 11, 9));
        String mayFixture = FootballDataUkSeasonImporter.fixtureExternalId(
                "E1", "2024/2025", 1879L, 1883L, LocalDate.of(2025, 5, 13));
        String reverseFixture = FootballDataUkSeasonImporter.fixtureExternalId(
                "E1", "2024/2025", 1883L, 1879L, LocalDate.of(2024, 11, 9));

        assertNotEquals(novemberFixture, mayFixture);
        assertNotEquals(novemberFixture, reverseFixture);
    }

    @Test
    void doesNotReuseSameTeamPairFixtureWhenKickoffIsOutsideMatchingWindow() {
        Match oldFixture = Match.builder()
                .matchTime(Instant.parse("2024-11-09T15:00:00Z"))
                .build();

        assertTrue(FootballDataUkSeasonImporter.closestFixtureCandidate(
                List.of(oldFixture), Instant.parse("2025-05-13T19:00:00Z")).isEmpty());
    }

    @Test
    void rejectsEquallyCloseFixtureCandidatesInsteadOfPickingArbitrarily() {
        Instant kickoff = Instant.parse("2024-11-09T15:00:00Z");
        Match first = Match.builder().matchTime(kickoff.minusSeconds(3600)).build();
        Match second = Match.builder().matchTime(kickoff.plusSeconds(3600)).build();

        FootballDataUkSeasonImporter.AmbiguousFixtureCandidateException exception =
                assertThrows(FootballDataUkSeasonImporter.AmbiguousFixtureCandidateException.class,
                        () -> FootballDataUkSeasonImporter.closestFixtureCandidate(
                                List.of(first, second), kickoff));

        assertTrue(exception.getMessage().contains("equally close"));
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
