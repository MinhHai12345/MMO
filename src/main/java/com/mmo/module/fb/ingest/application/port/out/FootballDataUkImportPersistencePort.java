package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.LeagueExternalId;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.entity.SeasonExternalId;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchExternalId;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.team.entity.TeamExternalId;
import com.mmo.module.fb.ingest.enums.Provider;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

/** Persistence boundary for the Football-Data.co.uk import workflow. */
public interface FootballDataUkImportPersistencePort {
    List<League> findAllLeagues();
    Optional<LeagueExternalId> findLeagueMapping(Long leagueId, Provider provider);
    Optional<LeagueExternalId> findLeagueMappingByExternalId(Provider provider, String externalId);
    List<LeagueExternalId> findLeagueMappings(Provider provider);
    LeagueExternalId saveLeagueMapping(LeagueExternalId mapping);

    Optional<Season> findSeasonById(Long seasonId);
    List<Season> findSeasonsByLeagueId(Long leagueId);
    Season saveSeason(Season season);
    Optional<SeasonExternalId> findSeasonMapping(Long seasonId, Provider provider);
    SeasonExternalId saveSeasonMapping(SeasonExternalId mapping);

    Optional<TeamExternalId> findTeamMapping(Provider provider, String externalId);
    Optional<TeamExternalId> findTeamMappingByTeamId(Long teamId, Provider provider);
    List<Team> findTeamsByName(String canonicalName);
    List<Team> findTeamsByNames(Collection<String> canonicalNames);
    List<Team> findAllTeams();
    List<TeamExternalId> findTeamMappings(Provider provider);
    Team saveTeam(Team team);
    void registerTeamExternalId(Team team, Provider provider, String externalId);

    Optional<MatchExternalId> findMatchMappingByProvider(MatchProvider provider, String externalId);
    List<MatchExternalId> findMatchMappingsByProviderAndExternalIds(MatchProvider provider, Collection<String> externalIds);
    List<MatchExternalId> findMatchMappingsByMatchIds(Collection<Long> matchIds, MatchProvider provider);
    Optional<MatchExternalId> findMatchMappingByMatchId(Long matchId, MatchProvider provider);
    MatchExternalId saveMatchMapping(MatchExternalId mapping);
    List<Match> findBatchFixtureCandidates(Long seasonId, Collection<Long> teamIds,
                                           Instant fromInclusive, Instant toExclusive);
    List<Match> saveMatches(Collection<Match> matches);
    List<MatchExternalId> saveMatchMappings(Collection<MatchExternalId> mappings);
    List<Match> findFixtureCandidates(Long seasonId, Long homeTeamId, Long awayTeamId,
                                      Instant fromInclusive, Instant toExclusive);
    Match saveMatch(Match match);
    void registerMatchExternalId(Match match, Provider provider, String externalId);
}
