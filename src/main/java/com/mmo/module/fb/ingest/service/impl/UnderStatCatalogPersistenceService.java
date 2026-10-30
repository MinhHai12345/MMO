package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.model.ProviderPlayerSeasonStats;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderStatDtos;
import com.mmo.module.fb.ingest.provider.understats.mapper.UnderStatMapper;
import com.mmo.module.fb.ingest.service.ExternalIdMappingService;
import com.mmo.module.fb.league.entity.League;
import com.mmo.module.fb.league.entity.LeagueExternalId;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.league.repository.LeagueRepository;
import com.mmo.module.fb.league.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.league.repository.SeasonRepository;
import com.mmo.module.fb.player.entity.Player;
import com.mmo.module.fb.player.entity.PlayerSeasonStats;
import com.mmo.module.fb.player.repository.PlayerExternalIdRepository;
import com.mmo.module.fb.player.repository.PlayerRepository;
import com.mmo.module.fb.player.repository.PlayerSeasonStatsRepository;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persists UnderStat league-season catalogue data into MMO's league, team and player tables. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnderStatCatalogPersistenceService {
    private static final Provider PROVIDER = Provider.UNDER_STATS;

    private final LeagueRepository leagueRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final SeasonRepository seasonRepository;
    private final SeasonExternalIdRepository seasonExternalIdRepository;
    private final TeamRepository teamRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final PlayerRepository playerRepository;
    private final PlayerSeasonStatsRepository playerSeasonStatsRepository;
    private final PlayerExternalIdRepository playerExternalIdRepository;
    private final ExternalIdMappingService externalIdMappingService;
    private final UnderStatMapper mapper;

    @Transactional
    public CatalogSyncResult persist(
            ProviderLeague sourceLeague,
            ProviderSeason sourceSeason,
            UnderStatDtos.LeagueData data) {
        if (sourceLeague == null || sourceSeason == null || data == null) {
            return new CatalogSyncResult(0, 0, 0);
        }

        League league = resolveLeague(sourceLeague);
        Season season = resolveSeason(league, sourceSeason);

        Map<String, Team> teamsByTitle = new HashMap<>();
        int teamsSaved = 0;
        if (data.teams() != null) {
            for (Map.Entry<String, UnderStatDtos.LeagueTeam> entry : data.teams().entrySet()) {
                UnderStatDtos.LeagueTeam source = entry.getValue();
                if (source == null) continue;
                String externalId = StringUtils.hasText(source.id()) ? source.id().trim() : entry.getKey();
                String name = mapper.teamName(source.title());
                if (!StringUtils.hasText(externalId) || !StringUtils.hasText(name)) continue;

                Team team = resolveTeam(externalId, name);
                teamsByTitle.put(mapper.teamLookupKey(name), team);
                teamsSaved++;
            }
        }

        int playersSaved = 0;
        if (data.players() != null) {
            for (UnderStatDtos.LeaguePlayer source : data.players()) {
                if (source == null || !StringUtils.hasText(source.id())
                        || !StringUtils.hasText(source.playerName())) continue;
                ProviderPlayerSeasonStats stats = mapper.toPlayerSeasonStats(source);
                if (stats == null) continue;
                Team team = StringUtils.hasText(stats.teamName())
                        ? teamsByTitle.get(mapper.teamLookupKey(stats.teamName())) : null;
                Player player = resolvePlayer(stats.externalPlayerId(), stats.playerName(), stats.position(), team);
                persistPlayerSeasonStats(player, season, team, stats);
                playersSaved++;
            }
        }

        return new CatalogSyncResult(1, teamsSaved, playersSaved);
    }

    private League resolveLeague(ProviderLeague source) {
        LeagueExternalId mapped = leagueExternalIdRepository
                .findByProviderAndExternalId(PROVIDER, source.externalId()).orElse(null);
        if (mapped != null) {
            mapped.setIdentify(source.identify());
            leagueExternalIdRepository.save(mapped);
            return mapped.getLeague();
        }

        String standardizedName = mapper.leagueName(source.name());
        List<League> named = leagueRepository.findAllByNameIgnoreCase(standardizedName);
        League league = named.size() == 1 ? named.get(0) : null;
        if (league != null && leagueExternalIdRepository
                .findByLeagueIdAndProvider(league.getId(), PROVIDER).isPresent()) {
            league = null;
        }

        if (league == null) {
            league = new League();
            league.setName(standardizedName);
            league.setSlug("understat-" + slug(source.identify()));
            league.setIsActive(false);
            league.setCountry(source.country());
            league = leagueRepository.save(league);
        } else if (!StringUtils.hasText(league.getCountry()) && StringUtils.hasText(source.country())) {
            league.setCountry(source.country());
            league = leagueRepository.save(league);
        }

        LeagueExternalId mapping = externalIdMappingService.registerLeague(league, PROVIDER, source.externalId());
        mapping.setIdentify(source.identify());
        leagueExternalIdRepository.save(mapping);
        return league;
    }

    private Season resolveSeason(League league, ProviderSeason source) {
        Season season = seasonExternalIdRepository.findByProviderAndExternalId(PROVIDER, source.externalId())
                .map(com.mmo.module.fb.league.entity.SeasonExternalId::getSeason)
                .orElse(null);

        if (season != null && (season.getLeague() == null
                || !league.getId().equals(season.getLeague().getId())
                || !source.year().equals(season.getYear()))) {
            throw new IllegalStateException("UnderStat season ID " + source.externalId()
                    + " is already mapped to a different canonical season");
        }

        if (season == null) {
            season = seasonRepository.findByLeague_IdAndYear(league.getId(), source.year()).orElse(null);
            if (season == null) {
                season = Season.builder()
                        .league(league)
                        .year(source.year())
                        .isCurrent(source.current())
                        .isActive(source.current())
                        .startDate(source.startDate())
                        .endDate(source.endDate())
                        .build();
                season = seasonRepository.save(season);
            }
        }

        var existingMapping = seasonExternalIdRepository.findBySeasonIdAndProvider(season.getId(), PROVIDER)
                .orElse(null);
        if (existingMapping != null && !source.externalId().equals(existingMapping.getExternalId())) {
            throw new IllegalStateException("Canonical season " + season.getId()
                    + " is already mapped to a different UnderStat season ID");
        }
        externalIdMappingService.registerSeason(season, PROVIDER, source.externalId());
        return season;
    }

    private Team resolveTeam(String externalId, String name) {
        Team mapped = teamExternalIdRepository.findByProviderAndExternalId(PROVIDER, externalId)
                .map(com.mmo.module.fb.team.entity.TeamExternalId::getTeam).orElse(null);
        if (mapped != null) return mapped;

        List<Team> named = teamRepository.findAllByNameIgnoreCase(name);
        Team team = named.size() == 1 ? named.get(0) : null;
        if (team != null && teamExternalIdRepository.findByTeamIdAndProvider(team.getId(), PROVIDER).isPresent()) {
            team = null;
        }
        if (team == null) {
            team = new Team();
            team.setName(name);
            team.setShortName(name);
            team.setSlug("understat-" + slug(name) + "-" + externalId);
            team.setIsActive(true);
            team = teamRepository.save(team);
        }
        externalIdMappingService.registerTeam(team, PROVIDER, externalId);
        return team;
    }

    private Player resolvePlayer(String externalId, String name, String position, Team team) {
        Player player = playerExternalIdRepository.findByProviderAndExternalId(PROVIDER, externalId)
                .map(com.mmo.module.fb.player.entity.PlayerExternalId::getPlayer).orElse(null);
        if (player == null && team != null) {
            List<Player> candidates = playerRepository.findAllByNameIgnoreCaseAndTeam_Id(name, team.getId());
            if (candidates.size() == 1 && playerExternalIdRepository
                    .findByPlayerIdAndProvider(candidates.get(0).getId(), PROVIDER).isEmpty()) {
                player = candidates.get(0);
            }
        }
        if (player == null) {
            player = Player.builder().name(name).team(team).position(mapper.position(position))
                    .isActive(true).build();
            player = playerRepository.save(player);
        } else {
            if (team != null) player.setTeam(team);
            player.setName(name);
            if (StringUtils.hasText(position)) player.setPosition(mapper.position(position));
            player.setIsActive(true);
            player = playerRepository.save(player);
        }
        externalIdMappingService.registerPlayer(player, PROVIDER, externalId);
        return player;
    }

    private void persistPlayerSeasonStats(
            Player player, Season season, Team team, ProviderPlayerSeasonStats source) {
        PlayerSeasonStats stats = playerSeasonStatsRepository
                .findByPlayerIdAndSeasonIdAndProvider(player.getId(), season.getId(), PROVIDER)
                .orElseGet(() -> PlayerSeasonStats.builder()
                        .player(player).season(season).provider(PROVIDER).build());
        if (team != null) stats.setTeam(team);
        if (source.appearances() != null) stats.setAppearances(source.appearances());
        if (source.minutesPlayed() != null) stats.setMinutesPlayed(source.minutesPlayed());
        if (source.goals() != null) stats.setGoals(source.goals());
        if (source.xg() != null) stats.setXg(source.xg());
        if (source.assists() != null) stats.setAssists(source.assists());
        if (source.xa() != null) stats.setXa(source.xa());
        if (source.shots() != null) stats.setShots(source.shots());
        if (source.keyPasses() != null) stats.setKeyPasses(source.keyPasses());
        if (source.yellowCards() != null) stats.setYellowCards(source.yellowCards());
        if (source.redCards() != null) stats.setRedCards(source.redCards());
        if (source.nonPenaltyGoals() != null) stats.setNonPenaltyGoals(source.nonPenaltyGoals());
        if (source.npxg() != null) stats.setNpxg(source.npxg());
        if (source.xgChain() != null) stats.setXgChain(source.xgChain());
        if (source.xgBuildup() != null) stats.setXgBuildup(source.xgBuildup());
        playerSeasonStatsRepository.save(stats);
    }

    private static String slug(String value) {
        if (!StringUtils.hasText(value)) return "unknown";
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    public record CatalogSyncResult(int leagues, int teams, int players) { }
}
