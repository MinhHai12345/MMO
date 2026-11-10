package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.domain.match.entity.Match;

import java.util.Map;
import java.util.List;

/** Outbound persistence port for Football-Data.co.uk historical odds. */
public interface FootballDataUkOddsPersistencePort {
    int persist(Match match, String seasonYear, Map<String, String> row);
    int persistAll(List<OddsInput> inputs);

    record OddsInput(Match match, String seasonYear, Map<String, String> row) {}
}
