package com.mmo.module.fb.ingest.application.model.provider;

public record TeamQuery(Long leagueId, String season, String leagueIdentify) {
    public TeamQuery(Long leagueId, String season) {
        this(leagueId, season, null);
    }
}
