package com.mmo.module.fb.ingest.provider.model;

public record TeamQuery(Long leagueId, String season, String leagueIdentify) {
    public TeamQuery(Long leagueId, String season) {
        this(leagueId, season, null);
    }
}
