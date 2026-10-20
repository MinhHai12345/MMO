package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.TeamFormFeatureService;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService.MatchFeatures;
import com.mmo.module.fb.analytics.service.TeamFormFeatureService.TeamFeatures;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamFormFeatureServiceImpl implements TeamFormFeatureService {

    private static final int HISTORY_SIZE = 10;
    private static final int VENUE_HISTORY_SIZE = 5;

    private final MatchRepository matchRepository;

    @Override
    public Optional<MatchFeatures> buildFeatures(Match targetMatch) {
        if (!validTarget(targetMatch)) {
            return Optional.empty();
        }

        Instant kickoff = targetMatch.getMatchTime();
        Long leagueId = targetMatch.getSeason().getLeague().getId();
        List<Match> homeHistory = history(targetMatch.getHomeTeam(), leagueId, kickoff);
        List<Match> awayHistory = history(targetMatch.getAwayTeam(), leagueId, kickoff);

        return Optional.of(new MatchFeatures(
                targetMatch.getId(),
                kickoff,
                summarize(targetMatch.getHomeTeam(), homeHistory, true),
                summarize(targetMatch.getAwayTeam(), awayHistory, false)
        ));
    }

    private List<Match> history(Team team, Long leagueId, Instant kickoff) {
        List<Match> matches = matchRepository.findRecentFinishedMatchesForTeamInLeagueBefore(
                team.getId(), leagueId, MatchStatus.FINISHED, kickoff, PageRequest.of(0, HISTORY_SIZE));
        return matches == null ? List.of() : matches;
    }

    private TeamFeatures summarize(Team team, List<Match> matches, boolean targetHome) {
        double goalsFor = 0;
        double goalsAgainst = 0;
        double points = 0;
        int wins = 0;
        int cleanSheets = 0;
        int scored = 0;
        int xgSamples = 0;
        double xgFor = 0;
        double xgAgainst = 0;
        StringBuilder form = new StringBuilder();
        List<Match> venueMatches = new ArrayList<>();

        for (Match match : matches) {
            boolean teamWasHome = team.getId().equals(match.getHomeTeam().getId());
            int gf = teamWasHome ? match.getHomeScore() : match.getAwayScore();
            int ga = teamWasHome ? match.getAwayScore() : match.getHomeScore();

            goalsFor += gf;
            goalsAgainst += ga;
            if (gf > ga) {
                points += 3;
                wins++;
                form.append('W');
            } else if (gf == ga) {
                points += 1;
                form.append('D');
            } else {
                form.append('L');
            }
            if (ga == 0) cleanSheets++;
            if (gf > 0) scored++;

            Double teamXg = teamWasHome ? match.getHomeXG() : match.getAwayXG();
            Double opponentXg = teamWasHome ? match.getAwayXG() : match.getHomeXG();
            if (validXg(teamXg) && validXg(opponentXg)) {
                xgFor += teamXg;
                xgAgainst += opponentXg;
                xgSamples++;
            }

            if (teamWasHome == targetHome && venueMatches.size() < VENUE_HISTORY_SIZE) {
                venueMatches.add(match);
            }
        }

        double venueGoalsFor = 0;
        double venueGoalsAgainst = 0;
        for (Match match : venueMatches) {
            boolean teamWasHome = team.getId().equals(match.getHomeTeam().getId());
            venueGoalsFor += teamWasHome ? match.getHomeScore() : match.getAwayScore();
            venueGoalsAgainst += teamWasHome ? match.getAwayScore() : match.getHomeScore();
        }

        int count = matches.size();
        int venueCount = venueMatches.size();
        return new TeamFeatures(
                team.getId(),
                count,
                venueCount,
                count == 0 ? 0 : goalsFor / count,
                count == 0 ? 0 : goalsAgainst / count,
                count == 0 ? 0 : points / count,
                count == 0 ? 0 : (double) wins / count,
                count == 0 ? 0 : (double) cleanSheets / count,
                count == 0 ? 0 : (double) scored / count,
                xgSamples == 0 ? null : xgFor / xgSamples,
                xgSamples == 0 ? null : xgAgainst / xgSamples,
                form.toString()
        );
    }

    private boolean validTarget(Match match) {
        return match != null
                && match.getMatchTime() != null
                && match.getHomeTeam() != null
                && match.getAwayTeam() != null
                && match.getHomeTeam().getId() != null
                && match.getAwayTeam().getId() != null
                && !match.getHomeTeam().getId().equals(match.getAwayTeam().getId())
                && match.getSeason() != null
                && match.getSeason().getLeague() != null
                && match.getSeason().getLeague().getId() != null;
    }

    private boolean validXg(Double value) {
        return value != null && Double.isFinite(value) && value >= 0;
    }
}
