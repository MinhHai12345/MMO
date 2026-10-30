package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.MmoProviderDataStandardizer;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderstatDtos;
import com.mmo.module.fb.ingest.service.ExternalIdMappingService;
import com.mmo.module.fb.league.entity.League;
import com.mmo.module.fb.league.entity.LeagueExternalId;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.league.repository.LeagueRepository;
import com.mmo.module.fb.league.repository.SeasonRepository;
import com.mmo.module.fb.player.entity.Player;
import com.mmo.module.fb.player.repository.PlayerExternalIdRepository;
import com.mmo.module.fb.player.repository.PlayerRepository;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Persists Understat catalogue data into MMO's canonical league, team and player tables. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnderstatCatalogPersistenceService {
    private static final Provider PROVIDER = Provider.UNDER_STATS;

    private final LeagueRepository leagueRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final PlayerRepository playerRepository;
    private final PlayerExternalIdRepository playerExternalIdRepository;
    private final ExternalIdMappingService externalIdMappingService;

    @Transactional
    public CatalogSyncResult persist(String leagueKey, String leagueName, String country,
                                     int seasonYear, UnderstatDtos.LeagueData data) {
        if (data == null) return new CatalogSyncResult(0, 0, 0);
        League league = resolveLeague(leagueKey, leagueName, country);
        Season season = seasonRepository.findByLeague_IdAndYear(league.getId(), String.valueOf(seasonYear))
                .orElseGet(() -> {
                    boolean current = seasonYear == Year.now().getValue();
                    return seasonRepository.save(Season.builder()
                            .league(league).year(String.valueOf(seasonYear))
                            .isCurrent(current).isActive(current).build());
                });

        Map<String, Team> teamsByTitle = new HashMap<>();
        int teamsSaved = 0;
        if (data.teams() != null) {
            for (Map.Entry<String, UnderstatDtos.LeagueTeam> entry : data.teams().entrySet()) {
                UnderstatDtos.LeagueTeam source = entry.getValue();
                if (source == null) continue;
                String externalId = firstText(source.id(), entry.getKey());
                String name = MmoProviderDataStandardizer.teamName(source.title());
                if (!StringUtils.hasText(externalId) || !StringUtils.hasText(name)) continue;
                Team team = resolveTeam(externalId, name);
                teamsByTitle.put(key(name), team);
                teamsSaved++;
            }
        }

        int playersSaved = 0;
        if (data.players() != null) {
            for (UnderstatDtos.LeaguePlayer source : data.players()) {
                if (source == null || !StringUtils.hasText(source.id())
                        || !StringUtils.hasText(source.playerName())) continue;
                String name = MmoProviderDataStandardizer.displayText(source.playerName());
                Team team = StringUtils.hasText(source.teamTitle())
                        ? teamsByTitle.get(key(source.teamTitle())) : null;
                Player player = resolvePlayer(source.id(), name, source.position(), team);
                if (player != null) playersSaved++;
            }
        }
        return new CatalogSyncResult(1, teamsSaved, playersSaved);
    }

    private League resolveLeague(String leagueKey, String leagueName, String country) {
        LeagueExternalId mapped = leagueExternalIdRepository
                .findByProviderAndExternalId(PROVIDER, leagueKey).orElse(null);
        if (mapped != null) {
            League league = mapped.getLeague();
            mapped.setIdentify(leagueKey);
            leagueExternalIdRepository.save(mapped);
            return league;
        }

        String standardizedName = MmoProviderDataStandardizer.leagueName(leagueName);
        List<League> named = leagueRepository.findAllByNameIgnoreCase(standardizedName);
        League league = named.size() == 1 ? named.get(0) : null;
        if (league != null && leagueExternalIdRepository
                .findByLeagueIdAndProvider(league.getId(), PROVIDER).isPresent()) {
            league = null;
        }
        if (league == null) {
            league = new League();
            league.setName(standardizedName);
            league.setSlug("understat-" + slug(leagueKey));
            league.setIsActive(false);
            league.setCountry(MmoProviderDataStandardizer.countryName(country));
            league = leagueRepository.save(league);
        }
        LeagueExternalId mapping = externalIdMappingService.registerLeague(league, PROVIDER, leagueKey);
        mapping.setIdentify(leagueKey);
        leagueExternalIdRepository.save(mapping);
        return league;
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
            if (candidates.size() == 1) {
                Player candidate = candidates.get(0);
                if (playerExternalIdRepository.findByPlayerIdAndProvider(candidate.getId(), PROVIDER).isEmpty()) {
                    player = candidate;
                }
            }
        }
        if (player == null) {
            player = Player.builder().name(name).team(team).position(MmoProviderDataStandardizer.displayText(position))
                    .isActive(true).build();
            player = playerRepository.save(player);
        } else {
            if (team != null) player.setTeam(team);
            player.setName(name);
            if (StringUtils.hasText(position)) {
                player.setPosition(MmoProviderDataStandardizer.displayText(position));
            }
            player.setIsActive(true);
            player = playerRepository.save(player);
        }
        externalIdMappingService.registerPlayer(player, PROVIDER, externalId);
        return player;
    }

    private static String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback == null ? null : fallback.trim();
    }

    private static String key(String value) {
        return MmoProviderDataStandardizer.displayText(value).toLowerCase(Locale.ROOT);
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    public record CatalogSyncResult(int leagues, int teams, int players) {}
}
