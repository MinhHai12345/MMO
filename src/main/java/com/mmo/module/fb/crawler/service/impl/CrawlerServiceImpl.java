package com.mmo.module.fb.crawler.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.mmo.module.fb.crawler.model.Provider;
import com.mmo.module.fb.crawler.service.CrawlerService;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategy;
import com.mmo.module.fb.crawler.strategy.CrawlerStrategyRegistry;
import com.mmo.module.fb.entity.League;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CrawlerServiceImpl implements CrawlerService {
    private final ObjectMapper objectMapper;
    DateTimeFormatter vnFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy HH:mm", new Locale("vi", "VN"));
    private final CrawlerStrategyRegistry crawlerStrategyRegistry;

    @Override
    @Transactional
    public void crawler() {
        CrawlerStrategy strategy = crawlerStrategyRegistry.getStrategy(Provider.SOFA_SCORE);
        List<League> leagues = strategy.fetchLeague();
    }

    @Override
    public void crawlerFlashScore() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"));
            Page page = context.newPage();

            // 2. URL lấy danh sách trận đấu của giải
            // Lưu ý: SofaScore thường dùng format api/v1/unique-tournament/{id}/season/{sid}/events
            String eventsUrl = String.format("https://api.sofascore.com/api/v1/unique-tournament/%d/season/%d/events/next/0", 679, 76984);

            System.out.println("--- Đang quét danh sách trận đấu ---");
            Response response = page.navigate(eventsUrl);

            if (response.status() == 200) {
                JsonNode root = objectMapper.readTree(response.text());
                JsonNode events = root.get("events");

                for (JsonNode event : events) {
                    long matchId = event.get("id").asLong();
                    String homeTeam = event.get("homeTeam").get("name").asText();
                    String awayTeam = event.get("awayTeam").get("name").asText();
                    String status = event.get("status").get("type").asText();
                    long timestamp = event.get("startTimestamp").asLong();
                    String vnTime = Instant.ofEpochSecond(timestamp)
                            .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                            .format(vnFormatter);
                    System.out.println(String.format("\n⚽ Trận: %s vs %s -- Giờ đá: %s (ID: %d)  status %s ", homeTeam, awayTeam, vnTime, matchId, status));

                    // 3. Cào MatchOdds cho trận đấu này
                    getMatchOdds(page, matchId);
                }
            }
            browser.close();
        } catch (Exception e) {
            System.err.println("Lỗi cào dữ liệu: " + e.getMessage());
        }
    }

    private void getMatchOdds(Page page, long matchId) {
        try {
            String oddsUrl = String.format("https://api.sofascore.com/api/v1/event/%d/odds/1/all", matchId);
            Response res = page.navigate(oddsUrl);
            if (res.status() == 200) {
                JsonNode odds = objectMapper.readTree(res.text());
                // Lấy kèo 1X2 (thường là market đầu tiên)
                JsonNode choices = odds.get("markets").get(0).get("choices");
                System.out.println(String.format("   💰 MatchOdds: 1[%s] X[%s] 2[%s]",
                        choices.get(0).get("fractionalValue").asText(),
                        choices.get(1).get("fractionalValue").asText(),
                        choices.get(2).get("fractionalValue").asText()));
            }
        } catch (Exception e) {
            System.out.println("   ⚠️ Chưa có tỷ lệ kèo.");
        }
    }

}
