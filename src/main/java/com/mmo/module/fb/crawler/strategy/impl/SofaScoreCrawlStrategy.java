package com.mmo.module.fb.crawler.strategy.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.mmo.module.fb.crawler.model.Provider;
import com.mmo.module.fb.crawler.strategy.AbstractCrawler;
import com.mmo.module.fb.entity.League;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SofaScoreCrawlStrategy extends AbstractCrawler {
    private final ObjectMapper objectMapper;

    @Override
    public List<League> crawlLeague() {
        List<League> leagues = new ArrayList<>();
        try (Page page = createPage()) {
            String url = "https://api.sofascore.com/api/v1/config/default-unique-tournaments/VN/football";
            randomDelay();
            Response response = page.navigate(url);

            if (response.status() == 200) {
                JsonNode root = objectMapper.readTree(response.text());
                JsonNode uniqueTournaments = root.get("uniqueTournaments");
                if (uniqueTournaments.isArray()) {
                    for (JsonNode node : uniqueTournaments) {
                        leagues.add(League.builder()
                                .sofaScoreId(node.path("id").asLong())
                                .name(node.path("name").asText())
                                .slug(node.path("slug").asText())
                                .isActive(true)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ Lỗi khi crawl danh sách League: {}", e.getMessage());
        }

        return leagues;
    }

    @Override
    public Provider getProvider() {
        return Provider.SOFA_SCORE;
    }
}
