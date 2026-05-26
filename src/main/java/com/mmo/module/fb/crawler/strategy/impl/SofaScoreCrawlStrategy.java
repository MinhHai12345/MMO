package com.mmo.module.fb.crawler.strategy.impl;

import com.microsoft.playwright.Page;
import com.mmo.converter.DynamicConverter;
import com.mmo.module.fb.crawler.model.enums.Provider;
import com.mmo.module.fb.crawler.model.sofa.SofaSeasonData;
import com.mmo.module.fb.crawler.model.sofa.SofaStandingsData;
import com.mmo.module.fb.crawler.model.sofa.SofaUniqueTournamentsData;
import com.mmo.module.fb.crawler.service.SofaCrawlerService;
import com.mmo.module.fb.crawler.strategy.AbstractCrawler;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Season;
import com.mmo.module.fb.entity.Team;
import com.mmo.module.fb.repository.LeagueRepository;
import com.mmo.module.fb.repository.SeasonRepository;
import com.mmo.module.fb.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SofaScoreCrawlStrategy extends AbstractCrawler {
    private final SofaCrawlerService sofaCrawlerService;
    private final DynamicConverter dynamicConverter;
    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;

    @Override
    public void storeLeagues() {
        Page page = createPage();
        List<SofaUniqueTournamentsData.UniqueTournamentDTO> tournaments = sofaCrawlerService.fetchLeagues(page);
        if (CollectionUtils.isNotEmpty(tournaments)) {
            List<League> leagues = leagueRepository.findAll();
            Set<Long> leagueIds = leagues.stream()
                    .filter(Objects::nonNull)
                    .map(League::getSofaScoreId)
                    .collect(Collectors.toSet());
            tournaments = tournaments.stream()
                    .filter(Objects::nonNull)
                    .filter(it -> !leagueIds.contains(it.getId()))
                    .toList();
            if (CollectionUtils.isNotEmpty(tournaments)) {
                leagueRepository.saveAll(dynamicConverter.convertAll(tournaments, League.class));
            }
        }
    }

    @Override
    public void storeSeasons() {
        List<League> leagues = leagueRepository.findByActiveIsTrue();
        Page page = createPage();
        Set<Long> existingSofaSeasonIds = seasonRepository.findAll().stream()
                .map(Season::getSofaScoreId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        leagues.forEach(league -> {
            List<SofaSeasonData.SeasonDTO> seasonDTOS = sofaCrawlerService.fetchSeasonByTournamentId(league.getSofaScoreId(), page);
            seasonDTOS.get(0).setCurrent(true);
            seasonDTOS = seasonDTOS.stream()
                    .filter(Objects::nonNull)
                    .filter(season -> !existingSofaSeasonIds.contains(season.getId()))
                    .toList();
            if (CollectionUtils.isNotEmpty(seasonDTOS)) {
                Set<Season> seasons = seasonDTOS.stream().map(seasonDTO -> {
                    Season season = dynamicConverter.convert(seasonDTO, Season.class);
                    season.setLeague(league);
                    return season;
                }).collect(Collectors.toSet());
                seasonRepository.saveAll(seasons);

                league.setSeasons(seasons);
                seasons.stream()
                        .filter(Season::isCurrent).findFirst()
                        .ifPresent(currentSeason -> league.setCurrentSeasonId(currentSeason.getId()));
                leagueRepository.save(league);
                existingSofaSeasonIds.addAll(seasons.stream().map(Season::getSofaScoreId).collect(Collectors.toSet()));
            }
        });
    }

    @Override
    public void storeTeams() {
        List<League> leagues = leagueRepository.findByActiveIsTrue();
        Page page = createPage();
        Set<Long> existingSofaTeamIds = teamRepository.findAll().stream()
                .map(Team::getSofaScoreId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        leagues.forEach(league -> {
            List<SofaStandingsData.StandingGroupDTO> standings = sofaCrawlerService.fetchStandingTotal(league.getSofaScoreId(),
                    league.getCurrentSeasonId(), page);
            List<SofaStandingsData.TeamDTO> teamDTOS = standings.stream()
                    .map(SofaStandingsData.StandingGroupDTO::getRows)
                    .flatMap(List::stream)
                    .map(SofaStandingsData.StandingRowDTO::getTeam)
                    .toList();

            teamDTOS = teamDTOS.stream()
                    .filter(Objects::nonNull)
                    .filter(teamDTO -> !existingSofaTeamIds.contains(teamDTO.getId()))
                    .collect(Collectors.toMap(
                            SofaStandingsData.TeamDTO::getId,
                            team -> team,
                            (existing, replacement) -> existing
                    )).values().stream().toList();

            if (CollectionUtils.isNotEmpty(teamDTOS)) {
                List<Team> teams = dynamicConverter.convertAll(teamDTOS, Team.class);
                teamRepository.saveAll(teams);
                existingSofaTeamIds.addAll(teams.stream().map(Team::getSofaScoreId).collect(Collectors.toSet()));
            }
        });
    }


//    public List<Match> fetchMatchesByRound(Page page, League league, int round) {
//        List<Match> matches = new ArrayList<>();
//        try {
//            randomDelay();
//            String url = String.format(MATCH_BY_ROUND_URI, appProperties.getSofaScore().getApi(),
//                    league.getSofaScoreId(), league.getCurrentSeason().getSofaScoreId(), round);
//            Response response = page.navigate(url);
//            if (response != null && response.status() == 200) {
//                JsonNode root = objectMapper.readTree(response.text());
//                JsonNode events = root.path("events");
//                if (events.isArray() && !events.isEmpty()) {
//                    events.forEach(node -> {
//                        Match match = dynamicConverter.convert(node, Match.class);
//                        match.setLeague(league);
//                        match.setSeason(league.getCurrentSeason());
//                        matches.add(match);
//                    });
//                }
//            }
//        } catch (Exception e) {
//            log.error("❌ Lỗi cào Round {} của giải {}: {}", round, league.getName(), e.getMessage());
//        }
//        return matches;
//    }


//    public Match fetchMatchXG(Page page, Match match) {
//        try {
//            String url = String.format(MATCH_XG_URI, appProperties.getSofaScore().getApi(), match.getSofaScoreId());
//            Response response = page.navigate(url);
//            if (response.status() == 200) {
//                JsonNode root = objectMapper.readTree(response.text());
//                JsonNode statistics = root.path("statistics");
//                if (statistics.isArray() && !statistics.isEmpty()) {
//                    JsonNode allStats = statistics.get(0);
//                    JsonNode groups = allStats.path("groups");
//
//                    for (JsonNode group : groups) {
//                        for (JsonNode item : group.path("statisticsItems")) {
//                            if ("expectedGoals".equalsIgnoreCase(item.path("key").asText())) {
//                                match.setHomeXG(new BigDecimal(item.path("home").asText()));
//                                match.setAwayXG(new BigDecimal(item.path("away").asText()));
//                            }
//                        }
//                    }
//                }
//            }
//        } catch (Exception e) {
//            log.error("❌ Lỗi cào xG trận {}: {}", match.getSofaScoreId(), e.getMessage());
//        }
//        return match;
//    }
//
//    public Set<Long> fetchDailyUpComingMatches(Page page) {
//        Set<Long> matchIds = new HashSet<>();
//        try {
//            String url = String.format(DAILY_MATCH_UP_COMING_URI, appProperties.getSofaScore().getApi());
//            Response response = page.navigate(url);
//            if (response.status() == 200) {
//                JsonNode root = objectMapper.readTree(response.text());
//                JsonNode featuredEvents = root.path("featuredEvents");
//                featuredEvents.forEach(event -> {
//                    matchIds.add(event.path("id").asLong());
//                });
//            }
//        } catch (Exception e) {
//            log.error("❌ Lỗi lấy trận đấu phổ biến: {}", e.getMessage());
//        }
//        return matchIds;
//    }
//
//    public SofaOddsData fetchDailyMatchOdds(Page page) {
//        try {
//            String url = String.format(DAILY_ALL_MATCH_ODDS_URI, appProperties.getSofaScore().getApi(), LocalDate.now());
//            Response response = page.navigate(url);
//            if (response.status() == 200) {
//                return objectMapper.readValue(response.text(), SofaOddsData.class);
//            }
//        } catch (Exception e) {
//            log.error("❌ Fetch all match odds daily error: {}", e.getMessage());
//        }
//        return null;
//    }

    @Override
    public Provider getProvider() {
        return Provider.SOFA_SCORE;
    }
}
