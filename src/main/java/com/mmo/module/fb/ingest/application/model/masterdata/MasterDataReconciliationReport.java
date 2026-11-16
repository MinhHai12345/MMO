package com.mmo.module.fb.ingest.application.model.masterdata;

import java.util.List;

public record MasterDataReconciliationReport(
        String status,
        int canonicalLeagues,
        int persistedLeagues,
        int canonicalSeasons,
        int persistedSeasons,
        int canonicalTeams,
        int persistedTeams,
        int missingLeagues,
        int missingSeasons,
        int missingTeams,
        int missingMappings,
        int mappingConflicts,
        int dateDiscrepancies,
        List<Discrepancy> discrepancies) {

    public boolean consistent() {
        return discrepancies == null || discrepancies.stream()
                .noneMatch(item -> "ERROR".equals(item.severity()));
    }

    public record Discrepancy(
            String severity,
            String entityType,
            String canonicalCode,
            String seasonYear,
            String provider,
            String field,
            String expected,
            String actual,
            String message) { }
}
