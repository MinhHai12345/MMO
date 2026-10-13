package com.mmo.module.fb.ingest.provider.strategy.impl;

import com.mmo.module.fb.domain.entity.League;
import com.mmo.module.fb.domain.entity.LeagueExternalId;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchExternalId;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.SeasonExternalId;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.BookmakerRepository;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.LeagueRepository;
import com.mmo.module.fb.domain.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.domain.repository.OddsHistoryRepository;
import com.mmo.module.fb.domain.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.domain.repository.TeamExternalIdRepository;
import com.mmo.module.fb.domain.repository.TeamRepository;
import com.mmo.module.fb.ingest.provider.strategy.AbstractCrawler;
import com.mmo.module.fb.repository.SeasonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiFootballCrawlStrategy extends AbstractCrawler {
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final SeasonRepository seasonRepository;
    private final BookmakerRepository bookmakerRepository;
    //    private final MatchOddsRepository matchOddsRepository;
    private final OddsHistoryRepository oddsHistoryRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final SeasonExternalIdRepository seasonExternalIdRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;

    private final WebClient.Builder webClientBuilder;

    @Override
    public void syncLeagues() {
        log.info("🔍 Starting API-Football league sync...");

        // TODO: Implement API-Football league fetching
        // This would use WebClient to call API-Football's /leagues endpoint
        // and save leagues with their API-Football external IDs

        log.info("✅ API-Football league sync completed (placeholder)");
    }

    @Override
    public void storeSeasons() {
        log.info("🔍 Starting API-Football season sync...");

        List<League> activeLeagues = leagueRepository.findByActiveIsTrue();

        for (League league : activeLeagues) {
            Optional<LeagueExternalId> apiFootballLeagueId = leagueExternalIdRepository
                    .findByLeagueIdAndProvider(league.getId(), Provider.API_FOOTBALL);

            if (apiFootballLeagueId.isEmpty()) {
                log.debug("⚠️ League {} does not have API-Football ID, skipping", league.getName());
                continue;
            }

            // TODO: Call API-Football /leagues/seasons endpoint
            // Save seasons with API-Football external IDs
        }

        log.info("✅ API-Football season sync completed (placeholder)");
    }

    @Override
    public void syncTeams() {
        log.info("🔍 Starting API-Football team sync...");

        List<League> activeLeagues = leagueRepository.findByActiveIsTrue();

        for (League league : activeLeagues) {
            Optional<LeagueExternalId> apiFootballLeagueId = leagueExternalIdRepository
                    .findByLeagueIdAndProvider(league.getId(), Provider.API_FOOTBALL);

            if (apiFootballLeagueId.isEmpty()) {
                continue;
            }

            // TODO: Call API-Football /teams endpoint with league and season
            // Save teams with API-Football external IDs
        }

        log.info("✅ API-Football team sync completed (placeholder)");
    }

    @Override
    public void storeMatches() {
        log.info("🔍 Starting API-Football match sync...");

        List<Season> currentSeasons = seasonRepository.findAll().stream()
                .filter(Season::isCurrent)
                .toList();

        for (Season season : currentSeasons) {
            Optional<SeasonExternalId> apiFootballSeasonId = seasonExternalIdRepository
                    .findBySeasonIdAndProvider(season.getId(), Provider.API_FOOTBALL);

            if (apiFootballSeasonId.isEmpty()) {
                continue;
            }

            // TODO: Call API-Football /fixtures endpoint
            // Save matches with API-Football external IDs
        }

        log.info("✅ API-Football match sync completed (placeholder)");
    }

    @Override
    public void storeMatchStatistics() {
        log.info("🔍 API-Football statistics are limited. Use Understats for advanced stats.");
    }

    @Override
    public void prepareMatchUpcomingDaily() {
        log.info("🔍 Starting API-Football upcoming matches sync...");

        // TODO: Call API-Football /fixtures endpoint with from=today&to=tomorrow
        // Save upcoming matches and create predictions

        log.info("✅ API-Football upcoming matches sync completed (placeholder)");
    }

    @Override
    public void prepareMatchResultDaily() {
        log.info("🔍 Starting API-Football match results sync...");

        // TODO: Call API-Football /fixtures endpoint with status=FT
        // Update match scores and prediction results

        log.info("✅ API-Football match results sync completed (placeholder)");
    }

    @Override
    public List<com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchesData.SofaEventDTO> getLatestHistoriesMatchesByTeamId(Long sofaTeamId) {
        return Collections.emptyList();
    }

    @Override
    public Provider getProvider() {
        return Provider.API_FOOTBALL;
    }

    // Helper method to call API-Football
    protected <T> T callApiFootball(String endpoint, Class<T> responseType) {
        WebClient webClient = webClientBuilder
                .baseUrl("https://api-football-v1.p.rapidapi.com/v3")
                .defaultHeader("x-rapidapi-key", "YOUR_API_KEY")
                .defaultHeader("x-rapidapi-host", "api-football-v1.p.rapidapi.com")
                .build();

        return webClient.get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(responseType)
                .block();
    }

    // Helper method to sync odds from API-Football
    protected void syncOddsForMatch(Match match) {
        Optional<MatchExternalId> apiFootballMatchId = matchExternalIdRepository
                .findByMatchIdAndProvider(match.getId(), Provider.API_FOOTBALL);

        if (apiFootballMatchId.isEmpty()) {
            return;
        }

        // TODO: Call API-Football /odds endpoint
        // Save odds to MatchOdds and OddsHistory tables
    }
}
