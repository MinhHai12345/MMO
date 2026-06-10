package com.mmo.module.fb.job;

import com.mmo.cronjob.entity.CronJob;
import com.mmo.cronjob.job.AbstractJob;
import com.mmo.module.fb.channel.service.TelegramService;
import com.mmo.module.fb.entity.MatchPrediction;
import com.mmo.module.fb.entity.enums.MatchPredictionStatus;
import com.mmo.module.fb.repository.MatchPredictionRepository;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MatchDailyRecapJob extends AbstractJob<CronJob> {
    @Resource
    private TelegramService telegramService;

    @Resource
    private MatchPredictionRepository matchPredictionRepository;

    @Override
    protected void executeInternal(JobExecutionContext context, CronJob cronJob) {
        List<MatchPrediction> completeMatches = matchPredictionRepository.findByStatus(MatchPredictionStatus.RESULT);
        if (CollectionUtils.isNotEmpty(completeMatches)) {
            populateWinMatches(completeMatches);
            telegramService.notifyMatchesRecap(completeMatches);
            completeMatches.forEach(it -> it.setStatus(MatchPredictionStatus.RECAPPED));
            matchPredictionRepository.saveAll(completeMatches);
        }
    }

    public void populateWinMatches(List<MatchPrediction> completedMatches) {
        for (MatchPrediction mp : completedMatches) {
            boolean isWin = checkPredictionSuccess(mp);
            mp.setWin(isWin);
        }
    }

    private boolean checkPredictionSuccess(MatchPrediction mp) {
        String pick = identifyWinner(mp.getMostLikelyWinner());
        if (pick == null || mp.getActualHomeGoals() == null || mp.getActualAwayGoals() == null) {
            return false;
        }
        int home = mp.getActualHomeGoals();
        int away = mp.getActualAwayGoals();

        return switch (pick.toUpperCase()) {
            case "HOME WIN" -> home > away;
            case "AWAY WIN" -> away > home;
            case "DRAW (X)" -> home == away;
            default -> false;
        };
    }

    private String identifyWinner(String recommendedPick) {
        if (recommendedPick == null) {
            return null;
        }
        Pattern pattern = Pattern.compile("\\((.*?)\\)");
        Matcher matcher = pattern.matcher(recommendedPick);

        if (matcher.find()) {
            String value = matcher.group(1);
            if ("X".equals(value)) {
                value = "DRAW (X)";
            }
            return value;
        }
        return null;
    }

}
