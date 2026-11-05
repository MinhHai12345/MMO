package com.mmo.module.fb.ingest.persistence.sofascore;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.persistence.SourceAwareMergeService;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreLineupsResponseDTO;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.domain.player.entity.Player;
import com.mmo.module.fb.domain.player.entity.PlayerExternalId;
import com.mmo.module.fb.domain.player.entity.PlayerMatchStats;
import com.mmo.module.fb.domain.player.repository.PlayerExternalIdRepository;
import com.mmo.module.fb.domain.player.repository.PlayerMatchStatsRepository;
import com.mmo.module.fb.domain.player.repository.PlayerRepository;
import com.mmo.module.fb.domain.team.entity.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class SofaScorePlayerStatsPersistenceService {
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final PlayerExternalIdRepository playerExternalIdRepository;
    private final PlayerMatchStatsRepository playerMatchStatsRepository;
    private final SourceAwareMergeService sourceAwareMergeService;

    @Transactional
    public int persist(Long matchId, SofaScoreLineupsResponseDTO response) {
        if (response == null) return 0;
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

    private void addTeamPlayers(Match match, SofaScoreLineupsResponseDTO.LineupTeamDTO lineup,
                                Team team, List<PlayerMatchStats> toSave,
                                Map<String, PlayerMatchStats> existingByExternalId) {
        if (lineup == null || lineup.players() == null || team == null) return;
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
            applyStatistics(match, player.getId(), stats, lineupPlayer.statistics());
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

    private void applyStatistics(Match match, Long playerId, PlayerMatchStats stats, Map<String, Object> values) {
        merge(match, playerId, "minutes_played", stats.getMinutesPlayed(),
                integer(values, "minutesPlayed", "minutes"), stats::setMinutesPlayed);
        merge(match, playerId, "goals", stats.getGoals(), integer(values, "goals"), stats::setGoals);
        merge(match, playerId, "assists", stats.getAssists(), integer(values, "goalAssist", "assists"), stats::setAssists);
        merge(match, playerId, "shots", stats.getShots(), integer(values, "totalShots", "shots"), stats::setShots);
        merge(match, playerId, "shots_on_target", stats.getShotsOnTarget(),
                integer(values, "shotsOnTarget", "onTargetScoringAttempt"), stats::setShotsOnTarget);
        merge(match, playerId, "xg", stats.getXg(), decimal(values, "expectedGoals", "xg"), stats::setXg);
        merge(match, playerId, "xa", stats.getXa(), decimal(values, "expectedAssists", "xa"), stats::setXa);
        merge(match, playerId, "passes", stats.getPasses(), integer(values, "totalPasses", "passes"), stats::setPasses);
        merge(match, playerId, "pass_accuracy", stats.getPassAccuracy(),
                decimal(values, "accuratePassesPercentage", "passAccuracy"), stats::setPassAccuracy);
        merge(match, playerId, "key_passes", stats.getKeyPasses(), integer(values, "keyPasses"), stats::setKeyPasses);
        merge(match, playerId, "tackles", stats.getTackles(), integer(values, "tackles"), stats::setTackles);
        merge(match, playerId, "interceptions", stats.getInterceptions(),
                integer(values, "interceptions"), stats::setInterceptions);
        merge(match, playerId, "duels_won", stats.getDuelsWon(),
                integer(values, "duelWon", "duelsWon"), stats::setDuelsWon);
        merge(match, playerId, "dribbles", stats.getDribbles(),
                integer(values, "successfulDribbles", "dribbles"), stats::setDribbles);
        merge(match, playerId, "fouls_drawn", stats.getFoulsDrawn(),
                integer(values, "wasFouled", "foulsDrawn"), stats::setFoulsDrawn);
        merge(match, playerId, "fouls_committed", stats.getFoulsCommitted(),
                integer(values, "fouls", "foulsCommitted"), stats::setFoulsCommitted);
        merge(match, playerId, "yellow_cards", stats.getYellowCards(),
                integer(values, "yellowCards"), stats::setYellowCards);
        merge(match, playerId, "red_cards", stats.getRedCards(),
                integer(values, "redCards"), stats::setRedCards);
    }

    private <T> void merge(Match match, Long playerId, String field, T current, T incoming, Consumer<T> setter) {
        if (incoming == null || playerId == null) return;
        sourceAwareMergeService.merge(match, Provider.SOFA_SCORE, Dataset.PLAYER_STATISTICS,
                "player:" + playerId + ":" + field, current != null, null, () -> setter.accept(incoming));
    }

    private Integer integer(Map<String, Object> values, String... keys) {
        BigDecimal value = decimal(values, keys);
        if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > 0
                || value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) return null;
        return value.intValue();
    }

    private BigDecimal decimal(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object raw = values.get(key);
            if (raw == null) continue;
            try {
                String text = raw.toString().trim().replace("%", "").replace(",", ".");
                if (!text.isBlank() && !"-".equals(text)) {
                    BigDecimal parsed = new BigDecimal(text);
                    if (parsed.signum() >= 0) return parsed;
                }
            } catch (NumberFormatException ignored) {
                // Try the next provider alias.
            }
        }
        return null;
    }
}
