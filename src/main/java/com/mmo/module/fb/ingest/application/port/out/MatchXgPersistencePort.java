package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.ingest.enums.Provider;

import java.math.BigDecimal;

/** Outbound persistence port for source-aware fixture xG updates. */
public interface MatchXgPersistencePort {
    boolean upsert(Provider provider, Match match, String seasonYear, BigDecimal homeXg, BigDecimal awayXg);
}
