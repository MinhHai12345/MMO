package com.mmo.module.fb.crawler.strategy.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.mmo.configuration.AppProperties;
import com.mmo.converter.DynamicConverter;
import com.mmo.module.fb.crawler.model.Provider;
import com.mmo.module.fb.crawler.strategy.AbstractCrawler;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.MatchOdds;
import com.mmo.module.fb.entity.Season;
import com.mmo.module.fb.entity.Team;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SofaScoreCrawlStrategy extends AbstractCrawler {
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final DynamicConverter dynamicConverter;

    private static final String LEAGUE_URI = "config/default-unique-tournaments/VN/football";
    private static final String SEASON_URI = "%sunique-tournament/%d/seasons";
    private static final String MATCH_BY_ROUND_URI = "%sunique-tournament/%d/season/%d/events/round/%d";
    private static final String MATCH_ODDS_ALL_URI = "%sevent/%d/odds/1/all";
    private static final String TEAM_OF_LEAGUE_URI = "%sunique-tournament/%d/season/%d/standings/total";
    private static final String MATCH_XG_URI = "%sevent/%d/statistics";
    private static final String DAILY_MATCH_UP_COMING_URI = "%sodds/1/featured-events-by-popularity/football";

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
            String url = String.format(SEASON_URI, appProperties.getSofaScore().getApi(), league.getSofaScoreId());

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
    public List<Match> fetchMatchesByRound(Page page, League league, int round) {
        List<Match> matches = new ArrayList<>();
        try {
            randomDelay();
            String url = String.format(MATCH_BY_ROUND_URI, appProperties.getSofaScore().getApi(),
                    league.getSofaScoreId(), league.getCurrentSeason().getSofaScoreId(), round);
            Response response = page.navigate(url);
            if (response != null && response.status() == 200) {
                JsonNode root = objectMapper.readTree(response.text());
                JsonNode events = root.path("events");
                if (events.isArray() && !events.isEmpty()) {
                    events.forEach(node -> {
                        Match match = dynamicConverter.convert(node, Match.class);
                        match.setLeague(league);
                        match.setSeason(league.getCurrentSeason());
                        matches.add(match);
                    });
                }
            }
        } catch (Exception e) {
            log.error("❌ Lỗi cào Round {} của giải {}: {}", round, league.getName(), e.getMessage());
        }
        return matches;
    }

    @Override
    public MatchOdds fetchMatchOddsByMatch(Page page, Match match) {
        MatchOdds matchOdds = null;
        try {
            String oddsUrl = String.format(MATCH_ODDS_ALL_URI, appProperties.getSofaScore().getApi(), match.getSofaScoreId());
            Response res = page.navigate(oddsUrl);
            if (res.status() == 200) {
                JsonNode odds = objectMapper.readTree(res.text());
                matchOdds = dynamicConverter.convert(odds, MatchOdds.class);
                if (match.getMatchOdds() == null) {
                    matchOdds.setMatch(match);
                } else {
                    matchOdds.setId(match.getMatchOdds().getId());
                }
            }
        } catch (Exception e) {
            System.out.println("   ⚠️ Chưa có tỷ lệ kèo.");
        }
        return matchOdds;
    }

    @Override
    public Match fetchMatchXG(Page page, Match match) {
        try {
            String url = String.format(MATCH_XG_URI, appProperties.getSofaScore().getApi(), match.getSofaScoreId());
            Response response = page.navigate(url);
            if (response.status() == 200) {
                JsonNode root = objectMapper.readTree(response.text());
                JsonNode statistics = root.path("statistics");
                if (statistics.isArray() && !statistics.isEmpty()) {
                    JsonNode allStats = statistics.get(0);
                    JsonNode groups = allStats.path("groups");

                    for (JsonNode group : groups) {
                        for (JsonNode item : group.path("statisticsItems")) {
                            if ("expectedGoals".equalsIgnoreCase(item.path("key").asText())) {
                                match.setHomeXG(new BigDecimal(item.path("home").asText()));
                                match.setAwayXG(new BigDecimal(item.path("away").asText()));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ Lỗi cào xG trận {}: {}", match.getSofaScoreId(), e.getMessage());
        }
        return match;
    }

    @Override
    public List<Team> fetchTeamsByLeague(Page page, League league) {
        List<Team> teams = new ArrayList<>();
        try {
            randomDelay();
            String url = String.format(TEAM_OF_LEAGUE_URI, appProperties.getSofaScore().getApi(), league.getSofaScoreId(),
                    league.getCurrentSeason().getSofaScoreId());
            Response response = page.navigate(url);
            if (response.status() == 200) {
                JsonNode root = objectMapper.readTree(response.text());
                JsonNode rows = root.path("standings");
                if (rows.isArray()) {
                    rows.forEach(row -> {
                        JsonNode jsonTeams = row.path("rows");
                        if (jsonTeams.isArray() && !jsonTeams.isEmpty()) {
                            jsonTeams.forEach(jsonTeam -> {
                                teams.add(dynamicConverter.convert(jsonTeam, Team.class));
                            });
                        }
                    });
                }
            }
        } catch (Exception e) {
            log.error("❌ Lỗi cào danh sách Team cho giải {}: {}", league.getName(), e.getMessage());
        }
        return teams;
    }

    @Override
    public Set<Long> fetchDailyUpComingMatches(Page page) {
        Set<Long> matchIds = new HashSet<>();
        try {
            String url = String.format(DAILY_MATCH_UP_COMING_URI, appProperties.getSofaScore().getApi());
            Response response = page.navigate(url);
            if (response.status() == 200) {
                JsonNode root = objectMapper.readTree(response.text());
                JsonNode featuredEvents = root.path("featuredEvents");
                featuredEvents.forEach(event -> {
                    matchIds.add(event.path("id").asLong());
                });
            }
        } catch (Exception e) {
            log.error("❌ Lỗi lấy trận đấu phổ biến: {}", e.getMessage());
        }
        return matchIds;
    }

    @Override
    public Provider getProvider() {
        return Provider.SOFA_SCORE;
    }
}
