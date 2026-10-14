package com.mmo.module.fb.ingest.provider.strategy.impl;

import com.mmo.module.fb.domain.entity.League;
import com.mmo.module.fb.domain.entity.LeagueExternalId;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.LeagueRepository;
import com.mmo.module.fb.domain.repository.MatchAdvancedStatsRepository;
import com.mmo.module.fb.domain.repository.MatchExternalIdRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.domain.repository.PlayerExternalIdRepository;
import com.mmo.module.fb.domain.repository.PlayerMatchStatsRepository;
import com.mmo.module.fb.domain.repository.PlayerRepository;
import com.mmo.module.fb.domain.repository.TeamExternalIdRepository;
import com.mmo.module.fb.domain.repository.TeamRepository;
import com.mmo.module.fb.ingest.provider.strategy.AbstractCrawler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnderstatsCrawlStrategy extends AbstractCrawler {
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;
    private final PlayerExternalIdRepository playerExternalIdRepository;
    private final PlayerMatchStatsRepository playerMatchStatsRepository;
    private final MatchAdvancedStatsRepository matchAdvancedStatsRepository;

    @Override
    public void syncLeagues() {
        log.info("🔍 Understats does not provide league data directly. Leagues should be synced from other providers.");
    }

    @Override
    public void storeSeasons() {
        log.info("🔍 Understats does not provide season data directly. Seasons should be synced from other providers.");
    }

    @Override
    public void syncTeams() {
        log.info("🔍 Understats teams are discovered during match scraping. Syncing teams from existing matches...");

        List<Match> matchesWithoutUnderstatsTeamIds = matchRepository.findAll().stream()
                .filter(m -> matchExternalIdRepository.findByMatchIdAndProvider(m.getId(), Provider.UNDER_STATS).isEmpty())
                .limit(100)
                .toList();

        if (CollectionUtils.isEmpty(matchesWithoutUnderstatsTeamIds)) {
            log.info("✅ All matches have Understats team mappings or no matches found.");
            return;
        }

        executeStorePipeline(
                matchesWithoutUnderstatsTeamIds,
                (match, page) -> {
                    // TODO: Implement Understats team scraping logic
                    // This would involve scraping Understats to find team IDs by name
                    return null;
                },
                (match, teamData) -> {
                    // TODO: Save team external ID mappings
                }
        );
    }

    @Override
    public void storeMatches() {
        log.info("🔍 Understats matches are synced by linking to existing matches from other providers...");

        List<League> activeLeagues = leagueRepository.findByActiveIsTrue();

        for (League league : activeLeagues) {
            Optional<LeagueExternalId> understatsLeagueId = leagueExternalIdRepository
                    .findByLeagueIdAndProvider(league.getId(), Provider.UNDER_STATS);

            if (understatsLeagueId.isEmpty()) {
                log.debug("⚠️ League {} does not have Understats ID, skipping", league.getName());
                continue;
            }

            // TODO: Implement match scraping from Understats using league external ID
            // This would involve scraping Understats match pages and linking to existing matches
        }
    }

    @Override
    public void storeMatchStatistics() {
        log.info("🔍 Starting Understats advanced statistics crawl...");

        List<Match> matchesWithoutAdvancedStats = matchRepository.findAll().stream()
                .filter(m -> m.getStatus() != null && "FINISHED".equals(m.getStatus().name()))
//                .filter(m -> matchAdvancedStatsRepository.findByMatchIdAndTeamId(m.getId(), m.getHomeTeam().getId()).isEmpty())
                .limit(50)
                .toList();

        if (CollectionUtils.isEmpty(matchesWithoutAdvancedStats)) {
            log.info("✅ All finished matches have advanced statistics.");
            return;
        }

        executeStorePipeline(
                matchesWithoutAdvancedStats,
                (match, page) -> {
                    // TODO: Implement Understats advanced stats scraping
                    // This would scrape xG, xA, PPDA, field tilt, etc. from Understats
                    return null;
                },
                (match, statsData) -> {
                    // TODO: Save MatchAdvancedStats for both home and away teams
                }
        );
    }

    @Override
    public void prepareMatchUpcomingDaily() {
        log.info("🔍 Understats does not provide upcoming match data. Use SofaScore or API-Football for this.");
    }

    @Override
    public void prepareMatchResultDaily() {
        log.info("🔍 Understats results are updated during statistics crawl.");
    }

    @Override
    public List<com.mmo.module.fb.ingest.provider.model.sofa.SofaMatchesData.SofaEventDTO> getLatestHistoriesMatchesByTeamId(Long sofaTeamId) {
        return Collections.emptyList();
    }

    @Override
    public Provider getProvider() {
        return Provider.UNDER_STATS;
    }

    // Helper method to find or create team by name
//    protected Team findOrCreateTeam(String teamName) {
//        return teamRepository.findByName(teamName)
//                .orElseGet(() -> {
//                    Team newTeam = Team.builder()
//                            .name(teamName)
//                            .isActive(true)
//                            .build();
//                    return teamRepository.save(newTeam);
//                });
//    }

    // Helper method to find or create player by name and team
//    protected Player findOrCreatePlayer(String playerName, Team team) {
//        return playerRepository.findByNameAndTeamId(playerName, team.getId())
//                .orElseGet(() -> {
//                    Player newPlayer = Player.builder()
//                            .name(playerName)
//                            .team(team)
//                            .isActive(true)
//                            .build();
//                    return playerRepository.save(newPlayer);
//                });
//    }
}
