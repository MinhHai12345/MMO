package com.mmo.module.fb.ingest.application.service.masterdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.module.fb.ingest.application.model.masterdata.MasterDataFile;
import com.mmo.module.fb.ingest.application.model.masterdata.MasterDataReconciliationReport;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Compares the curated canonical seed with the persisted master-data snapshot.
 * Database-only records and mappings are intentionally retained and are not
 * considered errors because the seed is not a complete provider catalogue.
 */
@Service
@RequiredArgsConstructor
public class MasterDataReconciliationService {

    private static final String MASTER_DATA_PATH = "master-data/football-master-data.json";

    private final ObjectMapper objectMapper;
    private final MasterDataExportService exportService;

    @Transactional(readOnly = true)
    public MasterDataReconciliationReport reconcile() {
        MasterDataFile canonical = loadCanonical();
        MasterDataFile persisted = exportService.export();
        List<MasterDataReconciliationReport.Discrepancy> discrepancies = new ArrayList<>();

        Map<String, MasterDataFile.LeagueEntry> persistedLeagues = indexLeagues(persisted);
        Map<String, MasterDataFile.TeamEntry> persistedTeams = indexTeams(persisted);
        int canonicalSeasonCount = 0;
        int persistedSeasonCount = safe(persisted.leagues()).stream()
                .mapToInt(league -> safe(league.seasons()).size()).sum();
        int missingLeagues = 0;
        int missingSeasons = 0;
        int missingTeams = 0;
        int missingMappings = 0;
        int mappingConflicts = 0;
        int dateDiscrepancies = 0;

        for (MasterDataFile.LeagueEntry expectedLeague : safe(canonical.leagues())) {
            String leagueCode = normalize(expectedLeague.code());
            MasterDataFile.LeagueEntry actualLeague = persistedLeagues.get(leagueCode);
            if (actualLeague == null) {
                missingLeagues++;
                discrepancies.add(discrepancy("ERROR", "LEAGUE", expectedLeague.code(),
                        null, null, "existence", expectedLeague.name(), null,
                        "Canonical league is missing from the database."));
                canonicalSeasonCount += safe(expectedLeague.seasons()).size();
                continue;
            }

            int[] mappingCounts = compareMappings("LEAGUE", expectedLeague.code(), null,
                    expectedLeague.externalMappings(), actualLeague.externalMappings(), discrepancies);
            missingMappings += mappingCounts[0];
            mappingConflicts += mappingCounts[1];

            Map<String, MasterDataFile.SeasonEntry> actualSeasons = new HashMap<>();
            safe(actualLeague.seasons()).forEach(season ->
                    actualSeasons.put(normalize(season.year()), season));
            for (MasterDataFile.SeasonEntry expectedSeason : safe(expectedLeague.seasons())) {
                canonicalSeasonCount++;
                MasterDataFile.SeasonEntry actualSeason = actualSeasons.get(normalize(expectedSeason.year()));
                if (actualSeason == null) {
                    missingSeasons++;
                    discrepancies.add(discrepancy("ERROR", "SEASON", expectedLeague.code(),
                            expectedSeason.year(), null, "existence", expectedSeason.year(), null,
                            "Canonical season is missing from the database."));
                    continue;
                }

                if (!Objects.equals(expectedSeason.startDate(), actualSeason.startDate())) {
                    dateDiscrepancies++;
                    discrepancies.add(discrepancy("WARNING", "SEASON", expectedLeague.code(),
                            expectedSeason.year(), null, "startDate",
                            string(expectedSeason.startDate()), string(actualSeason.startDate()),
                            "Persisted start date differs from the curated canonical seed."));
                }
                if (!Objects.equals(expectedSeason.endDate(), actualSeason.endDate())) {
                    dateDiscrepancies++;
                    discrepancies.add(discrepancy("WARNING", "SEASON", expectedLeague.code(),
                            expectedSeason.year(), null, "endDate",
                            string(expectedSeason.endDate()), string(actualSeason.endDate()),
                            "Persisted end date differs from the curated canonical seed."));
                }

                mappingCounts = compareMappings("SEASON", expectedLeague.code(), expectedSeason.year(),
                        expectedSeason.externalMappings(), actualSeason.externalMappings(), discrepancies);
                missingMappings += mappingCounts[0];
                mappingConflicts += mappingCounts[1];
            }
        }

        for (MasterDataFile.TeamEntry expectedTeam : safe(canonical.teams())) {
            MasterDataFile.TeamEntry actualTeam = persistedTeams.get(normalize(expectedTeam.code()));
            if (actualTeam == null) {
                missingTeams++;
                discrepancies.add(discrepancy("ERROR", "TEAM", expectedTeam.code(),
                        null, null, "existence", expectedTeam.name(), null,
                        "Canonical team is missing from the database."));
                continue;
            }
            int[] mappingCounts = compareMappings("TEAM", expectedTeam.code(), null,
                    expectedTeam.externalMappings(), actualTeam.externalMappings(), discrepancies);
            missingMappings += mappingCounts[0];
            mappingConflicts += mappingCounts[1];
        }

        int canonicalSeasonTotal = safe(canonical.leagues()).stream()
                .mapToInt(league -> safe(league.seasons()).size()).sum();
        return new MasterDataReconciliationReport(
                discrepancies.stream().anyMatch(item -> "ERROR".equals(item.severity()))
                        ? "INCONSISTENT" : dateDiscrepancies > 0 ? "REVIEW_REQUIRED" : "CONSISTENT",
                safe(canonical.leagues()).size(), safe(persisted.leagues()).size(),
                canonicalSeasonTotal, persistedSeasonCount,
                safe(canonical.teams()).size(), safe(persisted.teams()).size(),
                missingLeagues, missingSeasons, missingTeams,
                missingMappings, mappingConflicts, dateDiscrepancies, List.copyOf(discrepancies));
    }

    private int[] compareMappings(
            String entityType,
            String code,
            String seasonYear,
            Map<String, String> expected,
            Map<String, String> actual,
            List<MasterDataReconciliationReport.Discrepancy> discrepancies) {
        int missing = 0;
        int conflicts = 0;
        if (expected == null) return new int[]{0, 0};
        Map<String, String> actualMappings = actual == null ? Map.of() : actual;
        for (Map.Entry<String, String> mapping : expected.entrySet()) {
            String provider = mapping.getKey();
            String expectedId = mapping.getValue();
            String actualId = actualMappings.get(provider);
            if (actualId == null || actualId.isBlank()) {
                missing++;
                discrepancies.add(discrepancy("ERROR", entityType, code, seasonYear,
                        provider, "externalMapping", expectedId, null,
                        "Expected provider mapping is not persisted."));
            } else if (!Objects.equals(expectedId, actualId)) {
                conflicts++;
                discrepancies.add(discrepancy("ERROR", entityType, code, seasonYear,
                        provider, "externalMapping", expectedId, actualId,
                        "Persisted provider mapping differs from the canonical seed."));
            }
        }
        return new int[]{missing, conflicts};
    }

    private Map<String, MasterDataFile.LeagueEntry> indexLeagues(MasterDataFile data) {
        Map<String, MasterDataFile.LeagueEntry> result = new HashMap<>();
        safe(data.leagues()).forEach(entry -> result.put(normalize(entry.code()), entry));
        return result;
    }

    private Map<String, MasterDataFile.TeamEntry> indexTeams(MasterDataFile data) {
        Map<String, MasterDataFile.TeamEntry> result = new HashMap<>();
        safe(data.teams()).forEach(entry -> result.put(normalize(entry.code()), entry));
        return result;
    }

    private MasterDataReconciliationReport.Discrepancy discrepancy(
            String severity, String entityType, String code, String seasonYear,
            String provider, String field, String expected, String actual, String message) {
        return new MasterDataReconciliationReport.Discrepancy(
                severity, entityType, code, seasonYear, provider, field, expected, actual, message);
    }

    private MasterDataFile loadCanonical() {
        try (var inputStream = new ClassPathResource(MASTER_DATA_PATH).getInputStream()) {
            MasterDataFile data = objectMapper.readValue(inputStream, MasterDataFile.class);
            if (data == null) throw new IllegalStateException("Canonical master data document is empty.");
            return data;
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read classpath:" + MASTER_DATA_PATH, ex);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
