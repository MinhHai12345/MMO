package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.ingest.application.model.FieldMergeCommand;
import com.mmo.module.fb.ingest.application.port.out.MatchXgPersistencePort;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Batch persistence for canonical fixture-level xG with field-level source ownership. */
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
        return upsertAll(provider, List.of(new XgInput(match, seasonYear, homeXg, awayXg))) > 0;
    }

    @Override
    @Transactional
    public int upsertAll(Provider provider, List<XgInput> inputs) {
        if (provider == null || inputs == null || inputs.isEmpty()) return 0;
        List<FieldMergeCommand> commands = new ArrayList<>(inputs.size() * 2);
        Map<Long, Match> changedMatches = new LinkedHashMap<>();
        for (XgInput input : inputs) {
            if (input == null || input.match() == null || input.match().getId() == null
                    || input.seasonYear() == null || input.seasonYear().isBlank()
                    || (input.homeXg() == null && input.awayXg() == null)
                    || !dataSourcePolicy.isAllowed(provider, Dataset.MATCH_XG, input.seasonYear())) continue;
            Match match = input.match();
            if (input.homeXg() != null && input.homeXg().signum() >= 0) {
                commands.add(new FieldMergeCommand(match, provider, Dataset.MATCH_XG, "home_xg",
                        match.getHomeXG() != null, null, () -> {
                    match.setHomeXG(input.homeXg().doubleValue());
                    changedMatches.put(match.getId(), match);
                }));
            }
            if (input.awayXg() != null && input.awayXg().signum() >= 0) {
                commands.add(new FieldMergeCommand(match, provider, Dataset.MATCH_XG, "away_xg",
                        match.getAwayXG() != null, null, () -> {
                    match.setAwayXG(input.awayXg().doubleValue());
                    changedMatches.put(match.getId(), match);
                }));
            }
        }
        if (commands.isEmpty()) return 0;
        int applied = sourceAwareMergeService.mergeAll(commands);
        if (applied > 0 && !changedMatches.isEmpty()) {
            matchRepository.saveAll(changedMatches.values());
        }
        return applied;
    }
}
