package com.mmo.module.fb.crawler.strategy.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.mmo.configuration.AppProperties;
import com.mmo.module.fb.crawler.model.Provider;
import com.mmo.module.fb.crawler.strategy.AbstractCrawler;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Season;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SofaScoreCrawlStrategy extends AbstractCrawler {
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    private static final String LEAGUE_URI = "config/default-unique-tournaments/VN/football";
    private static final String SEASON_URI = "unique-tournament/%d/seasons";

    @Override
    public List<League> fetchLeague() {
        List<League> leagues = new ArrayList<>();
        try (Page page = createPage()) {
            String url = appProperties.getSofaScore().getApi().concat(LEAGUE_URI);
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
    public List<Season> fetchSeasonByLeague(League league) {
        List<Season> seasons = new ArrayList<>();

        try (Page page = createPage()) {
            String url = String.format(appProperties.getSofaScore().getApi().concat(SEASON_URI), league.getSofaScoreId());

            randomDelay();
            Response response = page.navigate(url);

            if (response != null && response.status() == 200) {
                JsonNode root = objectMapper.readTree(response.text());
                JsonNode seasonNodes = root.path("seasons");

                if (seasonNodes.isArray() && !seasonNodes.isEmpty()) {
                    for (int i = 0; i < seasonNodes.size(); i++) {
                        JsonNode node = seasonNodes.get(i);
                        seasons.add(Season.builder()
                                .sofaScoreId(node.path("id").asLong())
                                .year(node.path("year").asText())
                                .league(league)
                                .isCurrent(i == 0)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ Lỗi lấy season cho league {}: {}", league.getSofaScoreId(), e.getMessage());
        }
        return seasons;
    }


    @Override
    public Provider getProvider() {
        return Provider.SOFA_SCORE;
    }
}
