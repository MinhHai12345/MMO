package com.mmo.module.fb.crawler.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.mmo.converter.DynamicConverter;
import com.mmo.module.fb.crawler.service.CrawlerService;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.PerformanceHistory;
import com.mmo.module.fb.entity.Team;
import com.mmo.module.fb.repository.LeagueRepository;
import com.mmo.module.fb.repository.MatchRepository;
import com.mmo.module.fb.repository.PerformanceHistoryRepository;
import com.mmo.module.fb.repository.TeamRepository;
import com.mmo.module.fb.understat.model.LeagueData;
import com.mmo.module.fb.understat.service.UnderStatService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CrawlerServiceImpl implements CrawlerService {
    private final ObjectMapper objectMapper;
    private final UnderStatService underStatService;
    private final DynamicConverter dynamicConverter;
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PerformanceHistoryRepository performanceHistoryRepository;
    //    private final PlayerRepository playerRepository;
    private static final DateTimeFormatter UNDERSTAT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    DateTimeFormatter vnFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy HH:mm", new Locale("vi", "VN"));

    @Override
    @Transactional
    public void crawler() {
        List<League> leagues = leagueRepository.findBySeason("2025/2026");
        if (CollectionUtils.isEmpty(leagues)) {
            return;
        }
        leagues.forEach(league -> {
            LeagueData data = underStatService.getLeagueData(league.getCode().replaceAll(" ", "%20"), league.getSeason().split("/")[0]);
            List<Team> teams = storeTeams(data, league);
//            TODO: storePlayers
            storeHistories(data, teams);
            storeMatches(data, teams, league);
        });
    }

    @Override
    public void crawlerFlashScore() {
        try (Playwright playwright = Playwright.create()) {
            // 1. Khởi tạo trình duyệt (Headless = true để chạy ngầm)
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

                    // 3. Cào Odds cho trận đấu này
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
                System.out.println(String.format("   💰 Odds: 1[%s] X[%s] 2[%s]",
                        choices.get(0).get("fractionalValue").asText(),
                        choices.get(1).get("fractionalValue").asText(),
                        choices.get(2).get("fractionalValue").asText()));
            }
        } catch (Exception e) {
            System.out.println("   ⚠️ Chưa có tỷ lệ kèo.");
        }
    }


    private void storeMatches(LeagueData data, List<Team> teams, League league) {
        List<LeagueData.MatchDate> apiMatches = data.getDates();
        Set<String> apiMatchIds = apiMatches.stream()
                .map(LeagueData.MatchDate::getId)
                .collect(Collectors.toSet());

        Map<String, Match> existingMatchMap = matchRepository.findAllByUnderStatMatchIdIn(apiMatchIds)
                .stream()
                .collect(Collectors.toMap(Match::getUnderStatMatchId, m -> m));

        Map<String, Team> teamMap = teams.stream()
                .collect(Collectors.toMap(
                        t -> t.getName().toLowerCase().trim().replaceAll("\\s+", ""),
                        t -> t
                ));

        List<Match> matchesToSave = apiMatches.stream()
                .map(dto -> {
                    Match target = existingMatchMap.getOrDefault(dto.getId(), new Match());
                    target.setLeague(league);
                    target.setAwayTeam(teamMap.get(dto.getA().getTitle().toLowerCase().trim().replaceAll("\\s+", "")));
                    target.setHomeTeam(teamMap.get(dto.getH().getTitle().toLowerCase().trim().replaceAll("\\s+", "")));
                    return dynamicConverter.convert(dto, target);
                })
                .collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(matchesToSave)) {
            matchRepository.saveAll(matchesToSave);
        }
    }


    private void storeHistories(LeagueData data, List<Team> teams) {
        Map<String, Team> teamMap = teams.stream()
                .collect(Collectors.toMap(
                        t -> standardizeName(t.getName()),
                        t -> t
                ));
        data.getTeams().values().forEach(teamData -> {
            String teamKey = standardizeName(teamData.getTitle());
            Team team = teamMap.get(teamKey);
            if (team == null) {
                return;
            }
            Set<String> existingDates = performanceHistoryRepository.findByTeamIdAndDateGreaterThan(team.getId(),
                            LocalDateTime.of(2025, 7, 1, 0, 0))
                    .stream()
                    .map(it -> it.getDate().format(UNDERSTAT_FORMATTER))
                    .collect(Collectors.toSet());

            List<PerformanceHistory> newPerformances = teamData.getHistory().stream()
                    .filter(historyDto -> !existingDates.contains(historyDto.getDate()))
                    .map(historyDto -> {
                        PerformanceHistory performance = dynamicConverter.convert(historyDto, PerformanceHistory.class);
                        performance.setTeam(team);
                        return performance;
                    })
                    .collect(Collectors.toList());

            if (CollectionUtils.isNotEmpty(newPerformances)) {
                performanceHistoryRepository.saveAll(newPerformances);
            }
        });
    }

    private List<Team> storeTeams(LeagueData data, League league) {
        Set<String> apiTeamIds = data.getTeams().values().stream()
                .map(LeagueData.TeamData::getId)
                .collect(Collectors.toSet());
        List<Team> teams = teamRepository.findAllByUnderStatIdIn(apiTeamIds);

        Set<String> existingTeamIds = teams.stream()
                .map(Team::getUnderStatId)
                .collect(Collectors.toSet());

        List<Team> newTeams = data.getTeams().values().stream()
                .filter(t -> !existingTeamIds.contains(t.getId()))
                .map(t -> dynamicConverter.convert(t, Team.class))
                .collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(newTeams)) {
            newTeams.forEach(newTeam -> {
                newTeam.setLeague(league);
            });
            newTeams = teamRepository.saveAll(newTeams);
            teams.addAll(newTeams);
        }
        return teams;
    }

    private String standardizeName(String name) {
        return name != null ? name.toLowerCase().trim().replaceAll("\\s+", "") : "";
    }


    //    private void storePlayers(LeagueData data) {
//        Set<String> apiPlayerIds = data.getPlayers().stream()
//                .map(LeagueData.PlayerData::getId)
//                .collect(Collectors.toSet());
//
//        Set<String> existingPlayerIds = playerRepository.findAllByUnderStatIdIn(apiPlayerIds)
//                .stream()
//                .map(Player::getUnderStatId)
//                .collect(Collectors.toSet());
//
//        List<Player> newPlayers = data.getPlayers().stream()
//                .filter(p -> !existingPlayerIds.contains(p.getId()))
//                .map(p -> dynamicConverter.convert(p, Player.class))
//                .collect(Collectors.toList());
//
//        if (!newPlayers.isEmpty()) {
//            playerRepository.saveAll(newPlayers);
//        }
//    }
}
