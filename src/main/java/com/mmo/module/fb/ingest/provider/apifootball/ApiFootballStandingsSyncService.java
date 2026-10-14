package com.mmo.module.fb.ingest.provider.apifootball;

import com.fasterxml.jackson.databind.JsonNode;
import com.mmo.module.fb.AppProperties;
import com.mmo.module.fb.domain.entity.LeagueStanding;
import com.mmo.module.fb.domain.entity.LeagueExternalId;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.TeamExternalId;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.LeagueStandingRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.domain.repository.TeamExternalIdRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballStandingsSyncService {

    private final ApiFootballClient client;
    private final AppProperties appProperties;
    private final SeasonRepository seasonRepository;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final LeagueStandingRepository standingRepository;

    @Transactional
    public void syncCurrentStandings() {
        if (appProperties.getApiFootball() == null || !appProperties.getApiFootball().isEnabled()) return;

        int saved = 0;
        for (Season season : seasonRepository.findAll().stream().filter(Season::isCurrent).toList()) {
            Long apiLeagueId = leagueExternalIdRepository
                    .findByLeagueIdAndProvider(season.getLeague().getId(), Provider.API_FOOTBALL)
                    .map(LeagueExternalId::getExternalId)
                    .map(ApiFootballStandingsSyncService::parseLong)
                    .orElse(null);
            if (apiLeagueId == null) continue;

            JsonNode body = client.get("/standings", Map.of("league", apiLeagueId, "season", season.getYear()));
            JsonNode standingsGroups = body.path("response").path(0).path("league").path("standings");
            for (JsonNode group : standingsGroups) {
                for (JsonNode row : group) {
                    String apiTeamId = row.path("team").path("id").asText(null);
                    if (apiTeamId == null) continue;
                    var team = teamExternalIdRepository
                            .findByProviderAndExternalId(Provider.API_FOOTBALL, apiTeamId)
                            .map(TeamExternalId::getTeam)
                            .orElse(null);
                    if (team == null) continue;

                    LeagueStanding standing = standingRepository
                            .findBySeasonIdAndTeamId(season.getId(), team.getId())
                            .orElseGet(() -> LeagueStanding.builder()
                                    .league(season.getLeague()).season(season).team(team).build());
                    JsonNode all = row.path("all");
                    JsonNode goals = all.path("goals");
                    standing.setPosition(integer(row, "rank"));
                    standing.setPoints(integer(row, "points"));
                    standing.setGoalDifference(integer(row, "goalsDiff"));
                    standing.setForm(text(row, "form"));
                    standing.setGroupName(text(row, "group"));
                    standing.setPlayed(integer(all, "played"));
                    standing.setWon(integer(all, "win"));
                    standing.setDrawn(integer(all, "draw"));
                    standing.setLost(integer(all, "lose"));
                    standing.setGoalsFor(integer(goals, "for"));
                    standing.setGoalsAgainst(integer(goals, "against"));
                    standing.setLastSyncedAt(Instant.now());
                    standingRepository.save(standing);
                    saved++;
                }
            }
        }
        log.info("API-Football standings sync completed. {} rows upserted.", saved);
    }

    private static Long parseLong(String value) {
        try { return Long.valueOf(value); }
        catch (Exception ignored) { return null; }
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
