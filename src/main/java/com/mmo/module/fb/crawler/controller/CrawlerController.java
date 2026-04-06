package com.mmo.module.fb.crawler.controller;

import com.mmo.module.fb.crawler.service.CrawlerService;
import com.mmo.module.fb.job.JobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/crawlers")
@RequiredArgsConstructor
public class CrawlerController {
    private final CrawlerService crawlerService;
    private final JobRunner jobRunner;

    @GetMapping
    public void crawler() {
        crawlerService.fetchDailyMatches();
    }

    @GetMapping("/bot")
    public void pushBot() {
        jobRunner.fetchDailyMatchesJob();
        jobRunner.notifyMatchesDailyJob();
    }

}
