package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.domain.entity.H2HRecord;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.entity.TeamForm;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.repository.H2HRecordRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.domain.repository.TeamFormRepository;
import com.mmo.module.fb.domain.repository.TeamRepository;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class AdvancedMetricsJob extends AbstractJob<CronJob> {

    private static final String TEAM_FORM_TYPE_LAST_10 = "LAST_10";

    @Resource
    private MatchRepository matchRepository;

    @Resource
    private TeamRepository teamRepository;

    @Resource
    private SeasonRepository seasonRepository;

    @Resource
    private TeamFormRepository teamFormRepository;

    @Resource
    private H2HRecordRepository h2HRecordRepository;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.info("Starting advanced metrics calculation...");
        calculateTeamForms();
        calculateH2HRecords();
        log.info("Advanced metrics calculation completed");
    }

    private void calculateTeamForms() {
        log.info("Calculating team forms...");

        List<Season> currentSeasons = seasonRepository.findByIsCurrentTrueAndLeagueIsNotNullAndYearIsNotNull();
        if (CollectionUtils.isEmpty(currentSeasons)) {
            log.warn("No current seasons available; skipping team form calculation");
            return;
        }

        List<Team> teams = teamRepository.findAll();
        int savedForms = 0;

        for (Season season : currentSeasons) {
            for (Team team : teams) {
                try {
                    List<Match> recentMatches = matchRepository.findRecentFinishedMatchesForTeamInSeason(
                            team.getId(), season.getId(), MatchStatus.FINISHED, PageRequest.of(0, 10));

                    if (CollectionUtils.isEmpty(recentMatches)) {
                        continue;
                    }

                    String formData = calculateFormData(recentMatches, team);
                    if (formData.isEmpty()) {
                        continue;
                    }

                    TeamForm teamForm = teamFormRepository
                            .findByTeamIdAndSeasonIdAndFormType(team.getId(), season.getId(), TEAM_FORM_TYPE_LAST_10)
                            .orElseGet(() -> TeamForm.builder()
                                    .team(team)
                                    .season(season)
                                    .formType(TEAM_FORM_TYPE_LAST_10)
                                    .build());

                    teamForm.setFormData(formData);
                    teamForm.setCalculatedAt(Instant.now());
                    teamFormRepository.save(teamForm);
                    savedForms++;
                } catch (Exception e) {
                    log.error("Failed to calculate form for teamId={} seasonId={}",
                            team.getId(), season.getId(), e);
                }
            }
        }

        log.info("Saved {} team form records", savedForms);
    }

    static String calculateFormData(List<Match> matches, Team team) {
        StringBuilder formBuilder = new StringBuilder();

        for (Match match : matches) {
            if (match.getHomeScore() == null || match.getAwayScore() == null) {
                continue;
            }

            boolean isHomeTeam = match.getHomeTeam().getId().equals(team.getId());
            int teamScore = isHomeTeam ? match.getHomeScore() : match.getAwayScore();
            int opponentScore = isHomeTeam ? match.getAwayScore() : match.getHomeScore();

            if (teamScore > opponentScore) {
                formBuilder.append('W');
            } else if (teamScore < opponentScore) {
                formBuilder.append('L');
            } else {
                formBuilder.append('D');
            }
        }

        return formBuilder.toString();
    }

    private void calculateH2HRecords() {
        log.info("Calculating head-to-head records...");
        List<Team> teams = teamRepository.findAll();
        int savedRecords = 0;

        for (Team homeTeam : teams) {
            for (Team awayTeam : teams) {
                if (homeTeam.getId().equals(awayTeam.getId())) {
                    continue;
                }

                try {
                    Optional<H2HRecord> existingRecord = h2HRecordRepository
                            .findByHomeTeamIdAndAwayTeamId(homeTeam.getId(), awayTeam.getId());

                    List<Match> h2hMatches = matchRepository
                            .findByHomeTeamAndAwayTeamOrHomeTeamAndAwayTeamAndStatus(
                                    homeTeam, awayTeam, awayTeam, homeTeam, MatchStatus.FINISHED);

                    if (CollectionUtils.isEmpty(h2hMatches)) {
                        continue;
                    }

                    int homeWins = 0;
                    int awayWins = 0;
                    int draws = 0;
                    int homeGoals = 0;
                    int awayGoals = 0;
                    int validMatches = 0;

                    for (Match match : h2hMatches) {
                        if (match.getHomeScore() == null || match.getAwayScore() == null) {
                            continue;
                        }

                        validMatches++;
                        boolean selectedHomeTeamIsActualHome =
                                match.getHomeTeam().getId().equals(homeTeam.getId());

                        int selectedHomeGoals = selectedHomeTeamIsActualHome
                                ? match.getHomeScore() : match.getAwayScore();
                        int selectedAwayGoals = selectedHomeTeamIsActualHome
                                ? match.getAwayScore() : match.getHomeScore();

                        homeGoals += selectedHomeGoals;
                        awayGoals += selectedAwayGoals;

                        if (selectedHomeGoals > selectedAwayGoals) {
                            homeWins++;
                        } else if (selectedHomeGoals < selectedAwayGoals) {
                            awayWins++;
                        } else {
                            draws++;
                        }
                    }

                    // Do not overwrite an existing record with empty/incomplete score data.
                    if (validMatches == 0) {
                        continue;
                    }

                    H2HRecord record = existingRecord.orElseGet(() -> H2HRecord.builder()
                            .homeTeam(homeTeam)
                            .awayTeam(awayTeam)
                            .build());

                    record.setTotalMatches(validMatches);
                    record.setHomeWins(homeWins);
                    record.setAwayWins(awayWins);
                    record.setDraws(draws);
                    record.setHomeGoals(homeGoals);
                    record.setAwayGoals(awayGoals);
                    record.setLastUpdated(Instant.now());
                    h2HRecordRepository.save(record);
                    savedRecords++;
                } catch (Exception e) {
                    log.error("Failed to calculate H2H for homeTeamId={} awayTeamId={}",
                            homeTeam.getId(), awayTeam.getId(), e);
                }
            }
        }

        log.info("Saved {} head-to-head records", savedRecords);
    }
}
