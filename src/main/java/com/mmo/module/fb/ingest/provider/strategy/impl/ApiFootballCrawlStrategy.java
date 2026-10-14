package com.mmo.module.fb.ingest.provider.strategy.impl;

import com.mmo.module.fb.domain.entity.*;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.*;
import com.mmo.module.fb.ingest.provider.apifootball.ApiFootballClient;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.*;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballResponse;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchesData;
import com.mmo.module.fb.ingest.provider.strategy.AbstractCrawler;
import com.mmo.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiFootballCrawlStrategy extends AbstractCrawler {

    private static final com.fasterxml.jackson.core.type.TypeReference<ApiFootballResponse<LeagueItem>> LEAGUES_TYPE = new com.fasterxml.jackson.core.type.TypeReference<>() {};
    private static final com.fasterxml.jackson.core.type.TypeReference<ApiFootballResponse<TeamItem>> TEAMS_TYPE = new com.fasterxml.jackson.core.type.TypeReference<>() {};

    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final SeasonExternalIdRepository seasonExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final ApiFootballClient apiFootballClient;
    private final AppProperties appProperties;
    private final FixtureIngestionService fixtureIngestionService;

    @Override
    @Transactional
    public void syncLeagues() {
        if (!isEnabled()) return;
        List<Long> leagueIds = appProperties.getApiFootball().getLeagueIds();
        if (leagueIds == null || leagueIds.isEmpty()) {
            log.warn("No API-Football league IDs configured; set API_FOOTBALL_LEAGUE_IDS.");
            return;
        }

        int synced = 0;
        for (Long apiLeagueId : leagueIds) {
            ApiFootballResponse<LeagueItem> response =
                    apiFootballClient.get("/leagues", Map.of("id", apiLeagueId), LEAGUES_TYPE);
            for (LeagueItem item : safe(response.response())) {
                if (item.league() == null || item.league().id() == null) continue;
                LeagueDto dto = item.league();
                String externalId = dto.id().toString();

                League league = leagueExternalIdRepository
                        .findByProviderAndExternalId(Provider.API_FOOTBALL, externalId)
                        .map(LeagueExternalId::getLeague)
                        .orElseGet(() -> leagueRepository.findByIsActive(true).stream()
                                .filter(existing -> existing.getName().equalsIgnoreCase(dto.name()))
                                .findFirst().orElseGet(League::new));

                league.setName(dto.name());
                league.setSlug(slug(dto.name()) + "-" + externalId);
                league.setCountry(item.country() == null ? null : item.country().name());
                league.setLogoUrl(dto.logo());
                league.setExternalId(externalId);
                league.setActive(true);
                league.setIsActive(true);
                league = leagueRepository.save(league);

                final League savedLeague = league;
                LeagueExternalId external = leagueExternalIdRepository
                        .findByLeagueIdAndProvider(savedLeague.getId(), Provider.API_FOOTBALL)
                        .orElseGet(() -> LeagueExternalId.builder()
                                .league(savedLeague).provider(Provider.API_FOOTBALL)
                                .externalId(externalId).build());
                external.setExternalId(externalId);
                leagueExternalIdRepository.save(external);

                for (LeagueSeasonDto seasonDto : safe(item.seasons())) {
                    if (seasonDto.year() == null) continue;
                    String year = seasonDto.year().toString();
                    Season season = seasonRepository.findAll().stream()
                            .filter(existing -> existing.getLeague().getId().equals(savedLeague.getId())
                                    && existing.getYear().equals(year))
                            .findFirst()
                            .orElseGet(() -> Season.builder().league(savedLeague).year(year).build());
                    season.setCurrent(Boolean.TRUE.equals(seasonDto.current()));
                    season.setStartDate(parseDate(seasonDto.start()));
                    season.setEndDate(parseDate(seasonDto.end()));
                    season.setExternalId(year);
                    season = seasonRepository.save(season);

                    final Season savedSeason = season;
                    SeasonExternalId seasonExternal = seasonExternalIdRepository
                            .findBySeasonIdAndProvider(savedSeason.getId(), Provider.API_FOOTBALL)
                            .orElseGet(() -> SeasonExternalId.builder()
                                    .season(savedSeason).provider(Provider.API_FOOTBALL)
                                    .externalId(year).build());
                    seasonExternal.setExternalId(year);
                    seasonExternalIdRepository.save(seasonExternal);
                }
                synced++;
            }
        }
        log.info("API-Football league sync completed. {} leagues processed.", synced);
    }

    @Override
    @Transactional
    public void storeSeasons() {
        syncLeagues();
    }

    @Override
    @Transactional
    public void syncTeams() {
        if (!isEnabled()) return;
        int synced = 0;
        for (Season season : allSeasons()) {
            Long apiLeagueId = apiLeagueId(season.getLeague().getId());
            if (apiLeagueId == null) continue;

            ApiFootballResponse<TeamItem> response = apiFootballClient.get("/teams", Map.of(
                    "league", apiLeagueId, "season", season.getYear()), TEAMS_TYPE);
            for (TeamItem item : safe(response.response())) {
                if (item.team() == null || item.team().id() == null) continue;
                TeamDto dto = item.team();
                String externalId = dto.id().toString();

                Team team = teamExternalIdRepository
                        .findByProviderAndExternalId(Provider.API_FOOTBALL, externalId)
                        .map(TeamExternalId::getTeam)
                        .orElseGet(() -> teamRepository.findByName(dto.name()).orElseGet(Team::new));
                team.setName(dto.name());
                team.setShortName(dto.code());
                team.setSlug(slug(dto.name()) + "-" + externalId);
                team.setLogoUrl(dto.logo());
                team.setExternalId(externalId);
                team.setFoundedYear(dto.founded());
                team.setIsActive(true);
                if (item.venue() != null) {
                    team.setStadiumName(item.venue().name());
                    team.setStadiumCapacity(item.venue().capacity());
                }
                team = teamRepository.save(team);

                final Team savedTeam = team;
                TeamExternalId external = teamExternalIdRepository
                        .findByTeamIdAndProvider(savedTeam.getId(), Provider.API_FOOTBALL)
                        .orElseGet(() -> TeamExternalId.builder().team(savedTeam)
                                .provider(Provider.API_FOOTBALL).externalId(externalId).build());
                external.setExternalId(externalId);
                teamExternalIdRepository.save(external);
                synced++;
            }
        }
        log.info("API-Football team sync completed. {} teams processed.", synced);
    }

    @Override
    public void storeMatches() {
        // Lightweight rolling sync for the daily job.
        fixtureIngestionService.syncCurrentSeasonFixtures(
                LocalDate.now().minusDays(14), LocalDate.now().plusDays(30));
    }

    @Override
    public void syncAllCurrentSeasonFixtures() {
        // Bootstrap/reconciliation job: no date filter includes completed and future fixtures.
        fixtureIngestionService.syncCurrentSeasonFixtures(null, null);
    }

    @Override
    public void storeMatchStatistics() {
        log.info("Match statistics sync is not implemented in this phase.");
    }

    @Override
    public void prepareMatchUpcomingDaily() {
        fixtureIngestionService.syncCurrentSeasonFixtures(LocalDate.now(), LocalDate.now().plusDays(2));
    }

    @Override
    public void prepareMatchResultDaily() {
        fixtureIngestionService.syncCurrentSeasonFixtures(LocalDate.now().minusDays(3), LocalDate.now());
    }

    /**
     * All seasons returned by /leagues, including completed seasons. Historical team membership
     * matters because clubs can be promoted, relegated, renamed, or leave a competition.
     */
    private List<Season> allSeasons() {
        return seasonRepository.findAll().stream()
                .filter(season -> season.getLeague() != null)
                .toList();
    }

    private List<Season> currentSeasons() {
        return seasonRepository.findAll().stream()
                .filter(Season::isCurrent)
                .toList();
    }

    private Long apiLeagueId(Long localLeagueId) {
        return leagueExternalIdRepository.findByLeagueIdAndProvider(localLeagueId, Provider.API_FOOTBALL)
                .map(LeagueExternalId::getExternalId)
                .map(ApiFootballCrawlStrategy::parseLong)
                .orElse(null);
    }

    private boolean isEnabled() {
        return appProperties.getApiFootball() != null && appProperties.getApiFootball().isEnabled();
    }

    private static Long parseLong(String value) {
        try { return Long.valueOf(value); }
        catch (Exception ignored) { return null; }
    }

    private static LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) return null;
        try { return LocalDate.parse(value); }
        catch (Exception ignored) { return null; }
    }

    private static String slug(String value) {
        if (value == null) return "unknown";
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? Collections.emptyList() : values;
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
