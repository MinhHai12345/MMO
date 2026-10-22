package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.ModernFootballAnalyticsService;
import com.mmo.module.fb.analytics.service.ModernFootballAnalyticsService.ContentBrief;
import com.mmo.module.fb.analytics.service.ModernFootballAnalyticsService.MatchComparison;
import com.mmo.module.fb.analytics.service.ModernFootballAnalyticsService.ModernMatchReport;
import com.mmo.module.fb.analytics.service.ModernFootballAnalyticsService.PlayerImpact;
import com.mmo.module.fb.analytics.service.ModernFootballAnalyticsService.PostMatchReview;
import com.mmo.module.fb.analytics.service.ModernFootballAnalyticsService.TeamMetrics;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchAdvancedStats;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.entity.PlayerMatchStats;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.repository.MatchAdvancedStatsRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.domain.repository.PlayerMatchStatsRepository;
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
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModernFootballAnalyticsServiceImpl implements ModernFootballAnalyticsService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private final MatchRepository matchRepository;
    private final MatchAdvancedStatsRepository advancedStatsRepository;
    private final PlayerMatchStatsRepository playerMatchStatsRepository;

    @Override
    public ModernMatchReport analyze(Long matchId) {
        if (matchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "matchId is required");
        }
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found: " + matchId));

        List<String> limitations = new ArrayList<>();
        List<MatchAdvancedStats> advanced = advancedStatsRepository.findAllByMatchId(matchId);
        MatchAdvancedStats homeAdvanced = findForTeam(advanced, match.getHomeTeam());
        MatchAdvancedStats awayAdvanced = findForTeam(advanced, match.getAwayTeam());
        TeamMetrics home = teamMetrics(match.getHomeTeam(), homeAdvanced, match.getHomeScore(), match.getHomeXG());
        TeamMetrics away = teamMetrics(match.getAwayTeam(), awayAdvanced, match.getAwayScore(), match.getAwayXG());

        if (homeAdvanced == null || awayAdvanced == null) {
            limitations.add("Advanced team metrics are incomplete; tactical comparison only uses available values.");
        }
        if (match.getHomeXG() == null || match.getAwayXG() == null) {
            limitations.add("Match xG is incomplete; xG overperformance cannot be compared reliably.");
        }

        List<PlayerMatchStats> playerRows = playerMatchStatsRepository.findAllByMatchIdOrderByIdAsc(matchId);
        List<PlayerImpact> playerImpacts = playerRows.stream()
                .map(this::playerImpact)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(PlayerImpact::contributionScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .toList();
        if (playerRows.isEmpty()) {
            limitations.add("Player match statistics are unavailable; no individual impact ranking is shown.");
        }

        List<String> evidence = new ArrayList<>();
        addComparison(evidence, "Possession", home.possession(), away.possession(), "%");
        addLowerIsBetter(evidence, "PPDA (lower may indicate more intense pressing)", home.ppda(), away.ppda());
        addComparison(evidence, "Field tilt", home.fieldTilt(), away.fieldTilt(), "%");
        addComparison(evidence, "xG chain", home.xgChain(), away.xgChain(), "");
        addComparison(evidence, "Deep completions", asDecimal(home.deepCompletions()), asDecimal(away.deepCompletions()), "");
        addComparison(evidence, "Final-third entries", asDecimal(home.finalThirdEntries()), asDecimal(away.finalThirdEntries()), "");
        addComparison(evidence, "Shots from box", asDecimal(home.shotsFromBox()), asDecimal(away.shotsFromBox()), "");
        String tacticalSummary = evidence.isEmpty()
                ? "Insufficient advanced team metrics for a reliable tactical comparison."
                : String.join(" ", evidence);

        PostMatchReview review = postMatchReview(match);
        ContentBrief brief = buildBrief(match, home, away, playerImpacts, review, evidence);
        String competition = match.getSeason() != null && match.getSeason().getLeague() != null
                ? match.getSeason().getLeague().getName() : null;

        return new ModernMatchReport(match.getId(), match.getMatchTime(), competition, match.getRoundInfo(),
                match.getStatus() == null ? null : match.getStatus().name(), home, away,
                new MatchComparison(tacticalSummary, evidence), playerImpacts, review, brief, limitations);
    }

    private MatchAdvancedStats findForTeam(List<MatchAdvancedStats> rows, Team team) {
        if (team == null) return null;
        return rows.stream().filter(row -> row.getTeam() != null && Objects.equals(row.getTeam().getId(), team.getId()))
                .findFirst().orElse(null);
    }

    private TeamMetrics teamMetrics(Team team, MatchAdvancedStats stats, Integer goals, Double xg) {
        BigDecimal xgValue = xg == null ? null : decimal(xg);
        BigDecimal goalValue = goals == null ? null : BigDecimal.valueOf(goals);
        return new TeamMetrics(
                team == null ? null : team.getId(),
                team == null ? null : team.getName(),
                stats == null ? null : stats.getPossession(),
                stats == null ? null : stats.getPpda(),
                stats == null ? null : stats.getFieldTilt(),
                stats == null ? null : stats.getXgChain(),
                stats == null ? null : stats.getXgBuildup(),
                stats == null ? null : stats.getSetPieceXg(),
                stats == null ? null : stats.getCounterAttackXg(),
                stats == null ? null : stats.getDeepCompletions(),
                stats == null ? null : stats.getFinalThirdEntries(),
                stats == null ? null : stats.getShotsFromBox(),
                stats == null ? null : stats.getShotsOutsideBox(),
                goalValue, xgValue,
                goalValue == null || xgValue == null ? null : goalValue.subtract(xgValue).setScale(3, RoundingMode.HALF_UP),
                null, null, null, null, null);
    }

    private PlayerImpact playerImpact(PlayerMatchStats row) {
        if (row == null || row.getPlayer() == null) return null;
        BigDecimal xg = row.getXg();
        BigDecimal xa = row.getXa();
        // Transparent editorial ranking heuristic, not a provider-defined rating.
        BigDecimal score = BigDecimal.valueOf(n(row.getGoals()) * 4.0 + n(row.getAssists()) * 3.0
                + n(row.getShotsOnTarget()) * 0.5 + n(row.getKeyPasses()) * 0.4
                + n(row.getTackles()) * 0.2 + n(row.getInterceptions()) * 0.2
                + n(row.getDuelsWon()) * 0.1)
                .add(xg == null ? ZERO : xg.multiply(new BigDecimal("2")))
                .add(xa == null ? ZERO : xa.multiply(new BigDecimal("1.5")))
                .setScale(3, RoundingMode.HALF_UP);
        return new PlayerImpact(row.getPlayer().getId(), row.getPlayer().getName(), row.getPlayer().getPosition(),
                row.getMinutesPlayed(), row.getGoals(), row.getAssists(), xg, xa, row.getShotsOnTarget(),
                row.getKeyPasses(), row.getTackles(), row.getInterceptions(), row.getDuelsWon(),
                row.getYellowCards(), row.getRedCards(), score);
    }

    private PostMatchReview postMatchReview(Match match) {
        BigDecimal homeXg = match.getHomeXG() == null ? null : decimal(match.getHomeXG());
        BigDecimal awayXg = match.getAwayXG() == null ? null : decimal(match.getAwayXG());
        BigDecimal homeGoals = match.getHomeScore() == null ? null : BigDecimal.valueOf(match.getHomeScore());
        BigDecimal awayGoals = match.getAwayScore() == null ? null : BigDecimal.valueOf(match.getAwayScore());
        boolean hasScore = match.getHomeScore() != null && match.getAwayScore() != null;
        String homeName = match.getHomeTeam() == null ? "Home team" : match.getHomeTeam().getName();
        String awayName = match.getAwayTeam() == null ? "Away team" : match.getAwayTeam().getName();
        String result;
        if (!hasScore) {
            result = "Score is unavailable.";
        } else if (match.getStatus() == MatchStatus.FINISHED) {
            result = homeName + " " + match.getHomeScore() + " - " + match.getAwayScore() + " " + awayName;
        } else if (match.getStatus() == MatchStatus.LIVE) {
            result = "Live score: " + homeName + " " + match.getHomeScore() + " - "
                    + match.getAwayScore() + " " + awayName;
        } else {
            result = "Recorded score (match not marked finished): " + homeName + " "
                    + match.getHomeScore() + " - " + match.getAwayScore() + " " + awayName;
        }
        return new PostMatchReview(match.getHomeScore(), match.getAwayScore(), homeXg, awayXg,
                homeGoals == null || homeXg == null ? null : homeGoals.subtract(homeXg).setScale(3, RoundingMode.HALF_UP),
                awayGoals == null || awayXg == null ? null : awayGoals.subtract(awayXg).setScale(3, RoundingMode.HALF_UP),
                result);
    }

    private ContentBrief buildBrief(Match match, TeamMetrics home, TeamMetrics away,
                                    List<PlayerImpact> players, PostMatchReview review, List<String> evidence) {
        List<String> hooks = new ArrayList<>(evidence);
        if (review.homeScore() != null && review.awayScore() != null) hooks.add(review.resultSummary());
        if (review.homeXgOverperformance() != null && review.awayXgOverperformance() != null) {
            hooks.add("Goals minus xG: home " + review.homeXgOverperformance() + ", away " + review.awayXgOverperformance()
                    + " (descriptive only; not proof of luck or finishing skill).");
        }
        if (!players.isEmpty()) {
            PlayerImpact top = players.get(0);
            hooks.add("Highest editorial contribution score: " + top.playerName() + " (" + top.contributionScore()
                    + "); this is a transparent heuristic, not an official player rating.");
        }
        String headline = !evidence.isEmpty() ? "Tactical numbers behind the match"
                : review.homeScore() != null ? "What the scoreline does and does not tell us"
                : "Match preview: form, tactics and key questions";
        String narrative = match.getStatus() != null && match.getStatus().name().equals("FINISHED")
                ? "Post-match review grounded in recorded score, xG and available team/player metrics."
                : "Pre-match or in-progress brief: use available historical/team metrics and clearly label missing live information.";
        return new ContentBrief(headline, narrative, hooks,
                List.of("Facebook: evidence-led match thread", "TikTok/Shorts: 3-stat hook plus one tactical takeaway",
                        "YouTube: tactical breakdown with charts", "Carousel: key numbers and caveats"),
                "Use only populated source-backed fields. Do not infer lineups, injuries, tactics, shots or player ratings when the underlying data is absent.");
    }

    private void addComparison(List<String> out, String label, BigDecimal home, BigDecimal away, String suffix) {
        if (home != null && away != null) {
            String leader = home.compareTo(away) == 0 ? "level" : home.compareTo(away) > 0 ? "home higher" : "away higher";
            out.add(label + ": home " + home.stripTrailingZeros().toPlainString() + suffix + ", away "
                    + away.stripTrailingZeros().toPlainString() + suffix + " (" + leader + ").");
        }
    }

    private void addLowerIsBetter(List<String> out, String label, BigDecimal home, BigDecimal away) {
        if (home != null && away != null) {
            String leader = home.compareTo(away) == 0 ? "level" : home.compareTo(away) < 0 ? "home lower" : "away lower";
            out.add(label + ": home " + home + ", away " + away + " (" + leader + ").");
        }
    }

    private BigDecimal asDecimal(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }

    private int n(Integer value) { return value == null ? 0 : value; }
}
