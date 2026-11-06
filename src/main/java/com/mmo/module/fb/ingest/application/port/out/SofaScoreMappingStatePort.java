package com.mmo.module.fb.ingest.application.port.out;

import java.util.List;

/** Read-only mapping coverage queries used by SofaScore bootstrap and mapping workflows. */
public interface SofaScoreMappingStatePort {
    long countLeagues();
    long countSeasons();
    long countTeams();
    long countFixtures();
    long countCurrentSeasonFixtures();
    List<Long> currentSeasonIds();
    List<Long> mappedCurrentSeasonIds();
}
