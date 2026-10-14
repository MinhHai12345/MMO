package com.mmo.module.fb.ingest.provider.apifootball.mapper;

import com.mmo.core.converter.AbstractMapper;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.LeagueItem;
import com.mmo.module.fb.ingest.provider.apifootball.dto.ApiFootballDtos.LeagueSeasonDto;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Component
public class ApiFootballLeagueMapper extends AbstractMapper<LeagueItem, ProviderLeague> {

    public ProviderLeague toModel(LeagueItem item) {
        return map(item, null);
    }

    @Override
    public ProviderLeague map(LeagueItem item, ProviderLeague target) {
        if (item == null || item.league() == null || item.league().id() == null) return null;
        List<ProviderSeason> seasons = item.seasons() == null ? List.of()
                : item.seasons().stream()
                .filter(season -> season != null && season.year() != null)
                .map(this::toSeason)
                .toList();
        return new ProviderLeague(
                item.league().id().toString(),
                item.league().name(),
                item.country() == null ? null : item.country().name(),
                item.league().logo(),
                seasons
        );
    }

    private ProviderSeason toSeason(LeagueSeasonDto season) {
        return new ProviderSeason(
                season.year().toString(),
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
