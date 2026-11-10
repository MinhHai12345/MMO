package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.model.FieldMergeCommand;
import java.util.ArrayList;
import java.util.List;
import com.mmo.module.fb.ingest.application.port.out.MatchXgPersistencePort;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Persistence boundary for canonical fixture-level xG with field-level source ownership. */
@Repository
@RequiredArgsConstructor
public class MatchXgPersistenceAdapter implements MatchXgPersistencePort {

    private final MatchRepository matchRepository;
    private final DataSourcePolicy dataSourcePolicy;
    private final SourceAwareMergeAdapter sourceAwareMergeService;

    @Override
    @Transactional
    public boolean upsert(Provider provider, Match match, String seasonYear,
                          BigDecimal homeXg, BigDecimal awayXg) {
        if (match == null || seasonYear == null || seasonYear.isBlank()
                || (homeXg == null && awayXg == null)
                || !dataSourcePolicy.isAllowed(provider, Dataset.MATCH_XG, seasonYear)) return false;

        List<FieldMergeCommand> commands = new ArrayList<>(2);
        if (homeXg != null && homeXg.signum() >= 0) {
            commands.add(new FieldMergeCommand(match, provider, Dataset.MATCH_XG, "home_xg",
                    match.getHomeXG() != null, null, () -> match.setHomeXG(homeXg.doubleValue())));
        }
        if (awayXg != null && awayXg.signum() >= 0) {
            commands.add(new FieldMergeCommand(match, provider, Dataset.MATCH_XG, "away_xg",
                    match.getAwayXG() != null, null, () -> match.setAwayXG(awayXg.doubleValue())));
        }
        if (commands.isEmpty()) return false;
        boolean changed = sourceAwareMergeService.mergeAll(commands) > 0;
        if (changed) matchRepository.save(match);
        return changed;
    }
}
