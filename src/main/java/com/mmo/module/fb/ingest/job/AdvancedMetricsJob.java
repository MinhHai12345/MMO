package com.mmo.module.fb.ingest.job;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.job.AbstractJob;
import com.mmo.module.fb.domain.entity.H2HRecord;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.repository.H2HRecordRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.domain.repository.TeamFormRepository;
import com.mmo.module.fb.domain.repository.TeamRepository;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class AdvancedMetricsJob extends AbstractJob<CronJob> {

    @Resource
    private MatchRepository matchRepository;

    @Resource
    private TeamRepository teamRepository;

    @Resource
    private TeamFormRepository teamFormRepository;

    @Resource
    private H2HRecordRepository h2HRecordRepository;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        log.info("📊 Starting advanced metrics calculation...");

        calculateTeamForms();
        calculateH2HRecords();

        log.info("✅ Advanced metrics calculation completed");
    }

    private void calculateTeamForms() {
        log.info("📈 Calculating team forms...");

        List<Team> teams = teamRepository.findAll();

        for (Team team : teams) {
            try {
                List<Match> recentMatches = matchRepository.findTop10ByHomeTeamOrAwayTeamAndStatusOrderByMatchTimeDesc(
                        team, MatchStatus.FINISHED, PageRequest.of(0, 10));

                if (CollectionUtils.isEmpty(recentMatches)) {
                    continue;
                }

                String formData = calculateFormData(recentMatches, team);

                // TODO: Get current season for the team
                // For now, skip season-specific form calculation
                log.debug("📊 Form data for team {}: {}", team.getName(), formData);

            } catch (Exception e) {
                log.error("❌ Error calculating form for team {}: {}", team.getId(), e.getMessage());
            }
        }
    }

    private String calculateFormData(List<Match> matches, Team team) {
        StringBuilder formBuilder = new StringBuilder();

        for (Match match : matches) {
            if (match.getHomeTeam().getId().equals(team.getId())) {
                if (match.getHomeScore() != null && match.getAwayScore() != null) {
                    if (match.getHomeScore() > match.getAwayScore()) {
                        formBuilder.append("W");
                    } else if (match.getHomeScore() < match.getAwayScore()) {
                        formBuilder.append("L");
                    } else {
                        formBuilder.append("D");
                    }
                }
            } else {
                if (match.getHomeScore() != null && match.getAwayScore() != null) {
                    if (match.getAwayScore() > match.getHomeScore()) {
                        formBuilder.append("W");
                    } else if (match.getAwayScore() < match.getHomeScore()) {
                        formBuilder.append("L");
                    } else {
                        formBuilder.append("D");
                    }
                }
            }
        }

        return formBuilder.toString();
    }

    private void calculateH2HRecords() {
        log.info("🔄 Calculating head-to-head records...");

        List<Team> teams = teamRepository.findAll();

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

                    for (Match match : h2hMatches) {
                        if (match.getHomeScore() != null && match.getAwayScore() != null) {
                            homeGoals += match.getHomeScore();
                            awayGoals += match.getAwayScore();

                            if (match.getHomeTeam().getId().equals(homeTeam.getId())) {
                                if (match.getHomeScore() > match.getAwayScore()) {
                                    homeWins++;
                                } else if (match.getHomeScore() < match.getAwayScore()) {
                                    awayWins++;
                                } else {
                                    draws++;
                                }
                            } else {
                                if (match.getAwayScore() > match.getHomeScore()) {
                                    homeWins++;
                                } else if (match.getAwayScore() < match.getHomeScore()) {
                                    awayWins++;
                                } else {
                                    draws++;
                                }
                            }
                        }
                    }

                    H2HRecord record;
                    if (existingRecord.isPresent()) {
                        record = existingRecord.get();
                    } else {
                        record = H2HRecord.builder()
                                .homeTeam(homeTeam)
                                .awayTeam(awayTeam)
                                .build();
                    }

                    record.setTotalMatches(h2hMatches.size());
                    record.setHomeWins(homeWins);
                    record.setAwayWins(awayWins);
                    record.setDraws(draws);
                    record.setHomeGoals(homeGoals);
                    record.setAwayGoals(awayGoals);
                    record.setLastUpdated(Instant.now());

                    h2HRecordRepository.save(record);

                } catch (Exception e) {
                    log.error("❌ Error calculating H2H for {} vs {}: {}",
                            homeTeam.getName(), awayTeam.getName(), e.getMessage());
                }
            }
        }
    }
}
