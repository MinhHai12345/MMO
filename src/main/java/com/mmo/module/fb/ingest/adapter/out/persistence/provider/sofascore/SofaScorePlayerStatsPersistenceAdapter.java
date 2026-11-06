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
import com.mmo.module.fb.ingest.application.model.FieldMergeCommand;
import com.mmo.module.fb.ingest.application.port.out.SourceAwareMergePort;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.application.model.provider.ProviderLineupPlayer;
import com.mmo.module.fb.ingest.application.model.provider.ProviderLineups;
import com.mmo.module.fb.ingest.application.model.provider.ProviderPlayerMatchStats;
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

        List<PlayerMatchStats> existingStats = playerMatchStatsRepository.findAllByMatchIdOrderByIdAsc(matchId);
        List<Long> existingPlayerIds = existingStats.stream()
                .filter(stats -> stats.getPlayer() != null && stats.getPlayer().getId() != null)
                .map(stats -> stats.getPlayer().getId()).distinct().toList();
        Map<Long, PlayerMatchStats> statsByPlayerId = new HashMap<>();
        existingStats.stream().filter(stats -> stats.getPlayer() != null)
                .forEach(stats -> statsByPlayerId.put(stats.getPlayer().getId(), stats));
        Map<String, PlayerMatchStats> existingByExternalId = new HashMap<>();
        if (!existingPlayerIds.isEmpty()) {
            playerExternalIdRepository.findAllByPlayerIdInAndProvider(existingPlayerIds, Provider.SOFA_SCORE)
                    .forEach(mapping -> {
                        PlayerMatchStats stats = statsByPlayerId.get(mapping.getPlayer().getId());
                        if (stats != null) existingByExternalId.put(mapping.getExternalId(), stats);
                    });
        }

        List<String> incomingExternalIds = java.util.stream.Stream.concat(
                        (lineups.home() == null ? java.util.stream.Stream.<ProviderLineupPlayer>empty()
                                : lineups.home().stream()),
                        (lineups.away() == null ? java.util.stream.Stream.<ProviderLineupPlayer>empty()
                                : lineups.away().stream()))
                .filter(Objects::nonNull)
                .map(ProviderLineupPlayer::externalPlayerId)
                .filter(Objects::nonNull).distinct().toList();
        Map<String, PlayerExternalId> playerMappingsByExternalId = new HashMap<>();
        if (!incomingExternalIds.isEmpty()) {
            playerExternalIdRepository.findAllByProviderAndExternalIdIn(
                    Provider.SOFA_SCORE, incomingExternalIds)
                    .forEach(mapping -> playerMappingsByExternalId.put(mapping.getExternalId(), mapping));
        }

        List<PlayerMatchStats> toSave = new ArrayList<>();
        List<FieldMergeCommand> mergeCommands = new ArrayList<>();
        addTeamPlayers(match, lineups.home(), match.getHomeTeam(), toSave,
                existingByExternalId, playerMappingsByExternalId, mergeCommands);
        addTeamPlayers(match, lineups.away(), match.getAwayTeam(), toSave,
                existingByExternalId, playerMappingsByExternalId, mergeCommands);
        sourceAwareMergeService.mergeAll(mergeCommands);
        if (!toSave.isEmpty()) playerMatchStatsRepository.saveAll(toSave);
        return toSave.size();
    }

    private void addTeamPlayers(Match match, List<ProviderLineupPlayer> lineup, Team team,
                                List<PlayerMatchStats> toSave,
                                Map<String, PlayerMatchStats> existingByExternalId,
                                Map<String, PlayerExternalId> playerMappingsByExternalId,
                                List<FieldMergeCommand> mergeCommands) {
        if (lineup == null || team == null) return;
        for (ProviderLineupPlayer lineupPlayer : lineup) {
            if (lineupPlayer == null || lineupPlayer.externalPlayerId() == null
                    || lineupPlayer.name() == null || lineupPlayer.statistics() == null) {
                // A lineup identity alone is not evidence of a player performance line.
                continue;
            }
            String externalId = lineupPlayer.externalPlayerId();
            PlayerExternalId mapping = playerMappingsByExternalId.get(externalId);
            Player player;
            if (mapping != null) {
                player = mapping.getPlayer();
            } else {
                mapping = createPlayer(lineupPlayer, team, externalId);
                player = mapping.getPlayer();
                playerMappingsByExternalId.put(externalId, mapping);
            }
            if (player.getTeam() == null || !Objects.equals(player.getTeam().getId(), team.getId())) {
                player.setTeam(team);
                player = playerRepository.save(player);
            }
            PlayerMatchStats stats = existingByExternalId.get(externalId);
            if (stats == null) stats = PlayerMatchStats.builder().player(player).match(match).team(team).build();
            stats.setPlayer(player);
            stats.setMatch(match);
            stats.setTeam(team);
            applyStatistics(match, player.getId(), stats, lineupPlayer.statistics(), mergeCommands);
            toSave.add(stats);
        }
    }

    private PlayerExternalId createPlayer(ProviderLineupPlayer dto, Team team, String externalId) {
        Player player = Player.builder().name(dto.name())
                .position(dto.position() == null || dto.position().isBlank() ? "Unknown" : dto.position())
                .nationality(dto.nationality()).height(dto.height()).weight(dto.weight())
                .team(team).isActive(true).build();
        player = playerRepository.save(player);
        return playerExternalIdRepository.save(PlayerExternalId.builder()
                .player(player).provider(Provider.SOFA_SCORE).externalId(externalId).build());
    }

    private void applyStatistics(Match match, Long playerId, PlayerMatchStats stats,
                                 ProviderPlayerMatchStats values, List<FieldMergeCommand> mergeCommands) {
        merge(match, playerId, "minutes_played", stats.getMinutesPlayed(),
                values.minutesPlayed(), stats::setMinutesPlayed, mergeCommands);
        merge(match, playerId, "goals", stats.getGoals(), values.goals(), stats::setGoals, mergeCommands);
        merge(match, playerId, "assists", stats.getAssists(), values.assists(), stats::setAssists, mergeCommands);
        merge(match, playerId, "shots", stats.getShots(), values.shots(), stats::setShots, mergeCommands);
        merge(match, playerId, "shots_on_target", stats.getShotsOnTarget(),
                values.shotsOnTarget(), stats::setShotsOnTarget, mergeCommands);
        merge(match, playerId, "xg", stats.getXg(), values.xg(), stats::setXg, mergeCommands);
        merge(match, playerId, "xa", stats.getXa(), values.xa(), stats::setXa, mergeCommands);
        merge(match, playerId, "passes", stats.getPasses(), values.passes(), stats::setPasses, mergeCommands);
        merge(match, playerId, "pass_accuracy", stats.getPassAccuracy(),
                values.passAccuracy(), stats::setPassAccuracy, mergeCommands);
        merge(match, playerId, "key_passes", stats.getKeyPasses(), values.keyPasses(), stats::setKeyPasses, mergeCommands);
        merge(match, playerId, "tackles", stats.getTackles(), values.tackles(), stats::setTackles, mergeCommands);
        merge(match, playerId, "interceptions", stats.getInterceptions(),
                values.interceptions(), stats::setInterceptions, mergeCommands);
        merge(match, playerId, "duels_won", stats.getDuelsWon(), values.duelsWon(), stats::setDuelsWon, mergeCommands);
        merge(match, playerId, "dribbles", stats.getDribbles(), values.dribbles(), stats::setDribbles, mergeCommands);
        merge(match, playerId, "fouls_drawn", stats.getFoulsDrawn(), values.foulsDrawn(), stats::setFoulsDrawn, mergeCommands);
        merge(match, playerId, "fouls_committed", stats.getFoulsCommitted(),
                values.foulsCommitted(), stats::setFoulsCommitted, mergeCommands);
        merge(match, playerId, "yellow_cards", stats.getYellowCards(), values.yellowCards(), stats::setYellowCards, mergeCommands);
        merge(match, playerId, "red_cards", stats.getRedCards(), values.redCards(), stats::setRedCards, mergeCommands);
    }

    private <T> void merge(Match match, Long playerId, String field, T current, T incoming,
                           Consumer<T> setter, List<FieldMergeCommand> mergeCommands) {
        if (incoming == null || playerId == null) return;
        mergeCommands.add(new FieldMergeCommand(match, Provider.SOFA_SCORE, Dataset.PLAYER_STATISTICS,
                "player:" + playerId + ":" + field, current != null, null, () -> setter.accept(incoming)));
    }
}
