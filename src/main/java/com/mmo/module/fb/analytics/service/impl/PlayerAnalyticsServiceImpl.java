package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.PlayerAnalyticsService;
import com.mmo.module.fb.analytics.service.PlayerAnalyticsService.PlayerMatchInsight;
import com.mmo.module.fb.analytics.service.PlayerAnalyticsService.PlayerPerformance;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.PlayerMatchStats;
import com.mmo.module.fb.domain.repository.MatchPlayerStatsRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerAnalyticsServiceImpl implements PlayerAnalyticsService {

    private static final int SCALE = 3;
    private final MatchRepository matchRepository;
    private final MatchPlayerStatsRepository playerMatchStatsRepository;

    @Override
    public PlayerMatchInsight analyzeMatch(Long matchId) {
        if (matchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "matchId is required");
        }
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found: " + matchId));
        List<PlayerMatchStats> stats = playerMatchStatsRepository.findAllByMatchId(matchId);
        List<String> warnings = new ArrayList<>();
        if (match.getStatus() != com.mmo.module.fb.domain.enums.MatchStatus.FINISHED) {
            warnings.add("Player match analytics is most reliable after the fixture is finished and provider statistics have settled.");
        }
        if (stats.isEmpty()) {
            warnings.add("No player-level statistics are stored for this match. Player rankings are omitted rather than inferred from the score.");
            return new PlayerMatchInsight(matchId, 0, List.of(), warnings);
        }

        List<PlayerPerformance> ranked = stats.stream()
                .map(this::toPerformance)
                .sorted(Comparator.comparing(PlayerPerformance::impactScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList();
        if (stats.size() < 14) {
            warnings.add("Only " + stats.size() + " player-stat rows are available; the match dataset may be incomplete.");
        }
        if (stats.stream().noneMatch(s -> s.getXg() != null || s.getXa() != null)) {
            warnings.add("Player xG/xA is unavailable; chance-quality and finishing-efficiency claims are omitted.");
        }
        return new PlayerMatchInsight(matchId, stats.size(), ranked, warnings);
    }

    private PlayerPerformance toPerformance(PlayerMatchStats stats) {
        int goals = value(stats.getGoals());
        int assists = value(stats.getAssists());
        BigDecimal xg = stats.getXg();
        BigDecimal xa = stats.getXa();
        BigDecimal contribution = BigDecimal.valueOf(goals + assists).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal overperformance = xg == null ? null
                : BigDecimal.valueOf(goals).subtract(xg).setScale(SCALE, RoundingMode.HALF_UP);

        // Transparent editorial impact heuristic, not a provider rating or a scientific player score.
        BigDecimal score = BigDecimal.valueOf(goals * 4.0 + assists * 3.0
                + value(stats.getShotsOnTarget()) * 0.6 + value(stats.getKeyPasses()) * 0.35
                + value(stats.getTackles()) * 0.25 + value(stats.getInterceptions()) * 0.2
                + value(stats.getDuelsWon()) * 0.1);
        if (xg != null) score = score.add(xg.multiply(new BigDecimal("0.5")));
        if (xa != null) score = score.add(xa.multiply(new BigDecimal("0.5")));
        List<String> evidence = new ArrayList<>();
        if (goals > 0) evidence.add(goals + " goal(s)");
        if (assists > 0) evidence.add(assists + " assist(s)");
        if (xg != null) evidence.add("xG " + xg.toPlainString());
        if (xa != null) evidence.add("xA " + xa.toPlainString());
        if (value(stats.getShotsOnTarget()) > 0) evidence.add(value(stats.getShotsOnTarget()) + " shot(s) on target");
        if (value(stats.getKeyPasses()) > 0) evidence.add(value(stats.getKeyPasses()) + " key pass(es)");
        if (value(stats.getTackles()) > 0) evidence.add(value(stats.getTackles()) + " tackle(s)");

        return new PlayerPerformance(
                stats.getPlayer() == null ? null : stats.getPlayer().getId(),
                stats.getPlayer() == null ? null : stats.getPlayer().getName(),
                stats.getPlayer() == null || stats.getPlayer().getTeam() == null
                        ? null : stats.getPlayer().getTeam().getName(),
                stats.getMinutesPlayed(), goals, assists, xg, xa, value(stats.getShots()),
                value(stats.getShotsOnTarget()), value(stats.getKeyPasses()), value(stats.getTackles()),
                value(stats.getInterceptions()), value(stats.getDuelsWon()), value(stats.getYellowCards()),
                value(stats.getRedCards()), contribution, overperformance,
                score.setScale(SCALE, RoundingMode.HALF_UP), evidence);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
