package com.mmo.module.fb.ingest.policy;

/** Logical datasets whose write ownership is resolved independently per season. */
public enum Dataset {
    LEAGUE,
    SEASON,
    TEAM,
    FIXTURE,
    STANDINGS,
    MATCH_STATISTICS,
    PLAYER_STATISTICS,
    MATCH_XG,
    HISTORICAL_ODDS,
    UPCOMING_ODDS
}
