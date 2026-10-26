package com.mmo.module.fb.ingest.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO;

import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SofaScoreLeagueMapper {

    private static final Pattern SEASON_START_YEAR = Pattern.compile("(20\\d{2})");

    public ProviderLeague toProviderLeague(SofaScoreResponseDTO.UniqueTournamentDTO tournament,
                                           List<SofaScoreResponseDTO.SeasonDTO> sourceSeasons) {
        if (tournament == null || tournament.id() == null
                || tournament.name() == null || tournament.name().isBlank()) {
            return null;
        }

        List<SofaScoreResponseDTO.SeasonDTO> validSeasons = sourceSeasons == null ? List.of()
                : sourceSeasons.stream()
                    .filter(Objects::nonNull)
                    .filter(season -> season.id() != null)
                    .toList();
        boolean hasCurrentSeason = validSeasons.stream().anyMatch(SofaScoreResponseDTO.SeasonDTO::current);

        List<ProviderSeason> seasons = new ArrayList<>(validSeasons.size());
        for (int index = 0; index < validSeasons.size(); index++) {
            SofaScoreResponseDTO.SeasonDTO season = validSeasons.get(index);
            String year = normalizeSeasonYear(season);
            if (year == null) {
                continue;
            }
            // SofaScore occasionally omits isCurrent. Its seasons endpoint is ordered newest first;
            // use the first valid season as a fallback only when no season is explicitly current.
            boolean current = season.current() || (!hasCurrentSeason && seasons.isEmpty());
            seasons.add(new ProviderSeason(season.id().toString(), year, current, null, null));
        }

        String country = tournament.category() == null ? null : tournament.category().name();
        return new ProviderLeague(tournament.id().toString(), tournament.name(),
                country, null, List.copyOf(seasons));
    }

    public ProviderSeason toProviderSeason(SofaScoreResponseDTO.SeasonDTO season) {
        if (season == null || season.id() == null) {
            return null;
        }
        String year = normalizeSeasonYear(season);
        if (year == null) {
            return null;
        }
        return new ProviderSeason(season.id().toString(), year, season.current(), null, null);
    }

    private String normalizeSeasonYear(SofaScoreResponseDTO.SeasonDTO season) {
        if (season.year() != null && !season.year().isBlank()) {
            Matcher matcher = SEASON_START_YEAR.matcher(season.year());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        if (season.name() == null || season.name().isBlank()) {
            return null;
        }
        Matcher matcher = SEASON_START_YEAR.matcher(season.name());
        return matcher.find() ? matcher.group(1) : season.name().trim();
    }
}
