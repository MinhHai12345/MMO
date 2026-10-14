package com.mmo.module.fb.ingest.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.provider.model.ProviderLeague;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaSeasonData;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaUniqueTournamentsData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class SofaScoreLeagueMapper {

    public ProviderLeague toProviderLeague(SofaUniqueTournamentsData.UniqueTournamentDTO tournament,
                                           List<SofaSeasonData.SeasonDTO> sourceSeasons) {
        if (tournament == null || tournament.getId() == null) return null;
        List<ProviderSeason> seasons = sourceSeasons == null ? List.of() : sourceSeasons.stream()
                .filter(Objects::nonNull)
                .filter(season -> season.getId() != null)
                .map(this::toProviderSeason)
                .toList();
        String country = tournament.getCategory() == null ? null : tournament.getCategory().getName();
        return new ProviderLeague(tournament.getId().toString(), tournament.getName(), country, null, seasons);
    }

    public ProviderSeason toProviderSeason(SofaSeasonData.SeasonDTO season) {
        if (season == null || season.getId() == null) return null;
        String year = season.getYear() != null ? season.getYear() : season.getName();
        return new ProviderSeason(season.getId().toString(), year, season.isCurrent(), null, null);
    }
}
