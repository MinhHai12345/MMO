package com.mmo.module.fb.ingest.persistence;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Persistence boundary for the canonical fixture-level xG projection. */
@Service
@RequiredArgsConstructor
public class MatchXgPersistenceService {

    private final MatchRepository matchRepository;
    private final DataSourcePolicy dataSourcePolicy;

    @Transactional
    public boolean upsert(Provider provider, Match match, BigDecimal homeXg, BigDecimal awayXg) {
        if (match == null || match.getSeason() == null
                || !dataSourcePolicy.isPrimary(provider, Dataset.MATCH_XG, match.getSeason())
                || (homeXg == null && awayXg == null)) {
            return false;
        }

        if (homeXg != null) match.setHomeXG(homeXg.doubleValue());
        if (awayXg != null) match.setAwayXG(awayXg.doubleValue());
        matchRepository.save(match);
        return true;
    }
}
