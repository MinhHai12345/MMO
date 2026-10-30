package com.mmo.module.fb.ingest.provider.model;

import java.time.LocalDate;

/**
 * Source-neutral fixture query. leagueId is the provider's external league ID;
 * leagueIdentify carries non-numeric fetch keys such as UnderStat's league slug.
 */
public record FixtureQuery(Long leagueId, String season, LocalDate from, LocalDate to, String leagueIdentify) {
    public FixtureQuery(Long leagueId, String season, LocalDate from, LocalDate to) {
        this(leagueId, season, from, to, null);
    }
}
