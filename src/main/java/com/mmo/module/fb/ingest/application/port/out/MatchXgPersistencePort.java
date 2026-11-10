package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.ingest.enums.Provider;

import java.math.BigDecimal;
import java.util.List;

/** Outbound persistence port for source-aware fixture xG updates. */
public interface MatchXgPersistencePort {
    boolean upsert(Provider provider, Match match, String seasonYear, BigDecimal homeXg, BigDecimal awayXg);
    int upsertAll(Provider provider, List<XgInput> inputs);

    record XgInput(Match match, String seasonYear, BigDecimal homeXg, BigDecimal awayXg) {}
}
