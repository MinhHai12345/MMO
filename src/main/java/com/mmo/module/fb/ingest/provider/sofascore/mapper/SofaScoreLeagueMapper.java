package com.mmo.module.fb.ingest.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.provider.model.MmoProviderDataStandardizer;
import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import com.mmo.module.fb.ingest.provider.sofascore.SofaScoreSeasonMatcher;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.SeasonDTO;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.UniqueTournamentDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class SofaScoreLeagueMapper {

    public ProviderLeague toProviderLeague(UniqueTournamentDTO tournament, List<SeasonDTO> sourceSeasons) {
        if (tournament == null || tournament.id() == null
                || tournament.name() == null || tournament.name().isBlank()) {
            return null;
        }

        List<SeasonDTO> validSeasons = sourceSeasons == null ? List.of()
                : sourceSeasons.stream().filter(Objects::nonNull)
                .filter(season -> season.id() != null).toList();
        boolean hasCurrentSeason = validSeasons.stream().anyMatch(SeasonDTO::current);

        List<ProviderSeason> seasons = new ArrayList<>(validSeasons.size());
        for (SeasonDTO season : validSeasons) {
            String year = SofaScoreSeasonMatcher.canonicalStartYear(season.year());
            if (year == null) year = SofaScoreSeasonMatcher.canonicalStartYear(season.name());
            if (year == null) continue;
            boolean current = season.current() || (!hasCurrentSeason && seasons.isEmpty());
            seasons.add(new ProviderSeason(season.id().toString(), year, current, null, null));
        }

        String country = tournament.category() == null ? null
                : MmoProviderDataStandardizer.countryName(tournament.category().name());
        String tournamentId = tournament.id().toString();
        return new ProviderLeague(tournamentId, tournamentId,
                MmoProviderDataStandardizer.leagueName(tournament.name()),
                country, null, List.copyOf(seasons));
    }

    public ProviderSeason toProviderSeason(SeasonDTO season) {
        if (season == null || season.id() == null) return null;
        String year = SofaScoreSeasonMatcher.canonicalStartYear(season.year());
        if (year == null) year = SofaScoreSeasonMatcher.canonicalStartYear(season.name());
        if (year == null) return null;
        return new ProviderSeason(season.id().toString(), year, season.current(), null, null);
    }
}
