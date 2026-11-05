package com.mmo.module.fb.ingest.application.model;

public record SofaScoreExternalMappingResult(
        String provider,
        int leagueMappings,
        int seasonMappings,
        int teamMappings,
        int matchMappings
) {
}
