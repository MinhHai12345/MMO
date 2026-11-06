package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.port.out.MatchXgPersistencePort;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Persistence boundary for canonical fixture-level xG with field-level source ownership. */
@Service
@RequiredArgsConstructor
public class MatchXgPersistenceAdapter implements MatchXgPersistencePort {

    private final MatchRepository matchRepository;
    private final DataSourcePolicy dataSourcePolicy;
    private final SourceAwareMergeAdapter sourceAwareMergeService;

    @Override
    @Transactional
    public boolean upsert(Provider provider, Match match, BigDecimal homeXg, BigDecimal awayXg) {
        if (match == null || match.getSeason() == null || (homeXg == null && awayXg == null)
                || !dataSourcePolicy.isAllowed(provider, Dataset.MATCH_XG, match.getSeason())) return false;

        boolean changed = false;
        if (homeXg != null && homeXg.signum() >= 0) {
            changed |= sourceAwareMergeService.merge(match, provider, Dataset.MATCH_XG, "home_xg",
                    match.getHomeXG() != null, null, () -> match.setHomeXG(homeXg.doubleValue()));
        }
        if (awayXg != null && awayXg.signum() >= 0) {
            changed |= sourceAwareMergeService.merge(match, provider, Dataset.MATCH_XG, "away_xg",
                    match.getAwayXG() != null, null, () -> match.setAwayXG(awayXg.doubleValue()));
        }
        if (changed) matchRepository.save(match);
        return changed;
    }
}
