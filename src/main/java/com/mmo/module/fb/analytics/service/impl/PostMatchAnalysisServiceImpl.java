package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.PlayerAnalyticsService;
import com.mmo.module.fb.analytics.service.PostMatchAnalysisService;
import com.mmo.module.fb.analytics.service.PostMatchAnalysisService.PostMatchInsight;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostMatchAnalysisServiceImpl implements PostMatchAnalysisService {

    private static final BigDecimal MATERIAL_XG_GAP = new BigDecimal("0.75");
    private final MatchRepository matchRepository;
    private final PlayerAnalyticsService playerAnalyticsService;

    @Override
    public PostMatchInsight analyze(Long matchId) {
        if (matchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "matchId is required");
        }
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found: " + matchId));
        List<String> findings = new ArrayList<>();
        List<String> angles = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean finished = match.getStatus() == MatchStatus.FINISHED
                && match.getHomeScore() != null && match.getAwayScore() != null;

        if (!finished) {
            warnings.add("Post-match result analysis requires a finished match with both final scores.");
        } else {
            int homeScore = match.getHomeScore();
            int awayScore = match.getAwayScore();
            String result = homeScore > awayScore ? match.getHomeTeam().getName() + " won"
                    : homeScore < awayScore ? match.getAwayTeam().getName() + " won" : "The match ended level";
            findings.add("Result fact: " + match.getHomeTeam().getName() + " " + homeScore
                    + "–" + awayScore + " " + match.getAwayTeam().getName() + ".");
            angles.add("Result recap: " + result + ".");
        }

        BigDecimal homeXg = decimal(match.getHomeXG());
        BigDecimal awayXg = decimal(match.getAwayXG());
        if (homeXg != null && awayXg != null && finished) {
            BigDecimal gap = homeXg.subtract(awayXg);
            String higherXgTeam = gap.signum() > 0 ? match.getHomeTeam().getName()
                    : gap.signum() < 0 ? match.getAwayTeam().getName() : "Both teams";
            findings.add("Match xG: " + match.getHomeTeam().getName() + " " + homeXg.toPlainString()
                    + "–" + awayXg.toPlainString() + " " + match.getAwayTeam().getName() + ".");
            if (gap.abs().compareTo(MATERIAL_XG_GAP) >= 0) {
                angles.add(higherXgTeam + " recorded the higher match xG by " + gap.abs().setScale(2, RoundingMode.HALF_UP)
                        + "; compare chance quality with the final score before drawing conclusions.");
            }
            BigDecimal homeGoalsMinusXg = BigDecimal.valueOf(match.getHomeScore()).subtract(homeXg);
            BigDecimal awayGoalsMinusXg = BigDecimal.valueOf(match.getAwayScore()).subtract(awayXg);
            if (homeGoalsMinusXg.abs().compareTo(new BigDecimal("1.00")) >= 0) {
                angles.add(match.getHomeTeam().getName() + " scored " + homeGoalsMinusXg.abs().setScale(2, RoundingMode.HALF_UP)
                        + " " + (homeGoalsMinusXg.signum() > 0 ? "more" : "fewer")
                        + " goal(s) than its xG estimate; treat this as a single-match observation, not proof of finishing skill.");
            }
            if (awayGoalsMinusXg.abs().compareTo(new BigDecimal("1.00")) >= 0) {
                angles.add(match.getAwayTeam().getName() + " scored " + awayGoalsMinusXg.abs().setScale(2, RoundingMode.HALF_UP)
                        + " " + (awayGoalsMinusXg.signum() > 0 ? "more" : "fewer")
                        + " goal(s) than its xG estimate; treat this as a single-match observation, not proof of finishing skill.");
            }
        } else {
            warnings.add("Complete match-level xG is unavailable; xG comparison and finishing-over/underperformance angles are omitted.");
        }

        PlayerAnalyticsService.PlayerMatchInsight players = playerAnalyticsService.analyzeMatch(matchId);
        warnings.addAll(players.dataWarnings());
        if (!players.standoutPlayers().isEmpty()) {
            PlayerAnalyticsService.PlayerPerformance leader = players.standoutPlayers().get(0);
            if (leader.playerName() != null) {
                angles.add("Player spotlight candidate: " + leader.playerName()
                        + " (editorial impact heuristic " + leader.impactScore()
                        + "); validate the underlying event statistics before publication.");
            }
        }
        String competition = match.getSeason() == null || match.getSeason().getLeague() == null
                ? null : match.getSeason().getLeague().getName();
        return new PostMatchInsight(match.getId(), match.getMatchTime(), competition,
                match.getHomeTeam() == null ? null : match.getHomeTeam().getName(),
                match.getAwayTeam() == null ? null : match.getAwayTeam().getName(),
                match.getHomeScore(), match.getAwayScore(), homeXg, awayXg,
                finished ? match.getHomeTeam().getName() + " " + match.getHomeScore() + "–"
                        + match.getAwayScore() + " " + match.getAwayTeam().getName() : null,
                findings, angles, warnings, players);
    }

    private BigDecimal decimal(Double value) {
        return value == null || !Double.isFinite(value) ? null
                : BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }
}
