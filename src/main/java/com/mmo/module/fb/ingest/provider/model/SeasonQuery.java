package com.mmo.module.fb.ingest.provider.model;

/** Source-neutral query for the seasons available under one provider league. */
public record SeasonQuery(String leagueExternalId, String leagueIdentify) {
}
