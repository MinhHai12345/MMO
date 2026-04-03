package com.mmo.module.fb.job;

import com.mmo.module.fb.channel.service.TelegramService;
import com.mmo.module.fb.crawler.service.CrawlerService;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobRunner {
    private final CrawlerService crawlerService;
    private final TelegramService telegramService;
    private final MatchRepository matchRepository;

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void fetchDailyMatchesJob() {
        log.info("🚀 Bắt đầu cào danh sách trận đấu hôm nay...");
        crawlerService.fetchDailyMatches();
    }

    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Ho_Chi_Minh")
    public void notifyProcessMatchesJob() {
        log.info("📱 Đang tạo nội dung thông báo lịch thi đấu...");
        List<Match> todayMatches = matchRepository.findProcessMatch();

        if (CollectionUtils.isNotEmpty(todayMatches)) {
            Map<League, List<Match>> groupedMatches = todayMatches.stream()
                    .collect(Collectors.groupingBy(Match::getLeague));

            telegramService.notifyProcessMatches(groupedMatches);
            log.info("✅ Đã push nội dung lên các nền tảng.");
        }
    }

}
