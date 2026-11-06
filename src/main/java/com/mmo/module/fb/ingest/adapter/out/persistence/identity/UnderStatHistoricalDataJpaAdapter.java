package com.mmo.module.fb.ingest.adapter.out.persistence.identity;

import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchAdvancedStats;
import com.mmo.module.fb.domain.match.entity.MatchExternalId;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.repository.MatchAdvancedStatsRepository;
import com.mmo.module.fb.domain.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.team.entity.TeamExternalId;
import com.mmo.module.fb.domain.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.ingest.application.port.out.UnderStatHistoricalDataPort;
import com.mmo.module.fb.ingest.enums.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** JPA adapter for UnderStat's historical canonical-match and team-stat lookups. */
@Repository
@RequiredArgsConstructor
public class UnderStatHistoricalDataJpaAdapter implements UnderStatHistoricalDataPort {
    private final SeasonRepository seasonRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final MatchRepository matchRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final MatchAdvancedStatsRepository matchAdvancedStatsRepository;

    @Override
    public List<Season> findActiveSeasons() {
        return seasonRepository.findByIsActiveTrueAndLeagueIsNotNullAndYearIsNotNull();
    }

    @Override
    public Optional<String> findLeagueIdentify(Long leagueId, Provider provider) {
        return leagueExternalIdRepository.findByLeagueIdAndProvider(leagueId, provider)
                .map(mapping -> mapping.getIdentify());
    }

    @Override
    public Optional<Match> findMatch(Provider provider, String externalMatchId) {
        return matchExternalIdRepository.findByProviderAndExternalId(
                        MatchProvider.fromName(provider.name()), externalMatchId)
                .map(MatchExternalId::getMatch);
    }

    @Override
    public List<Match> findFixtureCandidates(Long seasonId, Instant fromInclusive, Instant toInclusive) {
        return matchRepository.findFixtureCandidatesBySeasonAndTime(seasonId, fromInclusive, toInclusive);
    }

    @Override
    public Optional<Team> findTeam(Provider provider, String externalTeamId) {
        return teamExternalIdRepository.findByProviderAndExternalId(provider, externalTeamId)
                .map(TeamExternalId::getTeam);
    }

    @Override
    public Optional<MatchAdvancedStats> findTeamStatistics(Long matchId, Long teamId) {
        return matchAdvancedStatsRepository.findByMatchIdAndTeamId(matchId, teamId);
    }

    @Override
    public Match saveMatch(Match match) {
        return matchRepository.save(match);
    }

    @Override
    public void saveTeamStatistics(MatchAdvancedStats statistics) {
        matchAdvancedStatsRepository.save(statistics);
    }
}
