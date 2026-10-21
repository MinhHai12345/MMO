package com.mmo.module.fb.domain.service;

import com.mmo.module.fb.domain.entity.League;
import com.mmo.module.fb.domain.entity.LeagueExternalId;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchExternalId;
import com.mmo.module.fb.domain.entity.Player;
import com.mmo.module.fb.domain.entity.PlayerExternalId;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.SeasonExternalId;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.entity.TeamExternalId;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.repository.PlayerExternalIdRepository;
import com.mmo.module.fb.domain.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.domain.repository.TeamExternalIdRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Provider-aware identity lookup and registration for football domain entities.
 *
 * <p>Provider IDs for leagues, seasons, teams, matches, and players are stored
 * only in mapping tables. Legacy entity-level IDs cannot identify their source
 * provider. Existing mappings are never silently reassigned to another entity.</p>
 */
@Service
@Transactional
public class ExternalIdMappingService {

    private final LeagueExternalIdRepository leagueExternalIds;
    private final SeasonExternalIdRepository seasonExternalIds;
    private final TeamExternalIdRepository teamExternalIds;
    private final MatchExternalIdRepository matchExternalIds;
    private final PlayerExternalIdRepository playerExternalIds;

    public ExternalIdMappingService(LeagueExternalIdRepository leagueExternalIds,
                                    SeasonExternalIdRepository seasonExternalIds,
                                    TeamExternalIdRepository teamExternalIds,
                                    MatchExternalIdRepository matchExternalIds,
                                    PlayerExternalIdRepository playerExternalIds) {
        this.leagueExternalIds = leagueExternalIds;
        this.seasonExternalIds = seasonExternalIds;
        this.teamExternalIds = teamExternalIds;
        this.matchExternalIds = matchExternalIds;
        this.playerExternalIds = playerExternalIds;
    }

    @Transactional(readOnly = true)
    public Optional<League> findLeague(Provider provider, String externalId) {
        requireIdentity(provider, externalId);
        return leagueExternalIds.findByProviderAndExternalId(provider, externalId.trim())
                .map(LeagueExternalId::getLeague);
    }

    @Transactional(readOnly = true)
    public Optional<Season> findSeason(Provider provider, String externalId) {
        requireIdentity(provider, externalId);
        return seasonExternalIds.findByProviderAndExternalId(provider, externalId.trim())
                .map(SeasonExternalId::getSeason);
    }

    @Transactional(readOnly = true)
    public Optional<Team> findTeam(Provider provider, String externalId) {
        requireIdentity(provider, externalId);
        return teamExternalIds.findByProviderAndExternalId(provider, externalId.trim())
                .map(TeamExternalId::getTeam);
    }

    @Transactional(readOnly = true)
    public Optional<Match> findMatch(Provider provider, String externalId) {
        requireIdentity(provider, externalId);
        return matchExternalIds.findByProviderAndExternalId(provider, externalId.trim())
                .map(MatchExternalId::getMatch);
    }

    @Transactional(readOnly = true)
    public Optional<Player> findPlayer(Provider provider, String externalId) {
        requireIdentity(provider, externalId);
        return playerExternalIds.findByProviderAndExternalId(provider, externalId.trim())
                .map(PlayerExternalId::getPlayer);
    }

    public LeagueExternalId registerLeague(League league, Provider provider, String externalId) {
        if (league == null || league.getId() == null) {
            throw new IllegalArgumentException("League must be persisted before registering an external ID");
        }
        requireIdentity(provider, externalId);
        return register(leagueExternalIds.findByLeagueIdAndProvider(league.getId(), provider),
                leagueExternalIds.findByProviderAndExternalId(provider, externalId.trim()),
                () -> LeagueExternalId.builder().league(league).provider(provider)
                        .externalId(externalId.trim()).build(),
                LeagueExternalId::getLeague, LeagueExternalId::getExternalId, league, "league", provider, externalId);
    }

    public SeasonExternalId registerSeason(Season season, Provider provider, String externalId) {
        if (season == null || season.getId() == null) {
            throw new IllegalArgumentException("Season must be persisted before registering an external ID");
        }
        requireIdentity(provider, externalId);
        return register(seasonExternalIds.findBySeasonIdAndProvider(season.getId(), provider),
                seasonExternalIds.findByProviderAndExternalId(provider, externalId.trim()),
                () -> SeasonExternalId.builder().season(season).provider(provider)
                        .externalId(externalId.trim()).build(),
                SeasonExternalId::getSeason, SeasonExternalId::getExternalId, season, "season", provider, externalId);
    }

    public TeamExternalId registerTeam(Team team, Provider provider, String externalId) {
        if (team == null || team.getId() == null) {
            throw new IllegalArgumentException("Team must be persisted before registering an external ID");
        }
        requireIdentity(provider, externalId);
        return register(teamExternalIds.findByTeamIdAndProvider(team.getId(), provider),
                teamExternalIds.findByProviderAndExternalId(provider, externalId.trim()),
                () -> TeamExternalId.builder().team(team).provider(provider)
                        .externalId(externalId.trim()).build(),
                TeamExternalId::getTeam, TeamExternalId::getExternalId, team, "team", provider, externalId);
    }

    public MatchExternalId registerMatch(Match match, Provider provider, String externalId) {
        if (match == null || match.getId() == null) {
            throw new IllegalArgumentException("Match must be persisted before registering an external ID");
        }
        requireIdentity(provider, externalId);
        return register(matchExternalIds.findByMatchIdAndProvider(match.getId(), provider),
                matchExternalIds.findByProviderAndExternalId(provider, externalId.trim()),
                () -> MatchExternalId.builder().match(match).provider(provider)
                        .externalId(externalId.trim()).build(),
                MatchExternalId::getMatch, MatchExternalId::getExternalId, match, "match", provider, externalId);
    }

    public PlayerExternalId registerPlayer(Player player, Provider provider, String externalId) {
        if (player == null || player.getId() == null) {
            throw new IllegalArgumentException("Player must be persisted before registering an external ID");
        }
        requireIdentity(provider, externalId);
        return register(playerExternalIds.findByPlayerIdAndProvider(player.getId(), provider),
                playerExternalIds.findByProviderAndExternalId(provider, externalId.trim()),
                () -> PlayerExternalId.builder().player(player).provider(provider)
                        .externalId(externalId.trim()).build(),
                PlayerExternalId::getPlayer, PlayerExternalId::getExternalId, player, "player", provider, externalId);
    }

    private <E, X> X register(Optional<X> byEntity, Optional<X> byExternalId,
                              java.util.function.Supplier<X> factory,
                              java.util.function.Function<X, E> entityOf,
                              java.util.function.Function<X, String> externalIdOf,
                              E requestedEntity, String entityType, Provider provider, String externalId) {
        if (byEntity.isPresent()) {
            X existing = byEntity.get();
            if (!sameEntity(entityOf.apply(existing), requestedEntity)) {
                throw conflict(entityType, provider, externalId,
                        "this entity already has a different ID for the provider");
            }
            if (!externalIdOf.apply(existing).equals(externalId.trim())) {
                throw conflict(entityType, provider, externalId,
                        "this entity already has a different ID for the provider");
            }
            if (byExternalId.isPresent() && !sameEntity(entityOf.apply(byExternalId.get()), requestedEntity)) {
                throw conflict(entityType, provider, externalId,
                        "the provider ID is already assigned to another entity");
            }
            return existing;
        }

        if (byExternalId.isPresent()) {
            if (sameEntity(entityOf.apply(byExternalId.get()), requestedEntity)) {
                return byExternalId.get();
            }
            throw conflict(entityType, provider, externalId,
                    "the provider ID is already assigned to another entity");
        }

        return save(factory.get());
    }

    @SuppressWarnings("unchecked")
    private <X> X save(X mapping) {
        if (mapping instanceof LeagueExternalId value) return (X) leagueExternalIds.save(value);
        if (mapping instanceof SeasonExternalId value) return (X) seasonExternalIds.save(value);
        if (mapping instanceof TeamExternalId value) return (X) teamExternalIds.save(value);
        if (mapping instanceof MatchExternalId value) return (X) matchExternalIds.save(value);
        if (mapping instanceof PlayerExternalId value) return (X) playerExternalIds.save(value);
        throw new IllegalArgumentException("Unsupported external ID mapping type: " + mapping.getClass());
    }

    private boolean sameEntity(Object existing, Object requested) {
        if (existing == requested) return true;
        if (existing == null || requested == null) return false;
        if (existing instanceof com.mmo.core.entity.AbstractEntity left
                && requested instanceof com.mmo.core.entity.AbstractEntity right) {
            return left.getId() != null && left.getId().equals(right.getId());
        }
        return false;
    }

    private void requireIdentity(Provider provider, String externalId) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider must not be null");
        }
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("External ID must not be blank");
        }
    }

    private IllegalStateException conflict(String entityType, Provider provider, String externalId, String reason) {
        return new IllegalStateException("Cannot register " + entityType + " external ID '"
                + externalId + "' for provider " + provider + ": " + reason);
    }
}
