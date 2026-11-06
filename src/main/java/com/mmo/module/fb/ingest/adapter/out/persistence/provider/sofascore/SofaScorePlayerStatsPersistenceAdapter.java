package com.mmo.module.fb.ingest.adapter.out.persistence.provider.sofascore;

import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.domain.player.entity.Player;
import com.mmo.module.fb.domain.player.entity.PlayerExternalId;
import com.mmo.module.fb.domain.player.entity.PlayerMatchStats;
import com.mmo.module.fb.domain.player.repository.PlayerExternalIdRepository;
import com.mmo.module.fb.domain.player.repository.PlayerMatchStatsRepository;
import com.mmo.module.fb.domain.player.repository.PlayerRepository;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.ingest.application.port.out.PlayerMatchStatisticsPersistencePort;
import com.mmo.module.fb.ingest.application.port.out.SourceAwareMergePort;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.provider.model.ProviderLineupPlayer;
import com.mmo.module.fb.ingest.provider.model.ProviderLineups;
import com.mmo.module.fb.ingest.provider.model.ProviderPlayerMatchStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Persists provider-neutral player match metrics and source provenance. */
@Repository
@RequiredArgsConstructor
public class SofaScorePlayerStatsPersistenceAdapter implements PlayerMatchStatisticsPersistencePort {
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final PlayerExternalIdRepository playerExternalIdRepository;
    private final PlayerMatchStatsRepository playerMatchStatsRepository;
    private final SourceAwareMergePort sourceAwareMergeService;

    @Override
    @Transactional
    public int persist(Long matchId, ProviderLineups lineups) {
        if (matchId == null || lineups == null || lineups.isEmpty()) return 0;
        Match match = matchRepository.findById(matchId).orElse(null);
        if (match == null) return 0;

        Map<String, PlayerMatchStats> existingByExternalId = new HashMap<>();
        for (PlayerMatchStats stats : playerMatchStatsRepository.findAllByMatchIdOrderByIdAsc(matchId)) {
            if (stats.getPlayer() == null) continue;
            playerExternalIdRepository.findByPlayerIdAndProvider(stats.getPlayer().getId(), Provider.SOFA_SCORE)
                    .ifPresent(mapping -> existingByExternalId.put(mapping.getExternalId(), stats));
        }

        List<PlayerMatchStats> toSave = new ArrayList<>();
        addTeamPlayers(match, lineups.home(), match.getHomeTeam(), toSave, existingByExternalId);
        addTeamPlayers(match, lineups.away(), match.getAwayTeam(), toSave, existingByExternalId);
        if (!toSave.isEmpty()) playerMatchStatsRepository.saveAll(toSave);
        return toSave.size();
    }

    private void addTeamPlayers(Match match, List<ProviderLineupPlayer> lineup, Team team,
                                List<PlayerMatchStats> toSave,
                                Map<String, PlayerMatchStats> existingByExternalId) {
        if (lineup == null || team == null) return;
        for (ProviderLineupPlayer lineupPlayer : lineup) {
            if (lineupPlayer == null || lineupPlayer.externalPlayerId() == null
                    || lineupPlayer.name() == null || lineupPlayer.statistics() == null) {
                // A lineup identity alone is not evidence of a player performance line.
                continue;
            }
            String externalId = lineupPlayer.externalPlayerId();
            Player player = playerExternalIdRepository.findByProviderAndExternalId(Provider.SOFA_SCORE, externalId)
                    .map(PlayerExternalId::getPlayer)
                    .orElseGet(() -> createPlayer(lineupPlayer, team, externalId));
            if (player.getTeam() == null || !Objects.equals(player.getTeam().getId(), team.getId())) {
                player.setTeam(team);
                player = playerRepository.save(player);
            }
            PlayerMatchStats stats = existingByExternalId.get(externalId);
            if (stats == null) stats = PlayerMatchStats.builder().player(player).match(match).team(team).build();
            stats.setPlayer(player);
            stats.setMatch(match);
            stats.setTeam(team);
            applyStatistics(match, player.getId(), stats, lineupPlayer.statistics());
            toSave.add(stats);
        }
    }

    private Player createPlayer(ProviderLineupPlayer dto, Team team, String externalId) {
        Player player = Player.builder().name(dto.name())
                .position(dto.position() == null || dto.position().isBlank() ? "Unknown" : dto.position())
                .nationality(dto.nationality()).height(dto.height()).weight(dto.weight())
                .team(team).isActive(true).build();
        player = playerRepository.save(player);
        playerExternalIdRepository.save(PlayerExternalId.builder()
                .player(player).provider(Provider.SOFA_SCORE).externalId(externalId).build());
        return player;
    }

    private void applyStatistics(Match match, Long playerId, PlayerMatchStats stats,
                                 ProviderPlayerMatchStats values) {
        merge(match, playerId, "minutes_played", stats.getMinutesPlayed(),
                values.minutesPlayed(), stats::setMinutesPlayed);
        merge(match, playerId, "goals", stats.getGoals(), values.goals(), stats::setGoals);
        merge(match, playerId, "assists", stats.getAssists(), values.assists(), stats::setAssists);
        merge(match, playerId, "shots", stats.getShots(), values.shots(), stats::setShots);
        merge(match, playerId, "shots_on_target", stats.getShotsOnTarget(),
                values.shotsOnTarget(), stats::setShotsOnTarget);
        merge(match, playerId, "xg", stats.getXg(), values.xg(), stats::setXg);
        merge(match, playerId, "xa", stats.getXa(), values.xa(), stats::setXa);
        merge(match, playerId, "passes", stats.getPasses(), values.passes(), stats::setPasses);
        merge(match, playerId, "pass_accuracy", stats.getPassAccuracy(),
                values.passAccuracy(), stats::setPassAccuracy);
        merge(match, playerId, "key_passes", stats.getKeyPasses(), values.keyPasses(), stats::setKeyPasses);
        merge(match, playerId, "tackles", stats.getTackles(), values.tackles(), stats::setTackles);
        merge(match, playerId, "interceptions", stats.getInterceptions(),
                values.interceptions(), stats::setInterceptions);
        merge(match, playerId, "duels_won", stats.getDuelsWon(), values.duelsWon(), stats::setDuelsWon);
        merge(match, playerId, "dribbles", stats.getDribbles(), values.dribbles(), stats::setDribbles);
        merge(match, playerId, "fouls_drawn", stats.getFoulsDrawn(), values.foulsDrawn(), stats::setFoulsDrawn);
        merge(match, playerId, "fouls_committed", stats.getFoulsCommitted(),
                values.foulsCommitted(), stats::setFoulsCommitted);
        merge(match, playerId, "yellow_cards", stats.getYellowCards(), values.yellowCards(), stats::setYellowCards);
        merge(match, playerId, "red_cards", stats.getRedCards(), values.redCards(), stats::setRedCards);
    }

    private <T> void merge(Match match, Long playerId, String field, T current, T incoming, Consumer<T> setter) {
        if (incoming == null || playerId == null) return;
        sourceAwareMergeService.merge(match, Provider.SOFA_SCORE, Dataset.PLAYER_STATISTICS,
                "player:" + playerId + ":" + field, current != null, null, () -> setter.accept(incoming));
    }
}
