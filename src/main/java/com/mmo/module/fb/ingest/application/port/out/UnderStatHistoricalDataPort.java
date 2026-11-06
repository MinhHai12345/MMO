package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchAdvancedStats;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.ingest.enums.Provider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence queries and writes required by UnderStat historical enrichment. */
public interface UnderStatHistoricalDataPort {
    List<Season> findActiveSeasons();
    Optional<String> findLeagueIdentify(Long leagueId, Provider provider);
    Optional<Match> findMatch(Provider provider, String externalMatchId);
    List<Match> findFixtureCandidates(Long seasonId, Instant fromInclusive, Instant toInclusive);
    Optional<Team> findTeam(Provider provider, String externalTeamId);
    Optional<MatchAdvancedStats> findTeamStatistics(Long matchId, Long teamId);
    Match saveMatch(Match match);
    void saveTeamStatistics(MatchAdvancedStats statistics);
}
