package com.mmo.module.fb.ingest.provider.apifootball;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.AppProperties;
import com.mmo.module.fb.domain.entity.LeagueStanding;
import com.mmo.module.fb.domain.entity.LeagueExternalId;
import com.mmo.module.fb.domain.entity.Season;
import com.mmo.module.fb.domain.entity.TeamExternalId;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.domain.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.domain.repository.LeagueStandingRepository;
import com.mmo.module.fb.domain.repository.SeasonRepository;
import com.mmo.module.fb.domain.repository.TeamExternalIdRepository;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.StandingRowDto;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.StandingsItem;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballStandingsSyncService {

    private static final TypeReference<ApiFootballResponse<StandingsItem>> RESPONSE_TYPE = new TypeReference<>() {};

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

        // /standings accepts historical seasons too; persist each season independently.
        for (Season season : seasonRepository.findAll()) {
            Long apiLeagueId = leagueExternalIdRepository
                    .findByLeagueIdAndProvider(season.getLeague().getId(), Provider.API_FOOTBALL)
                    .map(LeagueExternalId::getExternalId)
                    .map(ApiFootballStandingsSyncService::parseLong)
                    .orElse(null);
            if (apiLeagueId == null) continue;

            ApiFootballResponse<StandingsItem> response = client.get("/standings",
                    Map.of("league", apiLeagueId, "season", season.getYear()), RESPONSE_TYPE);
            for (StandingsItem item : safe(response.response())) {
                if (item.league() == null || item.league().standings() == null) continue;
                for (List<StandingRowDto> group : item.league().standings()) {
                    for (StandingRowDto row : group) {
                        if (row.team() == null || row.team().id() == null) continue;
                        var team = teamExternalIdRepository
                                .findByProviderAndExternalId(Provider.API_FOOTBALL, row.team().id().toString())
                                .map(TeamExternalId::getTeam).orElse(null);
                        if (team == null) continue;

                        LeagueStanding standing = standingRepository
                                .findBySeasonIdAndTeamId(season.getId(), team.getId())
                                .orElseGet(() -> LeagueStanding.builder()
                                        .league(season.getLeague()).season(season).team(team).build());
                        var all = row.all();
                        standing.setPosition(row.rank());
                        standing.setPoints(row.points());
                        standing.setGoalDifference(row.goalsDiff());
                        standing.setForm(row.form());
                        standing.setGroupName(row.group());
                        if (all != null) {
                            standing.setPlayed(all.played());
                            standing.setWon(all.win());
                            standing.setDrawn(all.draw());
                            standing.setLost(all.lose());
                            if (all.goals() != null) {
                                standing.setGoalsFor(all.goals().forGoals());
                                standing.setGoalsAgainst(all.goals().againstGoals());
                            }
                        }
                        standing.setLastSyncedAt(Instant.now());
                        standingRepository.save(standing);
                        saved++;
                    }
                }
            }
        }
        log.info("API-Football standings sync completed. {} rows upserted.", saved);
    }

    private static Long parseLong(String value) {
        try { return Long.valueOf(value); }
        catch (Exception ignored) { return null; }
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
