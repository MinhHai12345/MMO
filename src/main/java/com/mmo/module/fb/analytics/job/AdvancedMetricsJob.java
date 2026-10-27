package com.mmo.module.fb.analytics.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.league.entity.Season;
import com.mmo.module.fb.league.repository.SeasonRepository;
import com.mmo.module.fb.match.entity.H2HRecord;
import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.enums.MatchStatus;
import com.mmo.module.fb.match.repository.H2HRecordRepository;
import com.mmo.module.fb.match.repository.MatchRepository;
import com.mmo.module.fb.team.entity.Team;
import com.mmo.module.fb.team.entity.TeamForm;
import com.mmo.module.fb.team.repository.TeamFormRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class AdvancedMetricsJob extends AbstractJob<CronJob> {

    private static final String TEAM_FORM_TYPE_LAST_10 = "LAST_10";
    private static final int FORM_WINDOW_SIZE = 10;
    private static final int H2H_BATCH_SIZE = 1_000;

    private final MatchRepository matchRepository;
    private final SeasonRepository seasonRepository;
    private final TeamFormRepository teamFormRepository;
    private final H2HRecordRepository h2HRecordRepository;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.info("Advanced metrics run started");
        int teamForms = calculateTeamForms();
        if (shouldStop()) {
            log.warn("Advanced metrics run interrupted after team-form stage");
            return;
        }

        int h2hRecords = calculateH2HRecords();
        log.info("Advanced metrics run completed: teamFormsUpdated={}, h2hRecordsUpdated={}",
                teamForms, h2hRecords);
    }

    /**
     * Read each current season's finished matches once, then derive the last-ten form
     * for both teams in memory. This replaces the previous team x season query loop.
     */
    private int calculateTeamForms() {
        List<Season> currentSeasons = seasonRepository.findByIsCurrentTrueAndLeagueIsNotNullAndYearIsNotNull();
        if (currentSeasons.isEmpty()) {
            log.warn("No current seasons available; team form calculation skipped");
            return 0;
        }

        int savedForms = 0;
        for (Season season : currentSeasons) {
            if (shouldStop()) {
                break;
            }

            List<Match> finishedMatches = matchRepository.findFinishedScoredMatchesForSeason(
                    season.getId(), MatchStatus.FINISHED);
            Map<Long, List<Match>> recentMatchesByTeam = new HashMap<>();
            for (Match match : finishedMatches) {
                addRecentMatch(recentMatchesByTeam, match.getHomeTeam().getId(), match);
                addRecentMatch(recentMatchesByTeam, match.getAwayTeam().getId(), match);
            }

            Map<Long, TeamForm> existingByTeam = new HashMap<>();
            teamFormRepository.findBySeasonIdAndFormType(season.getId(), TEAM_FORM_TYPE_LAST_10)
                    .forEach(form -> existingByTeam.put(form.getTeam().getId(), form));

            List<TeamForm> toSave = new ArrayList<>();
            Instant calculatedAt = Instant.now();
            for (Map.Entry<Long, List<Match>> entry : recentMatchesByTeam.entrySet()) {
                if (shouldStop()) {
                    break;
                }

                List<Match> recentMatches = entry.getValue();
                if (recentMatches.isEmpty()) {
                    continue;
                }

                Team team = teamFor(recentMatches, entry.getKey());
                if (team == null) {
                    continue;
                }

                String formData = calculateFormData(recentMatches, team);
                if (formData.isEmpty()) {
                    continue;
                }

                TeamForm form = existingByTeam.get(entry.getKey());
                if (form == null) {
                    form = TeamForm.builder()
                            .team(team)
                            .season(season)
                            .formType(TEAM_FORM_TYPE_LAST_10)
                            .build();
                }
                form.setFormData(formData);
                form.setCalculatedAt(calculatedAt);
                toSave.add(form);
            }

            if (!toSave.isEmpty()) {
                teamFormRepository.saveAll(toSave);
                savedForms += toSave.size();
            }
            log.debug("Team forms calculated for seasonId={}: matches={}, teamsUpdated={}",
                    season.getId(), finishedMatches.size(), toSave.size());
        }

        return savedForms;
    }

    private void addRecentMatch(Map<Long, List<Match>> recentMatchesByTeam, Long teamId, Match match) {
        List<Match> recent = recentMatchesByTeam.computeIfAbsent(teamId, ignored -> new ArrayList<>(FORM_WINDOW_SIZE));
        if (recent.size() < FORM_WINDOW_SIZE) {
            recent.add(match);
        }
    }

    private Team teamFor(List<Match> matches, Long teamId) {
        for (Match match : matches) {
            if (match.getHomeTeam().getId().equals(teamId)) {
                return match.getHomeTeam();
            }
            if (match.getAwayTeam().getId().equals(teamId)) {
                return match.getAwayTeam();
            }
        }
        return null;
    }

    static String calculateFormData(List<Match> matches, Team team) {
        StringBuilder form = new StringBuilder(Math.min(matches.size(), FORM_WINDOW_SIZE));
        for (Match match : matches) {
            if (match.getHomeScore() == null || match.getAwayScore() == null) {
                continue;
            }

            boolean home = match.getHomeTeam().getId().equals(team.getId());
            int score = home ? match.getHomeScore() : match.getAwayScore();
            int opponentScore = home ? match.getAwayScore() : match.getHomeScore();

            if (score > opponentScore) {
                form.append('W');
            } else if (score < opponentScore) {
                form.append('L');
            } else {
                form.append('D');
            }
        }
        return form.toString();
    }

    /**
     * Aggregate all historical head-to-head results in stable, bounded pages. The
     * previous implementation issued a query for every ordered pair of teams,
     * including pairs that had never played each other (O(team^2) database queries).
     */
    private int calculateH2HRecords() {
        Map<PairKey, H2HAggregate> aggregates = new HashMap<>();
        int page = 0;
        int matchesRead = 0;

        while (!shouldStop()) {
            List<Match> matches = matchRepository.findFinishedScoredMatches(
                    MatchStatus.FINISHED, PageRequest.of(page, H2H_BATCH_SIZE));
            if (matches.isEmpty()) {
                break;
            }

            for (Match match : matches) {
                Team home = match.getHomeTeam();
                Team away = match.getAwayTeam();
                if (home == null || away == null || home.getId() == null || away.getId() == null
                        || match.getHomeScore() == null || match.getAwayScore() == null) {
                    continue;
                }

                int homeGoals = match.getHomeScore();
                int awayGoals = match.getAwayScore();
                aggregates.computeIfAbsent(new PairKey(home.getId(), away.getId()),
                        ignored -> new H2HAggregate(home, away))
                        .add(homeGoals, awayGoals);
                aggregates.computeIfAbsent(new PairKey(away.getId(), home.getId()),
                        ignored -> new H2HAggregate(away, home))
                        .add(awayGoals, homeGoals);
                matchesRead++;
            }

            if (matches.size() < H2H_BATCH_SIZE) {
                break;
            }
            page++;
        }

        if (shouldStop()) {
            log.warn("H2H aggregation interrupted; accumulated values were not persisted");
            return 0;
        }

        Map<PairKey, H2HRecord> existingByPair = new HashMap<>();
        h2HRecordRepository.findAllWithTeams().forEach(record ->
                existingByPair.put(new PairKey(record.getHomeTeam().getId(), record.getAwayTeam().getId()), record));

        Instant updatedAt = Instant.now();
        List<H2HRecord> toSave = new ArrayList<>(aggregates.size());
        for (Map.Entry<PairKey, H2HAggregate> entry : aggregates.entrySet()) {
            PairKey key = entry.getKey();
            H2HAggregate aggregate = entry.getValue();
            H2HRecord record = existingByPair.get(key);
            if (record == null) {
                record = H2HRecord.builder()
                        .homeTeam(aggregate.homeTeam)
                        .awayTeam(aggregate.awayTeam)
                        .build();
            }

            record.setTotalMatches(aggregate.totalMatches);
            record.setHomeWins(aggregate.homeWins);
            record.setAwayWins(aggregate.awayWins);
            record.setDraws(aggregate.draws);
            record.setHomeGoals(aggregate.homeGoals);
            record.setAwayGoals(aggregate.awayGoals);
            record.setLastUpdated(updatedAt);
            toSave.add(record);
        }

        if (!toSave.isEmpty()) {
            h2HRecordRepository.saveAll(toSave);
        }
        log.info("H2H metrics rebuilt: matchesRead={}, directionalRecordsUpdated={}",
                matchesRead, toSave.size());
        return toSave.size();
    }

    private record PairKey(Long homeTeamId, Long awayTeamId) {
    }

    private static final class H2HAggregate {
        private final Team homeTeam;
        private final Team awayTeam;
        private int totalMatches;
        private int homeWins;
        private int awayWins;
        private int draws;
        private int homeGoals;
        private int awayGoals;

        private H2HAggregate(Team homeTeam, Team awayTeam) {
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
        }

        private void add(int scored, int conceded) {
            totalMatches++;
            homeGoals += scored;
            awayGoals += conceded;
            if (scored > conceded) {
                homeWins++;
            } else if (scored < conceded) {
                awayWins++;
            } else {
                draws++;
            }
        }
    }
}
