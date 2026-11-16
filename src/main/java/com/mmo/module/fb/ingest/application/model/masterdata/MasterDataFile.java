package com.mmo.module.fb.ingest.application.model.masterdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Curated canonical football master data and provider identity mappings. */
public record MasterDataFile(
        List<LeagueEntry> leagues,
        List<TeamEntry> teams) {

    public record LeagueEntry(
            String code,
            String name,
            String country,
            String logoUrl,
            Boolean active,
            BigDecimal popularityScore,
            List<SeasonEntry> seasons,
            Map<String, String> externalMappings,
            Map<String, String> externalIdentifies) { }

    public record SeasonEntry(
            String year,
            Boolean current,
            Boolean active,
            LocalDate startDate,
            LocalDate endDate,
            Map<String, String> externalMappings) { }

    public record TeamEntry(
            String code,
            String name,
            String shortName,
            String logoUrl,
            Boolean active,
            Map<String, String> externalMappings) { }
}
