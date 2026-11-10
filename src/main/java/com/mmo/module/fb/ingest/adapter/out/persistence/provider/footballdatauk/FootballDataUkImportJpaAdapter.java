package com.mmo.module.fb.ingest.adapter.out.persistence.provider.footballdatauk;

import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.LeagueExternalId;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.entity.SeasonExternalId;
import com.mmo.module.fb.domain.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.league.repository.LeagueRepository;
import com.mmo.module.fb.domain.league.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchExternalId;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.team.entity.TeamExternalId;
import com.mmo.module.fb.domain.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.domain.team.repository.TeamRepository;
import com.mmo.module.fb.ingest.application.port.out.FootballDataUkImportPersistencePort;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.port.out.ExternalIdMappingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/** JPA implementation of the Football-Data.co.uk import persistence boundary. */
@Repository
@RequiredArgsConstructor
public class FootballDataUkImportJpaAdapter implements FootballDataUkImportPersistencePort {
    private final LeagueRepository leagueRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final SeasonRepository seasonRepository;
    private final SeasonExternalIdRepository seasonExternalIdRepository;
    private final TeamRepository teamRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final MatchRepository matchRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final ExternalIdMappingPort externalIdMappingService;

    @Override public List<League> findAllLeagues() { return leagueRepository.findAll(); }
    @Override public Optional<LeagueExternalId> findLeagueMapping(Long id, Provider provider) {
        return leagueExternalIdRepository.findByLeagueIdAndProvider(id, provider);
    }
    @Override public Optional<LeagueExternalId> findLeagueMappingByExternalId(Provider provider, String id) {
        return leagueExternalIdRepository.findByProviderAndExternalId(provider, id);
    }
    @Override public List<LeagueExternalId> findLeagueMappings(Provider provider) {
        return leagueExternalIdRepository.findAllByProvider(provider);
    }
    @Override public LeagueExternalId saveLeagueMapping(LeagueExternalId mapping) {
        return leagueExternalIdRepository.save(mapping);
    }

    @Override public Optional<Season> findSeasonById(Long id) { return seasonRepository.findById(id); }
    @Override public List<Season> findSeasonsByLeagueId(Long id) { return seasonRepository.findByLeague_Id(id); }
    @Override public Season saveSeason(Season season) { return seasonRepository.save(season); }
    @Override public Optional<SeasonExternalId> findSeasonMapping(Long id, Provider provider) {
        return seasonExternalIdRepository.findBySeasonIdAndProvider(id, provider);
    }
    @Override public SeasonExternalId saveSeasonMapping(SeasonExternalId mapping) {
        return seasonExternalIdRepository.save(mapping);
    }

    @Override public Optional<TeamExternalId> findTeamMapping(Provider provider, String id) {
        return teamExternalIdRepository.findByProviderAndExternalId(provider, id);
    }
    @Override public Optional<TeamExternalId> findTeamMappingByTeamId(Long id, Provider provider) {
        return teamExternalIdRepository.findByTeamIdAndProvider(id, provider);
    }
    @Override public List<Team> findTeamsByName(String name) {
        return teamRepository.findAllByNameIgnoreCase(name);
    }
    @Override public List<Team> findTeamsByNames(Collection<String> names) {
        return names == null || names.isEmpty() ? List.of()
                : teamRepository.findAllByNormalizedNameIn(names.stream().filter(java.util.Objects::nonNull)
                        .map(name -> name.toLowerCase(Locale.ROOT)).toList());
    }
    @Override public List<TeamExternalId> findTeamMappings(Provider provider) {
        return teamExternalIdRepository.findAllByProvider(provider);
    }
    @Override public Team saveTeam(Team team) { return teamRepository.save(team); }
    @Override public void registerTeamExternalId(Team team, Provider provider, String id) {
        externalIdMappingService.registerTeam(team, provider, id);
    }

    @Override public Optional<MatchExternalId> findMatchMappingByProvider(MatchProvider provider, String id) {
        return matchExternalIdRepository.findByProviderAndExternalId(provider, id);
    }
    @Override public List<MatchExternalId> findMatchMappingsByProviderAndExternalIds(
            MatchProvider provider, Collection<String> ids) {
        return ids == null || ids.isEmpty() ? List.of()
                : matchExternalIdRepository.findAllByProviderAndExternalIdIn(provider, List.copyOf(ids));
    }
    @Override public List<MatchExternalId> findMatchMappingsByMatchIds(
            Collection<Long> ids, MatchProvider provider) {
        return ids == null || ids.isEmpty() ? List.of()
                : matchExternalIdRepository.findAllByMatchIdInAndProvider(List.copyOf(ids), provider);
    }
    @Override public Optional<MatchExternalId> findMatchMappingByMatchId(Long id, MatchProvider provider) {
        return matchExternalIdRepository.findByMatchIdAndProvider(id, provider);
    }
    @Override public MatchExternalId saveMatchMapping(MatchExternalId mapping) {
        return matchExternalIdRepository.save(mapping);
    }
    @Override public List<Match> findBatchFixtureCandidates(Long seasonId, Collection<Long> teamIds,
                                                               Instant fromInclusive, Instant toExclusive) {
        return teamIds == null || teamIds.isEmpty() ? List.of()
                : matchRepository.findBatchFixtureCandidates(seasonId, List.copyOf(teamIds), fromInclusive, toExclusive);
    }
    @Override public List<Match> saveMatches(Collection<Match> matches) {
        return matches == null || matches.isEmpty() ? List.of() : matchRepository.saveAll(matches);
    }
    @Override public List<MatchExternalId> saveMatchMappings(Collection<MatchExternalId> mappings) {
        return mappings == null || mappings.isEmpty() ? List.of() : matchExternalIdRepository.saveAll(mappings);
    }
    @Override public List<Match> findFixtureCandidates(Long seasonId, Long homeId, Long awayId,
                                                       Instant fromInclusive, Instant toExclusive) {
        return matchRepository.findFixtureCandidates(seasonId, homeId, awayId, fromInclusive, toExclusive);
    }
    @Override public Match saveMatch(Match match) { return matchRepository.save(match); }
    @Override public void registerMatchExternalId(Match match, Provider provider, String id) {
        externalIdMappingService.registerMatch(match, provider, id);
    }
}
