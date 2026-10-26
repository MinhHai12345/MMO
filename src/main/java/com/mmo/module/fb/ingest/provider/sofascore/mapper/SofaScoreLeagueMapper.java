package com.mmo.module.fb.ingest.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO;

import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SeasonsResponseDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.TournamentsResponseDTO;
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
        if (tournament == null || tournament.getId() == null
                || tournament.getName() == null || tournament.getName().isBlank()) {
            return null;
        }

        List<SofaScoreResponseDTO.SeasonDTO> validSeasons = sourceSeasons == null ? List.of()
                : sourceSeasons.stream()
                    .filter(Objects::nonNull)
                    .filter(season -> season.getId() != null)
                    .toList();
        boolean hasCurrentSeason = validSeasons.stream().anyMatch(SofaScoreResponseDTO.SeasonDTO::isCurrent);

        List<ProviderSeason> seasons = new ArrayList<>(validSeasons.size());
        for (int index = 0; index < validSeasons.size(); index++) {
            SofaScoreResponseDTO.SeasonDTO season = validSeasons.get(index);
            String year = normalizeSeasonYear(season);
            if (year == null) {
                continue;
            }
            // SofaScore occasionally omits isCurrent. Its seasons endpoint is ordered newest first;
            // use the first valid season as a fallback only when no season is explicitly current.
            boolean current = season.isCurrent() || (!hasCurrentSeason && seasons.isEmpty());
            seasons.add(new ProviderSeason(season.getId().toString(), year, current, null, null));
        }

        String country = tournament.getCategory() == null ? null : tournament.getCategory().getName();
        return new ProviderLeague(tournament.getId().toString(), tournament.getName(),
                country, null, List.copyOf(seasons));
    }

    public ProviderSeason toProviderSeason(SofaScoreResponseDTO.SeasonDTO season) {
        if (season == null || season.getId() == null) {
            return null;
        }
        String year = normalizeSeasonYear(season);
        if (year == null) {
            return null;
        }
        return new ProviderSeason(season.getId().toString(), year, season.isCurrent(), null, null);
    }

    private String normalizeSeasonYear(SofaScoreResponseDTO.SeasonDTO season) {
        if (season.getYear() != null && !season.getYear().isBlank()) {
            Matcher matcher = SEASON_START_YEAR.matcher(season.getYear());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        if (season.getName() == null || season.getName().isBlank()) {
            return null;
        }
        Matcher matcher = SEASON_START_YEAR.matcher(season.getName());
        return matcher.find() ? matcher.group(1) : season.getName().trim();
    }
}
