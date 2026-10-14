package com.mmo.module.fb.ingest.provider.model;

import java.time.LocalDate;

/**
 * Source-neutral fixture query. leagueId is the provider's external league ID.
 */
public record FixtureQuery(Long leagueId, String season, LocalDate from, LocalDate to) {
}
