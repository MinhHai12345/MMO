package com.mmo.module.fb.ingest.provider.strategy.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mmo.module.fb.domain.entity.League;
import com.mmo.module.fb.domain.entity.LeagueExternalId;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchExternalId;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.SeasonExternalId;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.entity.TeamExternalId;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.LeagueRepository;
import com.mmo.module.fb.domain.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.domain.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.domain.repository.TeamExternalIdRepository;
import com.mmo.module.fb.domain.repository.TeamRepository;
import com.mmo.module.fb.ingest.provider.apifootball.ApiFootballClient;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchesData;
import com.mmo.module.fb.ingest.provider.strategy.AbstractCrawler;
import com.mmo.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiFootballCrawlStrategy extends AbstractCrawler {

    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final SeasonExternalIdRepository seasonExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final ApiFootballClient apiFootballClient;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public void syncLeagues() {
        if (!isEnabled()) {
            log.info("API-Football integration is disabled.");
            return;
        }

        List<Long> leagueIds = appProperties.getApiFootball().getLeagueIds();
        if (leagueIds == null || leagueIds.isEmpty()) {
            log.warn("No API-Football league IDs configured. Set API_FOOTBALL_LEAGUE_IDS, e.g. 39,140,135.");
            return;
        }

        int synced = 0;
        for (Long apiLeagueId : leagueIds) {
            JsonNode response = apiFootballClient.get("/leagues", Map.of("id", apiLeagueId));
            for (JsonNode item : response.path("response")) {
                JsonNode leagueNode = item.path("league");
                JsonNode countryNode = item.path("country");
                if (leagueNode.path("id").isMissingNode()) {
                    continue;
                }

                String externalId = text(leagueNode, "id");
                League league = leagueExternalIdRepository
                        .findByProviderAndExternalId(Provider.API_FOOTBALL, externalId)
                        .map(LeagueExternalId::getLeague)
                        .orElseGet(() -> leagueRepository.findByIsActive(true).stream()
                                .filter(existing -> existing.getName().equalsIgnoreCase(text(leagueNode, "name")))
                                .findFirst()
                                .orElseGet(League::new));

                league.setName(text(leagueNode, "name"));
                league.setSlug(slug(text(leagueNode, "name")) + "-" + externalId);
                league.setCountry(text(countryNode, "name"));
                league.setLogoUrl(text(leagueNode, "logo"));
                league.setExternalId(externalId);
                league.setActive(true);
                league.setIsActive(true);
                league = leagueRepository.save(league);

                LeagueExternalId external = leagueExternalIdRepository
                        .findByLeagueIdAndProvider(league.getId(), Provider.API_FOOTBALL)
                        .orElseGet(() -> LeagueExternalId.builder()
                                .league(league)
                                .provider(Provider.API_FOOTBALL)
                                .externalId(externalId)
                                .build());
                external.setExternalId(externalId);
                leagueExternalIdRepository.save(external);

                for (JsonNode seasonNode : item.path("seasons")) {
                    int year = seasonNode.path("year").asInt(0);
                    if (year <= 0) {
                        continue;
                    }
                    final League savedLeague = league;
                    Season season = seasonRepository.findAll().stream()
                            .filter(s -> s.getLeague().getId().equals(savedLeague.getId())
                                    && s.getYear().equals(String.valueOf(year)))
                            .findFirst()
                            .orElseGet(() -> Season.builder()
                                    .league(savedLeague)
                                    .year(String.valueOf(year))
                                    .build());
                    season.setCurrent(seasonNode.path("current").asBoolean(false));
                    season.setStartDate(parseDate(seasonNode.path("start").asText(null)));
                    season.setEndDate(parseDate(seasonNode.path("end").asText(null)));
                    season.setExternalId(String.valueOf(year));
                    season = seasonRepository.save(season);

                    SeasonExternalId seasonExternal = seasonExternalIdRepository
                            .findBySeasonIdAndProvider(season.getId(), Provider.API_FOOTBALL)
                            .orElseGet(() -> SeasonExternalId.builder()
                                    .season(season)
                                    .provider(Provider.API_FOOTBALL)
                                    .externalId(String.valueOf(year))
                                    .build());
                    seasonExternal.setExternalId(String.valueOf(year));
                    seasonExternalIdRepository.save(seasonExternal);
                }
                synced++;
            }
        }
        log.info("API-Football league sync completed. {} league records processed.", synced);
    }

    @Override
    @Transactional
    public void storeSeasons() {
        // /leagues returns season metadata as part of the official response.
        // Keeping this operation idempotent also refreshes active season flags.
        syncLeagues();
    }

    @Override
    @Transactional
    public void syncTeams() {
        if (!isEnabled()) return;

        List<Season> currentSeasons = currentSeasons();
        int synced = 0;
        for (Season season : currentSeasons) {
            Long leagueId = apiLeagueId(season.getLeague().getId());
            if (leagueId == null) continue;

            JsonNode response = apiFootballClient.get("/teams", Map.of(
                    "league", leagueId, "season", season.getYear()));
            for (JsonNode item : response.path("response")) {
                JsonNode teamNode = item.path("team");
                String externalId = text(teamNode, "id");
                if (!StringUtils.hasText(externalId)) continue;

                Team team = teamExternalIdRepository
                        .findByProviderAndExternalId(Provider.API_FOOTBALL, externalId)
                        .map(TeamExternalId::getTeam)
                        .orElseGet(() -> teamRepository.findByName(text(teamNode, "name"))
                                .orElseGet(Team::new));
                team.setName(text(teamNode, "name"));
                team.setShortName(text(teamNode, "code"));
                team.setSlug(slug(text(teamNode, "name")) + "-" + externalId);
                team.setLogoUrl(text(teamNode, "logo"));
                team.setExternalId(externalId);
                team.setIsActive(true);
                team = teamRepository.save(team);

                TeamExternalId teamExternal = teamExternalIdRepository
                        .findByTeamIdAndProvider(team.getId(), Provider.API_FOOTBALL)
                        .orElseGet(() -> TeamExternalId.builder()
                                .team(team).provider(Provider.API_FOOTBALL).externalId(externalId).build());
                teamExternal.setExternalId(externalId);
                teamExternalIdRepository.save(teamExternal);
                synced++;
            }
        }
        log.info("API-Football team sync completed. {} team records processed.", synced);
    }

    @Override
    @Transactional
    public void storeMatches() {
        syncFixtures(LocalDate.now().minusDays(14), LocalDate.now().plusDays(30));
    }

    @Override
    public void storeMatchStatistics() {
        log.info("Advanced match statistics are not included in this initial API-Football sync.");
    }

    @Override
    @Transactional
    public void prepareMatchUpcomingDaily() {
        syncFixtures(LocalDate.now(), LocalDate.now().plusDays(2));
    }

    @Override
    @Transactional
    public void prepareMatchResultDaily() {
        syncFixtures(LocalDate.now().minusDays(3), LocalDate.now());
    }

    private void syncFixtures(LocalDate from, LocalDate to) {
        if (!isEnabled()) return;

        int synced = 0;
        for (Season season : currentSeasons()) {
            Long leagueId = apiLeagueId(season.getLeague().getId());
            if (leagueId == null) continue;

            JsonNode response = apiFootballClient.get("/fixtures", Map.of(
                    "league", leagueId,
                    "season", season.getYear(),
                    "from", from.toString(),
                    "to", to.toString()
            ));

            for (JsonNode item : response.path("response")) {
                JsonNode fixture = item.path("fixture");
                JsonNode leagueNode = item.path("league");
                JsonNode teams = item.path("teams");
                JsonNode goals = item.path("goals");
                String fixtureId = text(fixture, "id");
                if (!StringUtils.hasText(fixtureId)) continue;

                Team home = findTeam(text(teams.path("home"), "id"));
                Team away = findTeam(text(teams.path("away"), "id"));
                if (home == null || away == null) {
                    log.debug("Skipping fixture {} because one or both teams have not been synced.", fixtureId);
                    continue;
                }

                final String extId = fixtureId;
                Match match = matchExternalIdRepository
                        .findByProviderAndExternalId(Provider.API_FOOTBALL, fixtureId)
                        .map(MatchExternalId::getMatch)
                        .orElseGet(() -> matchRepository.findAll().stream()
                                .filter(m -> extId.equals(m.getExternalId()))
                                .findFirst().orElseGet(Match::new));

                match.setExternalId(fixtureId);
                match.setSeason(season);
                match.setHomeTeam(home);
                match.setAwayTeam(away);
                String kickoff = fixture.path("date").asText(null);
                if (StringUtils.hasText(kickoff)) {
                    match.setMatchTime(Instant.parse(kickoff));
                    match.setStartTimestamp(Instant.parse(kickoff).getEpochSecond());
                }
                match.setRoundInfo(leagueNode.path("round").asText(null));
                match.setVenue(text(fixture.path("venue"), "name"));
                match.setReferee(text(fixture, "referee"));
                match.setStatus(toMatchStatus(text(fixture.path("status"), "short")));
                if (!goals.path("home").isMissingNode() && !goals.path("home").isNull()) {
                    match.setHomeScore(goals.path("home").asInt());
                }
                if (!goals.path("away").isMissingNode() && !goals.path("away").isNull()) {
                    match.setAwayScore(goals.path("away").asInt());
                }
                match = matchRepository.save(match);

                MatchExternalId external = matchExternalIdRepository
                        .findByMatchIdAndProvider(match.getId(), Provider.API_FOOTBALL)
                        .orElseGet(() -> MatchExternalId.builder()
                                .match(match).provider(Provider.API_FOOTBALL).externalId(fixtureId).build());
                external.setExternalId(fixtureId);
                matchExternalIdRepository.save(external);
                synced++;
            }
        }
        log.info("API-Football fixture sync completed for {} through {}. {} fixtures processed.",
                from, to, synced);
    }

    private Team findTeam(String apiTeamId) {
        if (!StringUtils.hasText(apiTeamId)) return null;
        return teamExternalIdRepository.findByProviderAndExternalId(Provider.API_FOOTBALL, apiTeamId)
                .map(TeamExternalId::getTeam)
                .orElse(null);
    }

    private List<Season> currentSeasons() {
        return seasonRepository.findAll().stream()
                .filter(Season::isCurrent)
                .toList();
    }

    private Long apiLeagueId(Long localLeagueId) {
        return leagueExternalIdRepository.findByLeagueIdAndProvider(localLeagueId, Provider.API_FOOTBALL)
                .map(LeagueExternalId::getExternalId)
                .map(id -> {
                    try { return Long.valueOf(id); }
                    catch (NumberFormatException ignored) { return null; }
                })
                .orElse(null);
    }

    private boolean isEnabled() {
        return appProperties.getApiFootball() != null && appProperties.getApiFootball().isEnabled();
    }

    private static MatchStatus toMatchStatus(String status) {
        if (status == null) return MatchStatus.SCHEDULED;
        return switch (status.toUpperCase()) {
            case "1H", "HT", "2H", "ET", "P", "LIVE", "BT" -> MatchStatus.LIVE;
            case "FT", "AET", "PEN" -> MatchStatus.FINISHED;
            case "PST", "SUSP", "INT" -> MatchStatus.POSTPONED;
            case "CANC", "ABD", "WO" -> MatchStatus.CANCELLED;
            default -> MatchStatus.SCHEDULED;
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value == null || value.isMissingNode() || value.isNull()
                ? null : value.asText();
    }

    private static LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) return null;
        try { return LocalDate.parse(value); }
        catch (Exception ignored) { return null; }
    }

    private static String slug(String value) {
        if (value == null) return "unknown";
        return value.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    @Override
    public List<SofaMatchesData.SofaEventDTO> getLatestHistoriesMatchesByTeamId(Long teamId) {
        return Collections.emptyList();
    }

    @Override
    public Provider getProvider() {
        return Provider.API_FOOTBALL;
    }
}
