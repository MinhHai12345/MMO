package com.mmo.module.fb.crawler.controller;

import com.mmo.module.fb.crawler.service.CrawlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/crawlers")
@RequiredArgsConstructor
public class CrawlerController {
    private final CrawlerService crawlerService;

    @GetMapping
    public void crawler() {
        crawlerService.crawler();
    }

}
