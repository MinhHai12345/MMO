package com.mmo.module.fb.ingest.provider.apifootball.mapper;

import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.LeagueItem;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.LeagueSeasonDto;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Component
public class ApiFootballLeagueMapper {

    public ProviderLeague toModel(LeagueItem item) {
        if (item == null || item.league() == null || item.league().id() == null) return null;
        String leagueExternalId = item.league().id().toString();
        List<ProviderSeason> seasons = item.seasons() == null ? List.of()
                : item.seasons().stream()
                .filter(season -> season != null && season.year() != null)
                .map(season -> toSeason(season, leagueExternalId))
                .toList();
        return new ProviderLeague(
                leagueExternalId,
                leagueExternalId,
                item.league().name(),
                item.country() == null ? null : item.country().name(),
                item.league().logo(),
                seasons
        );
    }

    private ProviderSeason toSeason(LeagueSeasonDto season, String leagueExternalId) {
        return new ProviderSeason(
                leagueExternalId + ":" + season.year(),
                season.year().toString(),
                Boolean.TRUE.equals(season.current()),
                parseDate(season.start()),
                parseDate(season.end())
        );
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
