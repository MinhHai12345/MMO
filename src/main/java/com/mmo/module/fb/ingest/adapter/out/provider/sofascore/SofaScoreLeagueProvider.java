package com.mmo.module.fb.ingest.adapter.out.provider.sofascore;

import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client.AbstractSofaScoreProvider;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.SeasonDTO;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreLeagueCatalogueResult;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.UniqueTournamentDTO;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.model.provider.ProviderLeague;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client.SofaScoreClient;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.mapper.SofaScoreLeagueMapper;
import com.mmo.module.fb.ingest.application.port.out.provider.LeagueIngestionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class SofaScoreLeagueProvider extends AbstractSofaScoreProvider implements LeagueIngestionPort {

    private final SofaScoreLeagueMapper mapper;
    private final SofaScoreClient sofaScoreClient;

    @Override
    public Provider getProvider() {
        return Provider.SOFA_SCORE;
    }

    @Override
    public List<ProviderLeague> fetchLeagues(List<Long> externalLeagueIds) {
        return withPage(page -> {
            long startedAt = System.nanoTime();
            log.info("[SOFA_SCORE][LEAGUE] Fetching league catalogue and seasons from one football page");

            SofaScoreLeagueCatalogueResult catalogue = sofaScoreClient.fetchLeaguesWithSeasons(page);
            if (catalogue == null || catalogue.tournaments().isEmpty()) {
                log.warn("[SOFA_SCORE][LEAGUE] Tournament catalogue returned no entries");
                return List.of();
            }

            List<ProviderLeague> result = new ArrayList<>();
            for (UniqueTournamentDTO tournament : catalogue.tournaments()) {
                if (externalLeagueIds != null && !externalLeagueIds.isEmpty()
                        && !externalLeagueIds.contains(tournament.id())) {
                    continue;
                }

                List<SeasonDTO> seasons = catalogue.seasonsByTournamentId()
                        .getOrDefault(tournament.id(), List.of());
                if (seasons.isEmpty()) {
                    log.warn("[SOFA_SCORE][LEAGUE] No seasons returned: tournamentId={}, name={}",
                            tournament.id(), tournament.name());
                    continue;
                }

                ProviderLeague league = mapper.toProviderLeague(tournament, seasons);
                if (league != null && league.seasons() != null && !league.seasons().isEmpty()) {
                    result.add(league);
                }
            }

            log.info("[SOFA_SCORE][LEAGUE] Fetch completed from one page: catalogueTournaments={}, leagues={}, durationMs={}",
                    catalogue.tournaments().size(), result.size(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return List.copyOf(result);
        });
    }
}
