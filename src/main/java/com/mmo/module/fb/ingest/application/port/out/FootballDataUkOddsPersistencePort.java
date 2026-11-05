package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.domain.match.entity.Match;

import java.util.Map;

/** Outbound persistence port for Football-Data.co.uk historical odds. */
public interface FootballDataUkOddsPersistencePort {
    int persist(Match match, Map<String, String> row);
}
