package com.mmo.module.fb.ingest.application.model.provider;

import java.time.LocalDate;

/** Source-neutral fixture query. */
public record FixtureQuery(Long leagueId, String season, LocalDate from, LocalDate to, String leagueIdentify) {
    public FixtureQuery(Long leagueId, String season, LocalDate from, LocalDate to) {
        this(leagueId, season, from, to, null);
    }
}
