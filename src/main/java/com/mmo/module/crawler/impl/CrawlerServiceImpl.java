package com.mmo.module.crawler.impl;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.mmo.module.crawler.CrawlerService;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CrawlerServiceImpl implements CrawlerService {
    @Override
    public void crawler() {
        String url = "https://understat.com/league/EPL";
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get();

            // 2. Tìm thẻ <script> chứa dữ liệu trận đấu (thường chứa biến datesData)
            Elements scripts = doc.select("script");
            System.out.println(scripts);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
