package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.engine.PoissonProbabilityCalculator;
import com.mmo.module.fb.analytics.service.MatchHistoryAnalyticsService;
import com.mmo.module.fb.analytics.service.MatchHistoryAnalyticsService.ExpectedGoals;
import com.mmo.module.fb.analytics.service.PreMatchAnalysisService;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService.MatchFeatures;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService.TeamFeatures;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.enums.MatchStatus;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreMatchAnalysisServiceImpl implements PreMatchAnalysisService {

    private static final int HEAD_TO_HEAD_HISTORY_SIZE = 50;
    private static final int HEAD_TO_HEAD_RESPONSE_SIZE = 10;
    private static final Duration RESULT_AVAILABILITY_LAG = Duration.ofHours(4);
    private static final int MIN_FORM_MATCHES = 5;
    private static final int MIN_MODEL_SAMPLES = 5;

    private final MatchRepository matchRepository;
    private final TeamFormFeatureService teamFormFeatureService;
    private final MatchHistoryAnalyticsService matchHistoryAnalyticsService;

    @Override
    public PreMatchReport analyze(Long matchId) {
        if (matchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "matchId is required");
        }

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Match not found: " + matchId));

        validateTarget(match);

        Instant historyCutoff = match.getMatchTime().minus(RESULT_AVAILABILITY_LAG);
        Optional<MatchFeatures> features = teamFormFeatureService.buildFeatures(match);
        TeamForm home = features.map(value -> teamForm(value.home(), match.getHomeTeam().getName()))
                .orElse(null);
        TeamForm away = features.map(value -> teamForm(value.away(), match.getAwayTeam().getName()))
                .orElse(null);

        HeadToHead headToHead = buildHeadToHead(match, historyCutoff);
        GoalModel goalModel = matchHistoryAnalyticsService.estimateFromGoals(match)
                .map(this::goalModel).orElse(null);
        GoalModel xgModel = matchHistoryAnalyticsService.estimateFromExpectedGoals(match)
                .map(this::goalModel).orElse(null);

        List<String> warnings = new ArrayList<>();
        if (home == null || away == null) {
            warnings.add("TEAM_FORM_UNAVAILABLE: team form features could not be built for this fixture.");
        } else {
            addFormWarning(warnings, "HOME", home);
            addFormWarning(warnings, "AWAY", away);
        }
        if (headToHead.matchesUsed() == 0) {
            warnings.add("NO_HEAD_TO_HEAD: no previous scored meetings were found in this competition.");
        } else if (headToHead.matchesUsed() < 3) {
            warnings.add("LIMITED_HEAD_TO_HEAD: fewer than three previous meetings are available.");
        }
        if (goalModel == null) {
            warnings.add("GOALS_MODEL_UNAVAILABLE: insufficient team or competition goal history.");
        } else if (Math.min(goalModel.homeSamples(), goalModel.awaySamples()) < MIN_MODEL_SAMPLES) {
            warnings.add("GOALS_MODEL_LIMITED: at least one side has fewer than five effective input samples.");
        }
        if (xgModel == null) {
            warnings.add("XG_MODEL_UNAVAILABLE: insufficient historical xG coverage for both teams and competition.");
        } else if (Math.min(xgModel.homeSamples(), xgModel.awaySamples()) < MIN_MODEL_SAMPLES) {
            warnings.add("XG_MODEL_LIMITED: xG model has fewer than five input samples for at least one side.");
        }
        if (match.getStatus() == MatchStatus.FINISHED) {
            warnings.add("MATCH_ALREADY_FINISHED: this report is a retrospective pre-kickoff reconstruction.");
        }

        String quality = determineQuality(home, away, goalModel, xgModel);
        List<String> notes = List.of(
                "Team form, head-to-head and goal/xG estimates use completed matches before kickoff with a four-hour result-availability buffer.",
                "Goal model estimates scoring rates from historical goals; xG model estimates rates from historical expected goals. Their probabilities are separate model outputs.",
                "Head-to-head is contextual evidence only and is not blended into the model probabilities.",
                "Poisson probabilities are not calibrated probabilities and must be evaluated out of sample before being used for automated decisions.",
                "This analysis is for football insight and content generation, not betting advice."
        );

        String competition = match.getSeason() != null && match.getSeason().getLeague() != null
                ? match.getSeason().getLeague().getName() : null;

        return new PreMatchReport(
                match.getId(),
                match.getMatchTime(),
                competition,
                match.getRoundInfo(),
                match.getStatus() == null ? null : match.getStatus().name(),
                home,
                away,
                headToHead,
                goalModel,
                xgModel,
                quality,
                warnings,
                notes
        );
    }

    private void validateTarget(Match match) {
        if (match.getMatchTime() == null
                || match.getHomeTeam() == null
                || match.getAwayTeam() == null
                || match.getHomeTeam().getId() == null
                || match.getAwayTeam().getId() == null
                || match.getHomeTeam().getId().equals(match.getAwayTeam().getId())
                || match.getSeason() == null
                || match.getSeason().getLeague() == null
                || match.getSeason().getLeague().getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Match must have kickoff, two distinct teams and a competition before analysis");
        }
    }

    private TeamForm teamForm(TeamFeatures features, String teamName) {
        return new TeamForm(
                features.teamId(),
                teamName,
                features.matchesUsed(),
                features.venueMatchesUsed(),
                features.averageGoalsFor(),
                features.averageGoalsAgainst(),
                features.averagePoints(),
                features.venueAverageGoalsFor(),
                features.venueAverageGoalsAgainst(),
                features.winRate(),
                features.cleanSheetRate(),
                features.scoredRate(),
                features.averageXgFor(),
                features.averageXgAgainst(),
                features.recentForm()
        );
    }

    private HeadToHead buildHeadToHead(Match target, Instant before) {
        Long leagueId = target.getSeason().getLeague().getId();
        Long homeId = target.getHomeTeam().getId();
        Long awayId = target.getAwayTeam().getId();

        List<Match> homeHistory = matchRepository.findRecentFinishedMatchesForTeamInLeagueBefore(
                homeId, leagueId, MatchStatus.FINISHED, before,
                PageRequest.of(0, HEAD_TO_HEAD_HISTORY_SIZE));

        List<Match> meetings = (homeHistory == null ? List.<Match>of() : homeHistory).stream()
                .filter(previous -> isMeeting(previous, homeId, awayId))
                .toList();

        int homeWins = 0;
        int draws = 0;
        int awayWins = 0;
        int goals = 0;
        List<HeadToHeadMatch> recent = new ArrayList<>();

        for (Match previous : meetings) {
            Integer homeScore = previous.getHomeScore();
            Integer awayScore = previous.getAwayScore();
            if (homeScore == null || awayScore == null) {
                continue;
            }
            boolean targetHomeWon = previous.getHomeTeam().getId().equals(homeId)
                    ? homeScore > awayScore : awayScore > homeScore;
            boolean targetAwayWon = previous.getHomeTeam().getId().equals(awayId)
                    ? homeScore > awayScore : awayScore > homeScore;

            if (homeScore.equals(awayScore)) {
                draws++;
            } else if (targetHomeWon) {
                homeWins++;
            } else if (targetAwayWon) {
                awayWins++;
            }

            goals += homeScore + awayScore;
            if (recent.size() < HEAD_TO_HEAD_RESPONSE_SIZE) {
                recent.add(new HeadToHeadMatch(
                        previous.getId(),
                        previous.getMatchTime(),
                        previous.getHomeTeam().getName(),
                        previous.getAwayTeam().getName(),
                        homeScore,
                        awayScore
                ));
            }
        }

        int count = homeWins + draws + awayWins;
        String summary = count == 0
                ? "No previous scored meetings found in the available competition history."
                : "In " + count + " meetings: " + target.getHomeTeam().getName() + " won "
                    + homeWins + ", drew " + draws + ", " + target.getAwayTeam().getName()
                    + " won " + awayWins + ".";

        return new HeadToHead(count, homeWins, draws, awayWins,
                count == 0 ? 0.0 : (double) goals / count, summary, recent);
    }

    private boolean isMeeting(Match previous, Long homeId, Long awayId) {
        if (previous.getHomeTeam() == null || previous.getAwayTeam() == null) {
            return false;
        }
        Long previousHomeId = previous.getHomeTeam().getId();
        Long previousAwayId = previous.getAwayTeam().getId();
        return (homeId.equals(previousHomeId) && awayId.equals(previousAwayId))
                || (homeId.equals(previousAwayId) && awayId.equals(previousHomeId));
    }

    private GoalModel goalModel(ExpectedGoals estimate) {
        PoissonProbabilityCalculator.Result probabilities =
                PoissonProbabilityCalculator.calculate(estimate.home(), estimate.away());
        return new GoalModel(
                estimate.home(),
                estimate.away(),
                estimate.homeSamples(),
                estimate.awaySamples(),
                probabilities.homeWin(),
                probabilities.draw(),
                probabilities.awayWin(),
                outcome(probabilities.homeWin(), probabilities.draw(), probabilities.awayWin())
        );
    }

    private String outcome(double home, double draw, double away) {
        if (home > draw && home > away) {
            return "HOME";
        }
        if (away > draw && away > home) {
            return "AWAY";
        }
        return "DRAW";
    }

    private void addFormWarning(List<String> warnings, String side, TeamForm form) {
        if (form.matchesUsed() < MIN_FORM_MATCHES) {
            warnings.add(side + "_FORM_LIMITED: only " + form.matchesUsed()
                    + " completed league matches are available for recent form.");
        }
        if (form.venueMatchesUsed() < 3) {
            warnings.add(side + "_VENUE_HISTORY_LIMITED: fewer than three matches are available"
                    + " for the target home/away venue split.");
        }
        if (form.averageXgFor() == null || form.averageXgAgainst() == null) {
            warnings.add(side + "_XG_HISTORY_UNAVAILABLE: recent team form has no usable paired xG samples.");
        }
    }

    private String determineQuality(TeamForm home, TeamForm away, GoalModel goals, GoalModel xg) {
        if (home == null || away == null || goals == null) {
            return "INSUFFICIENT";
        }
        if (home.matchesUsed() < MIN_FORM_MATCHES || away.matchesUsed() < MIN_FORM_MATCHES
                || home.venueMatchesUsed() < 3 || away.venueMatchesUsed() < 3
                || Math.min(goals.homeSamples(), goals.awaySamples()) < MIN_MODEL_SAMPLES) {
            return "LIMITED";
        }
        if (xg == null || home.averageXgFor() == null || away.averageXgFor() == null) {
            return "GOALS_ONLY";
        }
        if (Math.min(xg.homeSamples(), xg.awaySamples()) < MIN_MODEL_SAMPLES) {
            return "LIMITED_XG";
        }
        return "GOOD";
    }
}
