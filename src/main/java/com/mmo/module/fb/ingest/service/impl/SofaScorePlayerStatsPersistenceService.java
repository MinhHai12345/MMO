package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreLineupsResponseDTO;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.repository.MatchRepository;
import com.mmo.module.fb.player.entity.Player;
import com.mmo.module.fb.player.entity.PlayerExternalId;
import com.mmo.module.fb.player.entity.PlayerMatchStats;
import com.mmo.module.fb.player.repository.PlayerExternalIdRepository;
import com.mmo.module.fb.player.repository.PlayerMatchStatsRepository;
import com.mmo.module.fb.player.repository.PlayerRepository;
import com.mmo.module.fb.team.entity.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SofaScorePlayerStatsPersistenceService {
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final PlayerExternalIdRepository playerExternalIdRepository;
    private final PlayerMatchStatsRepository playerMatchStatsRepository;

    @Transactional
    public int persist(Long matchId, SofaScoreLineupsResponseDTO response) {
        Match match = matchRepository.findById(matchId).orElse(null);
        if (match == null) return 0;
        Map<String, PlayerMatchStats> existingByExternalId = new HashMap<>();
        for (PlayerMatchStats stats : playerMatchStatsRepository.findAllByMatchIdOrderByIdAsc(matchId)) {
            if (stats.getPlayer() == null) continue;
            playerExternalIdRepository.findByPlayerIdAndProvider(stats.getPlayer().getId(), Provider.SOFA_SCORE)
                    .ifPresent(mapping -> existingByExternalId.put(mapping.getExternalId(), stats));
        }
        List<PlayerMatchStats> toSave = new ArrayList<>();
        addTeamPlayers(match, response.home(), match.getHomeTeam(), toSave, existingByExternalId);
        addTeamPlayers(match, response.away(), match.getAwayTeam(), toSave, existingByExternalId);
        if (!toSave.isEmpty()) playerMatchStatsRepository.saveAll(toSave);
        return toSave.size();
    }

    @Transactional
    public void markAttempt(Long matchId) {
        matchRepository.findById(matchId).ifPresent(match -> {
            match.setPlayerStatisticsLastAttemptAt(Instant.now());
            matchRepository.save(match);
        });
    }

    private void addTeamPlayers(Match match, SofaScoreLineupsResponseDTO.LineupTeamDTO lineup,
                                Team team, List<PlayerMatchStats> toSave,
                                Map<String, PlayerMatchStats> existingByExternalId) {
        if (lineup == null || lineup.players() == null) return;
        for (SofaScoreLineupsResponseDTO.LineupPlayerDTO lineupPlayer : lineup.players()) {
            if (lineupPlayer == null || lineupPlayer.player() == null
                    || lineupPlayer.player().id() == null || lineupPlayer.player().name() == null
                    || lineupPlayer.statistics() == null || lineupPlayer.statistics().isEmpty()) {
                // A lineup identity alone is not evidence of a player performance line.
                continue;
            }
            String externalId = lineupPlayer.player().id().toString();
            Player player = playerExternalIdRepository.findByProviderAndExternalId(Provider.SOFA_SCORE, externalId)
                    .map(PlayerExternalId::getPlayer)
                    .orElseGet(() -> createPlayer(lineupPlayer.player(), team, externalId));
            if (player.getTeam() == null || !Objects.equals(player.getTeam().getId(), team.getId())) {
                player.setTeam(team);
                player = playerRepository.save(player);
            }
            PlayerMatchStats stats = existingByExternalId.get(externalId);
            if (stats == null) stats = PlayerMatchStats.builder().player(player).match(match).team(team).build();
            stats.setPlayer(player);
            stats.setMatch(match);
            stats.setTeam(team);
            applyStatistics(stats, lineupPlayer.statistics());
            toSave.add(stats);
        }
    }

    private Player createPlayer(SofaScoreLineupsResponseDTO.PlayerDTO dto, Team team, String externalId) {
        Player player = Player.builder().name(dto.name())
                .position(dto.position() == null || dto.position().isBlank() ? "Unknown" : dto.position())
                .nationality(dto.nationality()).height(dto.height()).weight(dto.weight())
                .team(team).isActive(true).build();
        player = playerRepository.save(player);
        playerExternalIdRepository.save(PlayerExternalId.builder()
                .player(player).provider(Provider.SOFA_SCORE).externalId(externalId).build());
        return player;
    }

    private void applyStatistics(PlayerMatchStats stats, Map<String, Object> values) {
        stats.setMinutesPlayed(integer(values, "minutesPlayed", "minutes"));
        stats.setGoals(integer(values, "goals"));
        stats.setAssists(integer(values, "goalAssist", "assists"));
        stats.setShots(integer(values, "totalShots", "shots"));
        stats.setShotsOnTarget(integer(values, "shotsOnTarget", "onTargetScoringAttempt"));
        stats.setXg(decimal(values, "expectedGoals", "xg"));
        stats.setXa(decimal(values, "expectedAssists", "xa"));
        stats.setPasses(integer(values, "totalPasses", "passes"));
        stats.setPassAccuracy(decimal(values, "accuratePassesPercentage", "passAccuracy"));
        stats.setKeyPasses(integer(values, "keyPasses"));
        stats.setTackles(integer(values, "tackles"));
        stats.setInterceptions(integer(values, "interceptions"));
        stats.setDuelsWon(integer(values, "duelWon", "duelsWon"));
        stats.setDribbles(integer(values, "successfulDribbles", "dribbles"));
        stats.setFoulsDrawn(integer(values, "wasFouled", "foulsDrawn"));
        stats.setFoulsCommitted(integer(values, "fouls", "foulsCommitted"));
        stats.setYellowCards(integer(values, "yellowCards"));
        stats.setRedCards(integer(values, "redCards"));
    }

    private Integer integer(Map<String, Object> values, String... keys) {
        BigDecimal value = decimal(values, keys);
        return value == null ? null : value.intValue();
    }

    private BigDecimal decimal(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object raw = values.get(key);
            if (raw == null) continue;
            try {
                String text = raw.toString().trim().replace("%", "").replace(",", ".");
                if (!text.isBlank() && !"-".equals(text)) return new BigDecimal(text);
            } catch (NumberFormatException ignored) {
                // Try the next provider alias.
            }
        }
        return null;
    }
}
