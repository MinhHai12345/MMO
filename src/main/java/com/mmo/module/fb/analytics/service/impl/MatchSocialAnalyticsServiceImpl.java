package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.MatchSocialInsight;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.MarketInsight;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.PredictionInsight;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.TeamInsight;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService.MatchFeatures;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService.TeamFeatures;
import com.mmo.module.fb.domain.entity.LeagueStanding;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchOdds;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.enums.StandingType;
import com.mmo.module.fb.domain.repository.LeagueStandingRepository;
import com.mmo.module.fb.domain.repository.MatchOddsRepository;
import com.mmo.module.fb.domain.repository.MatchPredictionRepository;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchSocialAnalyticsServiceImpl implements MatchSocialAnalyticsService {

    private static final int SCALE = 3;

    private final MatchRepository matchRepository;
    private final MatchPredictionRepository matchPredictionRepository;
    private final TeamFormFeatureService teamFormFeatureService;
    private final LeagueStandingRepository leagueStandingRepository;
    private final MatchOddsRepository matchOddsRepository;

    @Override
    public MatchSocialInsight analyze(Long matchId) {
        if (matchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "matchId is required");
        }

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Match not found: " + matchId));

        List<String> warnings = new ArrayList<>();
        List<String> angles = new ArrayList<>();

        Optional<MatchFeatures> features = teamFormFeatureService.buildFeatures(match);
        TeamInsight home = teamInsight(match.getHomeTeam(), match, features.map(MatchFeatures::home).orElse(null));
        TeamInsight away = teamInsight(match.getAwayTeam(), match, features.map(MatchFeatures::away).orElse(null));

        if (features.isEmpty()) {
            warnings.add("Insufficient valid historical data to calculate recent-form features.");
        } else {
            addFormAngles(home, away, angles);
        }

        PredictionInsight prediction = matchPredictionRepository.findByMatchId(matchId)
                .map(this::predictionInsight).orElse(null);
        if (prediction == null || prediction.homeWinProbability() == null
                || prediction.drawProbability() == null || prediction.awayWinProbability() == null) {
            warnings.add("No complete 1X2 model probabilities are available for this match.");
        } else {
            addPredictionAngle(prediction, home, away, angles);
        }

        MarketInsight market = marketInsight(matchOddsRepository.findAllByMatchId(matchId));
        if (market == null) {
            warnings.add("No valid 1X2 odds market is available; market comparison is omitted.");
        } else if (prediction != null && prediction.homeWinProbability() != null
                && prediction.drawProbability() != null && prediction.awayWinProbability() != null) {
            addModelMarketAngle(prediction, market, angles);
        }

        if (match.getHomeXG() == null || match.getAwayXG() == null) {
            warnings.add("Match-level xG is incomplete; do not publish xG-based claims as facts.");
        }

        return new MatchSocialInsight(
                match.getId(),
                match.getMatchTime(),
                match.getSeason() != null && match.getSeason().getLeague() != null
                        ? match.getSeason().getLeague().getName() : null,
                match.getSeason() != null ? match.getSeason().getYear() : null,
                match.getRoundInfo(),
                match.getVenue(),
                match.getStatus() != null ? match.getStatus().name() : null,
                home,
                away,
                prediction,
                market,
                angles,
                warnings);
    }

    private TeamInsight teamInsight(Team team, Match match, TeamFeatures features) {
        LeagueStanding standing = match.getSeason() == null || team == null
                ? null
                : leagueStandingRepository.findBySeasonIdAndTeamIdAndStandingType(
                        match.getSeason().getId(), team.getId(), StandingType.TOTAL).orElse(null);

        return new TeamInsight(
                team == null ? null : team.getId(),
                team == null ? null : team.getName(),
                team == null ? null : team.getLogoUrl(),
                standing == null ? null : standing.getPosition(),
                standing == null ? null : standing.getPoints(),
                standing == null ? null : standing.getForm(),
                features == null ? null : features.recentForm(),
                features == null ? 0 : features.matchesUsed(),
                features == null ? 0 : features.venueMatchesUsed(),
                decimal(features == null ? null : features.averageGoalsFor()),
                decimal(features == null ? null : features.averageGoalsAgainst()),
                decimal(features == null ? null : features.averagePoints()),
                decimal(features == null ? null : features.winRate()),
                decimal(features == null ? null : features.cleanSheetRate()),
                decimal(features == null ? null : features.scoredRate()),
                decimal(features == null ? null : features.averageXgFor()),
                decimal(features == null ? null : features.averageXgAgainst()));
    }

    private PredictionInsight predictionInsight(MatchPrediction prediction) {
        return new PredictionInsight(
                prediction.getPredictionStatus(),
                prediction.getPredictedResult(),
                prediction.getWinHomeProb(),
                prediction.getDrawProb(),
                prediction.getWinAwayProb(),
                prediction.getConfidenceScore(),
                prediction.getPredictionReasoning(),
                prediction.getCalculatedAt());
    }

    private MarketInsight marketInsight(List<MatchOdds> oddsRows) {
        if (oddsRows == null || oddsRows.isEmpty()) {
            return null;
        }

        // Use one complete 1X2 market only; never mix prices from different bookmakers/markets.
        Optional<MatchOdds> selected = oddsRows.stream()
                .filter(o -> positive(o.getHomeWinOdds()) && positive(o.getDrawOdds()) && positive(o.getAwayWinOdds()))
                .sorted(Comparator.comparing(MatchOdds::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .findFirst();
        if (selected.isEmpty()) {
            return null;
        }

        MatchOdds odds = selected.get();
        double home = 1.0 / odds.getHomeWinOdds().doubleValue();
        double draw = 1.0 / odds.getDrawOdds().doubleValue();
        double away = 1.0 / odds.getAwayWinOdds().doubleValue();
        double total = home + draw + away;
        if (!Double.isFinite(total) || total <= 0) {
            return null;
        }

        return new MarketInsight(odds.getHomeWinOdds(), odds.getDrawOdds(), odds.getAwayWinOdds(),
                decimal(home / total), decimal(draw / total), decimal(away / total),
                decimal(total - 1.0));
    }

    private void addFormAngles(TeamInsight home, TeamInsight away, List<String> angles) {
        if (home.recentForm() != null && !home.recentForm().isBlank()) {
            angles.add("Home recent form: " + home.recentForm());
        }
        if (away.recentForm() != null && !away.recentForm().isBlank()) {
            angles.add("Away recent form: " + away.recentForm());
        }
        if (home.averageGoalsFor() != null && away.averageGoalsAgainst() != null
                && home.averageGoalsFor().compareTo(away.averageGoalsAgainst()) > 0) {
            angles.add("Home scoring output is above away defensive concession rate in the available sample.");
        }
        if (away.averageGoalsFor() != null && home.averageGoalsAgainst() != null
                && away.averageGoalsFor().compareTo(home.averageGoalsAgainst()) > 0) {
            angles.add("Away scoring output is above home defensive concession rate in the available sample.");
        }
        if (home.winRate() != null && away.winRate() != null
                && home.winRate().subtract(away.winRate()).abs().compareTo(new BigDecimal("0.200")) >= 0) {
            angles.add("Recent win-rate gap is at least 20 percentage points; verify sample sizes before publishing.");
        }
    }

    private void addPredictionAngle(PredictionInsight prediction, TeamInsight home, TeamInsight away,
                                    List<String> angles) {
        BigDecimal homeProb = prediction.homeWinProbability();
        BigDecimal drawProb = prediction.drawProbability();
        BigDecimal awayProb = prediction.awayWinProbability();
        BigDecimal max = List.of(homeProb, drawProb, awayProb).stream().max(BigDecimal::compareTo).orElseThrow();
        if (max.compareTo(new BigDecimal("0.55")) < 0) {
            angles.add("Model probabilities indicate a relatively balanced outcome distribution.");
        } else if (max.equals(homeProb)) {
            angles.add("Model favours the home-win outcome; publish as a probability, not a certainty.");
        } else if (max.equals(awayProb)) {
            angles.add("Model favours the away-win outcome; publish as a probability, not a certainty.");
        } else {
            angles.add("Model assigns the largest probability to a draw; avoid framing it as a guaranteed result.");
        }
    }

    private void addModelMarketAngle(PredictionInsight prediction, MarketInsight market, List<String> angles) {
        BigDecimal difference = prediction.homeWinProbability().subtract(market.homeImpliedProbability()).abs();
        BigDecimal drawDifference = prediction.drawProbability().subtract(market.drawImpliedProbability()).abs();
        BigDecimal awayDifference = prediction.awayWinProbability().subtract(market.awayImpliedProbability()).abs();
        BigDecimal max = List.of(difference, drawDifference, awayDifference).stream()
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        if (max.compareTo(new BigDecimal("0.080")) >= 0) {
            angles.add("Model and normalized market probabilities differ by at least 8 percentage points on one outcome; treat as a discussion angle, not a betting recommendation.");
        }
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private BigDecimal decimal(Double value) {
        return value == null || !Double.isFinite(value) ? null
                : BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(double value) {
        return Double.isFinite(value) ? BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP) : null;
    }
}
