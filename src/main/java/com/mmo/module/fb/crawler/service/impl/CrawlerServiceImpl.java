package com.mmo.module.fb.crawler.service.impl;

import com.microsoft.playwright.Page;
import com.mmo.configuration.AppProperties;
import com.mmo.module.fb.crawler.model.Provider;
import com.mmo.module.fb.crawler.service.CrawlerService;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategy;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.MatchOdds;
import com.mmo.module.fb.entity.enums.MatchStatus;
import com.mmo.module.fb.repository.MatchOddsRepository;
import com.mmo.module.fb.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CrawlerServiceImpl implements CrawlerService {
    DateTimeFormatter vnFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("vi", "VN"));
    private final MatchRepository matchRepository;
    private final TemplateEngine templateEngine;
    private final TelegramClient telegramClient;
    private final AppProperties appProperties;
    private final MatchOddsRepository matchOddsRepository;
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    public void crawler() {
        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);
        Page page = strategy.createPage();
        Set<Long> sofaScoreIds = strategy.fetchDailyUpComingMatches(page);
        List<Match> matches = matchRepository.findBySofaScoreIdIn(sofaScoreIds);
        if (CollectionUtils.isNotEmpty(matches)) {
            matches.forEach(match -> {
                MatchOdds matchOdds = strategy.fetchMatchOddsByMatch(page, match);
                matchOddsRepository.save(matchOdds);
                match.setStatus(MatchStatus.LIVE);
                match.setMatchOdds(matchOdds);
            });
            matchRepository.saveAll(matches);
        }
        // Process post telegram, FB, tiktok
    }

    public String getFormattedContent() {
        Context context = new Context();
        String date = Instant.ofEpochSecond(LocalDate.now().toEpochDay())
                .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(vnFormatter);
        context.setVariable("date", date);
        context.setVariable("leagueName", "UEFA Champions League");
        context.setVariable("home", "Arsenal");
        context.setVariable("away", "Man City");

        return templateEngine.process("daily-matches-information", context);
    }


    @Override
    public void crawlerFlashScore() {
    }

}
