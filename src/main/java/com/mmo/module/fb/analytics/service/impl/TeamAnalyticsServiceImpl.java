package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.analytics.service.TeamAnalyticsService;
import com.mmo.module.fb.analytics.service.TeamAnalyticsService.MatchSample;
import com.mmo.module.fb.analytics.service.TeamAnalyticsService.TeamPerformance;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.enums.MatchStatus;
import com.mmo.module.fb.match.repository.MatchRepository;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamAnalyticsServiceImpl implements TeamAnalyticsService {

    private static final int DEFAULT_HISTORY_SIZE = 10;
    private static final int MAX_HISTORY_SIZE = 50;

    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;

    @Override
    public TeamPerformance analyze(Long teamId, int historySize) {
        if (teamId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "teamId is required");
        }
        int boundedSize = historySize <= 0 ? DEFAULT_HISTORY_SIZE : Math.min(historySize, MAX_HISTORY_SIZE);
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found: " + teamId));

        List<Match> matches = matchRepository.findRecentFinishedMatchesForTeam(
                teamId, MatchStatus.FINISHED, PageRequest.of(0, boundedSize));

        int homeMatches = 0, awayMatches = 0, wins = 0, draws = 0, losses = 0;
        int cleanSheets = 0, scoredMatches = 0, xgMatches = 0;
        double goalsFor = 0, goalsAgainst = 0, points = 0, xgFor = 0, xgAgainst = 0;
        StringBuilder form = new StringBuilder();
        List<MatchSample> samples = new ArrayList<>(matches.size());

        for (Match match : matches) {
            boolean home = teamId.equals(match.getHomeTeam().getId());
            if (home) homeMatches++; else awayMatches++;
            Integer gfValue = home ? match.getHomeScore() : match.getAwayScore();
            Integer gaValue = home ? match.getAwayScore() : match.getHomeScore();
            if (gfValue == null || gaValue == null) continue;

            int gf = gfValue;
            int ga = gaValue;
            goalsFor += gf;
            goalsAgainst += ga;
            String result;
            if (gf > ga) {
                wins++;
                points += 3;
                result = "W";
            } else if (gf == ga) {
                draws++;
                points += 1;
                result = "D";
            } else {
                losses++;
                result = "L";
            }
            form.append(result);
            if (ga == 0) cleanSheets++;
            if (gf > 0) scoredMatches++;

            Double ownXg = home ? match.getHomeXG() : match.getAwayXG();
            Double opponentXg = home ? match.getAwayXG() : match.getHomeXG();
            boolean hasXg = validXg(ownXg) && validXg(opponentXg);
            if (hasXg) {
                xgMatches++;
                xgFor += ownXg;
                xgAgainst += opponentXg;
            }

            Team opponent = home ? match.getAwayTeam() : match.getHomeTeam();
            samples.add(new MatchSample(match.getId(), match.getMatchTime(),
                    opponent == null ? null : opponent.getName(), home, gf, ga,
                    hasXg ? ownXg : null, hasXg ? opponentXg : null, result));
        }

        int count = samples.size();
        List<String> warnings = new ArrayList<>();
        if (count < 5) warnings.add("LIMITED_MATCH_HISTORY: fewer than five completed matches with valid scores.");
        if (xgMatches == 0) warnings.add("NO_XG_HISTORY: no recent matches contain valid xG for both teams.");
        else if (xgMatches < count) warnings.add("PARTIAL_XG_COVERAGE: xG averages use only matches with valid xG for both teams.");

        return new TeamPerformance(
                team.getId(), team.getName(), Instant.now(), boundedSize, count,
                homeMatches, awayMatches, wins, draws, losses,
                ratio(wins, count), count == 0 ? 0 : points / count,
                count == 0 ? 0 : goalsFor / count,
                count == 0 ? 0 : goalsAgainst / count,
                count == 0 ? 0 : (goalsFor - goalsAgainst) / count,
                ratio(cleanSheets, count), ratio(scoredMatches, count),
                xgMatches, average(xgFor, xgMatches), average(xgAgainst, xgMatches),
                xgMatches == 0 ? null : (goalsForForXg(samples) - xgFor) / xgMatches,
                form.toString(), samples, warnings);
    }

    private double goalsForForXg(List<MatchSample> samples) {
        return samples.stream().filter(s -> s.xgFor() != null).mapToInt(MatchSample::goalsFor).sum();
    }

    private boolean validXg(Double value) {
        return value != null && Double.isFinite(value) && value >= 0;
    }

    private double ratio(int value, int total) {
        return total == 0 ? 0 : (double) value / total;
    }

    private Double average(double value, int count) {
        return count == 0 ? null : value / count;
    }
}
