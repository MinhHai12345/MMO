package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.MatchSocialInsight;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.MarketInsight;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.ContentCandidate;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.PredictionInsight;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.TeamInsight;
import com.mmo.module.fb.analytics.service.MatchSocialAnalyticsService.ContentPlan;
import com.mmo.module.fb.analytics.service.PlayerAnalyticsService;
import com.mmo.module.fb.analytics.service.PostMatchAnalysisService;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService.MatchFeatures;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService.TeamFeatures;
import com.mmo.module.fb.domain.entity.LeagueStanding;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchOdds;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.enums.StandingType;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.repository.LeagueStandingRepository;
import com.mmo.module.fb.domain.repository.MatchOddsRepository;
import com.mmo.module.fb.domain.repository.MatchPredictionRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

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
    private final PlayerAnalyticsService playerAnalyticsService;
    private final PostMatchAnalysisService postMatchAnalysisService;


    @Override
    public List<ContentCandidate> discover(Instant from, Instant to, Long leagueId, int limit) {
        Instant effectiveFrom = from == null ? Instant.now() : from;
        Instant effectiveTo = to == null ? effectiveFrom.plus(Duration.ofDays(7)) : to;
        if (!effectiveTo.isAfter(effectiveFrom)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`to` must be after `from`");
        }
        if (Duration.between(effectiveFrom, effectiveTo).compareTo(Duration.ofDays(60)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date range must not exceed 60 days");
        }

        int boundedLimit = Math.max(1, Math.min(limit, 50));
        List<Match> matches = matchRepository.findSocialInsightCandidates(
                effectiveFrom, effectiveTo, MatchStatus.SCHEDULED, leagueId, PageRequest.of(0, 100));
        List<ContentCandidate> candidates = matches.stream().map(this::toContentCandidate)
                .sorted(Comparator.comparingInt(ContentCandidate::readinessScore).reversed()
                        .thenComparing(ContentCandidate::kickoff))
                .limit(boundedLimit)
                .toList();
        return List.copyOf(candidates);
    }

    private ContentCandidate toContentCandidate(Match match) {
        List<String> factors = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int score = 0;

        if (match.getSeason() != null && match.getSeason().getLeague() != null
                && match.getHomeTeam() != null && match.getAwayTeam() != null) {
            score += 10;
            factors.add("Competition and both teams are linked (+10)");
        } else {
            missing.add("Competition or team metadata is incomplete");
        }

        Optional<MatchFeatures> featureResult = teamFormFeatureService.buildFeatures(match);
        TeamFeatures home = featureResult.map(MatchFeatures::home).orElse(null);
        TeamFeatures away = featureResult.map(MatchFeatures::away).orElse(null);
        int homeMatches = home == null ? 0 : home.matchesUsed();
        int awayMatches = away == null ? 0 : away.matchesUsed();
        int formScore = Math.min(15, homeMatches * 3) + Math.min(15, awayMatches * 3);
        score += formScore;
        if (homeMatches >= 5 && awayMatches >= 5) {
            factors.add("Both teams have at least five usable historical matches (+" + formScore + ")");
        } else {
            missing.add("Historical form sample is limited (home=" + homeMatches + ", away=" + awayMatches + ")");
        }

        int homeVenueMatches = home == null ? 0 : home.venueMatchesUsed();
        int awayVenueMatches = away == null ? 0 : away.venueMatchesUsed();
        int venueScore = Math.min(5, homeVenueMatches) + Math.min(5, awayVenueMatches);
        score += venueScore;
        if (homeVenueMatches >= 3 && awayVenueMatches >= 3) {
            factors.add("Both venue-specific samples contain at least three matches (+" + venueScore + ")");
        } else {
            missing.add("Home/away venue samples are limited (home=" + homeVenueMatches + ", away=" + awayVenueMatches + ")");
        }

        MatchPrediction prediction = matchPredictionRepository.findByMatchId(match.getId()).orElse(null);
        boolean predictionAvailable = prediction != null && prediction.getWinHomeProb() != null
                && prediction.getDrawProb() != null && prediction.getWinAwayProb() != null;
        if (predictionAvailable) {
            score += 20;
            factors.add("Complete persisted 1X2 model probabilities are available (+20)");
        } else {
            missing.add("Complete model probabilities are unavailable");
        }

        boolean marketAvailable = marketInsight(matchOddsRepository.findAllByMatchId(match.getId())) != null;
        if (marketAvailable) {
            score += 10;
            factors.add("A complete 1X2 market row is available (+10)");
        } else {
            missing.add("Complete 1X2 market data is unavailable");
        }

        boolean xgAvailable = home != null && home.averageXgFor() != null && home.averageXgAgainst() != null
                && away != null && away.averageXgFor() != null && away.averageXgAgainst() != null;
        if (xgAvailable) {
            score += 20;
            factors.add("Historical xG-for and xG-against are available for both teams (+20)");
        } else {
            missing.add("Historical team xG is incomplete; use score-derived indicators only");
        }

        String level = score >= 75 ? "READY" : score >= 45 ? "PARTIAL" : "LIMITED";
        return new ContentCandidate(
                match.getId(), match.getMatchTime(),
                match.getSeason() != null && match.getSeason().getLeague() != null
                        ? match.getSeason().getLeague().getName() : null,
                match.getSeason() == null ? null : match.getSeason().getYear(),
                match.getRoundInfo(), match.getStatus() == null ? null : match.getStatus().name(),
                match.getHomeTeam() == null ? null : match.getHomeTeam().getId(),
                match.getHomeTeam() == null ? null : match.getHomeTeam().getName(),
                match.getAwayTeam() == null ? null : match.getAwayTeam().getId(),
                match.getAwayTeam() == null ? null : match.getAwayTeam().getName(),
                score, level, homeMatches, awayMatches, predictionAvailable, marketAvailable,
                xgAvailable, factors, missing);
    }

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
        TeamInsight home = teamInsight(match.getHomeTeam(), match, features.map(MatchFeatures::home).orElse(null), warnings);
        TeamInsight away = teamInsight(match.getAwayTeam(), match, features.map(MatchFeatures::away).orElse(null), warnings);

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
        if ((match.getVenue() == null || match.getVenue().isBlank())
                && (match.getHomeTeam() == null || match.getHomeTeam().getStadiumName() == null
                || match.getHomeTeam().getStadiumName().isBlank())) {
            warnings.add("Venue is unavailable from both the fixture and the home-team stadium metadata.");
        }

        PlayerAnalyticsService.PlayerMatchInsight playerAnalytics =
                match.getStatus() == MatchStatus.FINISHED ? playerAnalyticsService.analyzeMatch(matchId) : null;
        PostMatchAnalysisService.PostMatchInsight postMatch =
                match.getStatus() == MatchStatus.FINISHED ? postMatchAnalysisService.analyze(matchId) : null;
        ContentPlan contentPlan = buildContentPlan(match, angles, warnings, postMatch);

        return new MatchSocialInsight(
                match.getId(),
                match.getMatchTime(),
                match.getSeason() != null && match.getSeason().getLeague() != null
                        ? match.getSeason().getLeague().getName() : null,
                match.getSeason() != null ? match.getSeason().getYear() : null,
                match.getRoundInfo(),
                match.getVenue() != null && !match.getVenue().isBlank()
                        ? match.getVenue()
                        : match.getHomeTeam() == null ? null : match.getHomeTeam().getStadiumName(),
                match.getStatus() != null ? match.getStatus().name() : null,
                home,
                away,
                prediction,
                market,
                matchupInsight(home, away, angles, warnings),
                playerAnalytics,
                postMatch,
                contentPlan,
                angles,
                warnings);
    }


    private ContentPlan buildContentPlan(Match match, List<String> preMatchAngles,
                                         List<String> warnings,
                                         PostMatchAnalysisService.PostMatchInsight postMatch) {
        boolean finished = match.getStatus() == MatchStatus.FINISHED
                && match.getHomeScore() != null && match.getAwayScore() != null;
        String phase = finished ? "POST_MATCH"
                : match.getStatus() == MatchStatus.LIVE ? "LIVE" : "PRE_MATCH";
        List<String> postAngles = postMatch == null ? List.of() : postMatch.editorialAngles();
        List<String> longAngles = new ArrayList<>();
        if (!finished) {
            longAngles.addAll(preMatchAngles.stream().limit(4).toList());
            longAngles.add("Compare both teams' recent form and venue-specific samples, clearly stating sample sizes.");
            longAngles.add("Separate model probabilities from facts and avoid presenting any outcome as certain.");
        } else {
            longAngles.addAll(postMatch == null ? List.of() : postMatch.evidenceBasedFindings());
            longAngles.addAll(postAngles.stream().limit(4).toList());
            if (postMatch != null && postMatch.playerAnalytics() != null) {
                postMatch.playerAnalytics().standoutPlayers().stream().limit(2).forEach(player ->
                        longAngles.add("Player segment: " + player.playerName() + " — " + String.join(", ", player.evidence())));
            }
        }
        List<String> shorts = new ArrayList<>();
        if (finished) {
            shorts.add(match.getHomeTeam().getName() + " " + match.getHomeScore() + "–"
                    + match.getAwayScore() + " " + match.getAwayTeam().getName() + ": the key takeaway.");
            if (match.getHomeXG() != null && match.getAwayXG() != null) {
                shorts.add("The score was " + match.getHomeScore() + "–" + match.getAwayScore()
                        + ", while xG was " + decimal(match.getHomeXG()) + "–" + decimal(match.getAwayXG())
                        + ". What does that tell us?");
            }
            if (postMatch != null && postMatch.playerAnalytics() != null
                    && !postMatch.playerAnalytics().standoutPlayers().isEmpty()) {
                PlayerAnalyticsService.PlayerPerformance leader = postMatch.playerAnalytics().standoutPlayers().get(0);
                if (leader.playerName() != null) {
                    shorts.add("Player spotlight: " + leader.playerName() + " — "
                            + String.join(", ", leader.evidence()) + ".");
                }
            }
        } else {
            shorts.addAll(preMatchAngles.stream().limit(2).map(a -> "Pre-match question: " + a).toList());
        }
        List<String> caveats = new ArrayList<>(warnings);
        if (!finished) caveats.add("This is a pre-match content plan; final score and post-match statistics are not yet available.");
        if (finished && (match.getHomeXG() == null || match.getAwayXG() == null)) {
            caveats.add("Do not use xG comparison in the video until both teams' match xG values are available.");
        }
        return new ContentPlan(phase,
                finished ? List.of() : preMatchAngles,
                postAngles, longAngles.stream().distinct().limit(8).toList(),
                shorts.stream().filter(s -> s != null && !s.isBlank()).distinct().limit(3).toList(),
                caveats.stream().distinct().toList());
    }

    private TeamInsight teamInsight(Team team, Match match, TeamFeatures features, List<String> warnings) {
        LeagueStanding standing = null;
        boolean upcomingScheduledMatch = match.getStatus() == MatchStatus.SCHEDULED
                && match.getMatchTime() != null
                && match.getMatchTime().isAfter(Instant.now());

        if (upcomingScheduledMatch && match.getSeason() != null && team != null) {
            standing = leagueStandingRepository.findBySeasonIdAndTeamIdAndStandingType(
                    match.getSeason().getId(), team.getId(), StandingType.TOTAL).orElse(null);
            if (standing != null && !isInternallyConsistent(standing)) {
                warnings.add("Standings row for " + team.getName()
                        + " is internally inconsistent; position, points and standings form are omitted.");
                standing = null;
            }
        } else {
            warnings.add("Current standings are omitted for " + (team == null ? "a team" : team.getName())
                    + " because they are not a point-in-time snapshot for this match kickoff.");
        }

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
                decimal(features == null ? null : features.averageXgAgainst()),
                decimal(features == null ? null : features.venueAverageGoalsFor()),
                decimal(features == null ? null : features.venueAverageGoalsAgainst()));
    }

    private boolean isInternallyConsistent(LeagueStanding standing) {
        if (standing.getWon() != null && standing.getDrawn() != null
                && standing.getPoints() != null
                && standing.getPoints() != standing.getWon() * 3 + standing.getDrawn()) {
            return false;
        }
        if (standing.getPlayed() != null && standing.getWon() != null
                && standing.getDrawn() != null && standing.getLost() != null
                && standing.getPlayed() != standing.getWon() + standing.getDrawn() + standing.getLost()) {
            return false;
        }
        return standing.getGoalsFor() == null || standing.getGoalsAgainst() == null
                || standing.getGoalDifference() == null
                || standing.getGoalDifference().equals(standing.getGoalsFor() - standing.getGoalsAgainst());
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


    private MatchupInsight matchupInsight(TeamInsight home, TeamInsight away,
                                          List<String> angles, List<String> warnings) {
        List<MatchupSignal> signals = new ArrayList<>();

        addMatchupSignal(signals, "HOME_ATTACK_VS_AWAY_DEFENCE",
                home.averageGoalsFor(), away.averageGoalsAgainst(),
                "Home attack scoring rate compared with away goals conceded",
                home.matchesAnalyzed(), away.matchesAnalyzed());
        addMatchupSignal(signals, "AWAY_ATTACK_VS_HOME_DEFENCE",
                away.averageGoalsFor(), home.averageGoalsAgainst(),
                "Away attack scoring rate compared with home goals conceded",
                away.matchesAnalyzed(), home.matchesAnalyzed());

        if (home.venueAverageGoalsFor() != null && away.venueAverageGoalsAgainst() != null) {
            addMatchupSignal(signals, "HOME_VENUE_ATTACK_VS_AWAY_VENUE_DEFENCE",
                    home.venueAverageGoalsFor(), away.venueAverageGoalsAgainst(),
                    "Home team's home scoring rate compared with away team's away concession rate",
                    home.venueMatchesAnalyzed(), away.venueMatchesAnalyzed());
        }
        if (away.venueAverageGoalsFor() != null && home.venueAverageGoalsAgainst() != null) {
            addMatchupSignal(signals, "AWAY_VENUE_ATTACK_VS_HOME_VENUE_DEFENCE",
                    away.venueAverageGoalsFor(), home.venueAverageGoalsAgainst(),
                    "Away team's away scoring rate compared with home team's home concession rate",
                    away.venueMatchesAnalyzed(), home.venueMatchesAnalyzed());
        }

        boolean xgAvailable = home.averageXgFor() != null && home.averageXgAgainst() != null
                && away.averageXgFor() != null && away.averageXgAgainst() != null;
        if (xgAvailable) {
            addMatchupSignal(signals, "HOME_XG_ATTACK_VS_AWAY_XG_DEFENCE",
                    home.averageXgFor(), away.averageXgAgainst(),
                    "Historical xG-for compared with opponent xG-against",
                    home.matchesAnalyzed(), away.matchesAnalyzed());
            addMatchupSignal(signals, "AWAY_XG_ATTACK_VS_HOME_XG_DEFENCE",
                    away.averageXgFor(), home.averageXgAgainst(),
                    "Historical xG-for compared with opponent xG-against",
                    away.matchesAnalyzed(), home.matchesAnalyzed());
        } else {
            warnings.add("Tactical matchup is limited to score-derived indicators because historical team xG is incomplete.");
        }

        long attackRateHigher = signals.stream()
                .filter(signal -> "ATTACK_RATE_HIGHER".equals(signal.edge()))
                .count();
        long concessionRateHigher = signals.stream()
                .filter(signal -> "OPPONENT_CONCESSION_RATE_HIGHER".equals(signal.edge()))
                .count();
        long similarRates = signals.stream()
                .filter(signal -> "SIMILAR_RATES".equals(signal.edge()))
                .count();

        String summary;
        if (signals.isEmpty()) {
            summary = "Insufficient comparable historical metrics to describe this matchup.";
        } else if (attackRateHigher == signals.size()) {
            summary = "Across all available comparisons, attack rates are higher than the corresponding opponent concession averages. This describes historical rates, not a match-result prediction.";
        } else if (concessionRateHigher == signals.size()) {
            summary = "Across all available comparisons, opponent concession averages are higher than the corresponding attack rates. This does not by itself establish a match-result advantage.";
        } else if (similarRates == signals.size()) {
            summary = "Attack rates and opponent concession averages are similar across the available comparisons; these indicators do not establish a match-result advantage.";
        } else {
            summary = "Available comparisons are mixed: " + attackRateHigher
                    + " attack-rate comparison(s) are higher, " + concessionRateHigher
                    + " opponent-concession comparison(s) are higher, and " + similarRates
                    + " are similar. These are historical rate comparisons, not a match-result prediction.";
        }
        angles.add("Statistical matchup compares historical attack and concession rates; formation, pressing and player-level tactical data are not available in this analysis.");
        return new MatchupInsight(summary, xgAvailable, signals);
    }

    private void addMatchupSignal(List<MatchupSignal> signals, String dimension,
                                  BigDecimal attackRate, BigDecimal opponentConcessionRate,
                                  String evidence, int attackSamples, int defenceSamples) {
        if (attackRate == null || opponentConcessionRate == null) {
            return;
        }
        BigDecimal difference = attackRate.subtract(opponentConcessionRate);
        BigDecimal threshold = new BigDecimal("0.150");
        String edge = difference.compareTo(threshold) > 0 ? "ATTACK_RATE_HIGHER"
                : difference.compareTo(threshold.negate()) < 0
                ? "OPPONENT_CONCESSION_RATE_HIGHER" : "SIMILAR_RATES";
        String confidence = attackSamples >= 8 && defenceSamples >= 8 ? "MODERATE"
                : attackSamples >= 4 && defenceSamples >= 4 ? "LOW" : "VERY_LOW";
        signals.add(new MatchupSignal(dimension, edge,
                evidence + ": " + attackRate.toPlainString() + " vs " + opponentConcessionRate.toPlainString()
                        + " per match; samples=" + attackSamples + "/" + defenceSamples,
                confidence));
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
