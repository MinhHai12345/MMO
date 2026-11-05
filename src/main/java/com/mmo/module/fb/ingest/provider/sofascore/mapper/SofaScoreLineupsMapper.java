package com.mmo.module.fb.ingest.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.provider.model.ProviderLineupPlayer;
import com.mmo.module.fb.ingest.provider.model.ProviderLineups;
import com.mmo.module.fb.ingest.provider.model.ProviderPlayerMatchStats;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreLineupsResponseDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts SofaScore's raw lineup DTO into provider-neutral canonical player metrics. */
@Component
public class SofaScoreLineupsMapper {

    public ProviderLineups toModel(SofaScoreLineupsResponseDTO source) {
        if (source == null) return new ProviderLineups(List.of(), List.of());
        return new ProviderLineups(mapTeam(source.home()), mapTeam(source.away()));
    }

    private List<ProviderLineupPlayer> mapTeam(SofaScoreLineupsResponseDTO.LineupTeamDTO source) {
        if (source == null || source.players() == null || source.players().isEmpty()) return List.of();
        List<ProviderLineupPlayer> result = new ArrayList<>();
        for (SofaScoreLineupsResponseDTO.LineupPlayerDTO lineupPlayer : source.players()) {
            if (lineupPlayer == null || lineupPlayer.player() == null
                    || lineupPlayer.player().id() == null || lineupPlayer.player().name() == null) continue;
            var player = lineupPlayer.player();
            String position = hasText(lineupPlayer.position()) ? lineupPlayer.position() : player.position();
            result.add(new ProviderLineupPlayer(
                    player.id().toString(), player.name(), position, player.nationality(),
                    player.height(), player.weight(), mapStatistics(lineupPlayer.statistics())));
        }
        return List.copyOf(result);
    }

    private ProviderPlayerMatchStats mapStatistics(Map<String, Object> values) {
        if (values == null || values.isEmpty()) return null;
        return new ProviderPlayerMatchStats(
                integer(values, "minutesPlayed", "minutes"),
                integer(values, "goals"),
                integer(values, "goalAssist", "assists"),
                integer(values, "totalShots", "shots"),
                integer(values, "shotsOnTarget", "onTargetScoringAttempt"),
                decimal(values, "expectedGoals", "xg"),
                decimal(values, "expectedAssists", "xa"),
                integer(values, "totalPasses", "passes"),
                decimal(values, "accuratePassesPercentage", "passAccuracy"),
                integer(values, "keyPasses"),
                integer(values, "tackles"),
                integer(values, "interceptions"),
                integer(values, "duelWon", "duelsWon"),
                integer(values, "successfulDribbles", "dribbles"),
                integer(values, "wasFouled", "foulsDrawn"),
                integer(values, "fouls", "foulsCommitted"),
                integer(values, "yellowCards"),
                integer(values, "redCards"));
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
