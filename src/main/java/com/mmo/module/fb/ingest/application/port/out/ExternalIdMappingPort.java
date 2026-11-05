package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.LeagueExternalId;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.entity.SeasonExternalId;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchExternalId;
import com.mmo.module.fb.domain.player.entity.Player;
import com.mmo.module.fb.domain.player.entity.PlayerExternalId;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.team.entity.TeamExternalId;
import com.mmo.module.fb.ingest.enums.Provider;

import java.util.Optional;

/** Outbound port for provider-scoped canonical entity identity mappings. */
public interface ExternalIdMappingPort {
    Optional<League> findLeague(Provider provider, String externalId);
    Optional<Season> findSeason(Provider provider, String externalId);
    Optional<Team> findTeam(Provider provider, String externalId);
    Optional<Match> findMatch(Provider provider, String externalId);
    Optional<Player> findPlayer(Provider provider, String externalId);

    LeagueExternalId registerLeague(League league, Provider provider, String externalId);
    SeasonExternalId registerSeason(Season season, Provider provider, String externalId);
    TeamExternalId registerTeam(Team team, Provider provider, String externalId);
    MatchExternalId registerMatch(Match match, Provider provider, String externalId);
    PlayerExternalId registerPlayer(Player player, Provider provider, String externalId);
}
