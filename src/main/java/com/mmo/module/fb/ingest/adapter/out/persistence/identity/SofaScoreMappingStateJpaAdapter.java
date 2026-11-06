package com.mmo.module.fb.ingest.adapter.out.persistence.identity;

import com.mmo.module.fb.domain.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.league.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.ingest.application.port.out.SofaScoreMappingStatePort;
import com.mmo.module.fb.ingest.enums.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** JPA adapter for SofaScore's provider mapping coverage checks. */
@Repository
@RequiredArgsConstructor
public class SofaScoreMappingStateJpaAdapter implements SofaScoreMappingStatePort {
    private static final Provider PROVIDER = Provider.SOFA_SCORE;
    private static final MatchProvider MATCH_PROVIDER = MatchProvider.fromName(PROVIDER.name());

    private final LeagueExternalIdRepository leagueRepository;
    private final SeasonExternalIdRepository seasonRepository;
    private final TeamExternalIdRepository teamRepository;
    private final MatchExternalIdRepository matchRepository;

    @Override public long countLeagues() { return leagueRepository.findAllByProvider(PROVIDER).size(); }
    @Override public long countSeasons() { return seasonRepository.findAllByProvider(PROVIDER).size(); }
    @Override public long countTeams() { return teamRepository.findAllByProvider(PROVIDER).size(); }
    @Override public long countFixtures() { return matchRepository.findAllByProvider(MATCH_PROVIDER).size(); }
    @Override public long countCurrentSeasonFixtures() { return matchRepository.countCurrentSeasonByProvider(MATCH_PROVIDER); }
    @Override public List<Long> currentSeasonIds() { return seasonRepository.findCurrentSeasonIdsByProvider(PROVIDER); }
    @Override public List<Long> mappedCurrentSeasonIds() { return matchRepository.findMappedCurrentSeasonIdsByProvider(MATCH_PROVIDER); }
}
