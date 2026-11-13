package com.mmo.module.fb.ingest.adapter.out.provider.sofascore;

import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client.AbstractSofaScoreProvider;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.SeasonDTO;
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
            log.info("[SOFA_SCORE][LEAGUE] Loading tournament catalogue");
            List<UniqueTournamentDTO> tournaments = sofaScoreClient.fetchLeagues(page);
            if (tournaments == null || tournaments.isEmpty()) {
                log.warn("[SOFA_SCORE][LEAGUE] Tournament catalogue returned no entries");
                return List.of();
            }

            List<UniqueTournamentDTO> selectedTournaments = tournaments.stream()
                    .filter(Objects::nonNull)
                    .filter(tournament -> tournament.id() != null)
                    .filter(tournament -> externalLeagueIds == null || externalLeagueIds.isEmpty()
                            || externalLeagueIds.contains(tournament.id()))
                    .toList();

            log.info("[SOFA_SCORE][LEAGUE] Catalogue loaded: total={}, selected={}",
                    tournaments.size(), selectedTournaments.size());

            List<ProviderLeague> result = new ArrayList<>();
            for (UniqueTournamentDTO tournament : selectedTournaments) {
                long tournamentStartedAt = System.nanoTime();
                log.info("[SOFA_SCORE][LEAGUE] Fetching seasons: tournamentId={}, name={}",
                        tournament.id(), tournament.name());
                try {
                    List<SeasonDTO> seasons =
                            sofaScoreClient.fetchSeasonByTournamentId(tournament.id(), page);
                    if (seasons == null || seasons.isEmpty()) {
                        log.warn("[SOFA_SCORE][LEAGUE] No seasons returned: tournamentId={}, name={}",
                                tournament.id(), tournament.name());
                        continue;
                    }

                    ProviderLeague league = mapper.toProviderLeague(tournament, seasons);
                    if (league != null && league.seasons() != null && !league.seasons().isEmpty()) {
                        result.add(league);
                    }
                    log.info("[SOFA_SCORE][LEAGUE] Tournament completed: tournamentId={}, seasons={}, durationMs={}",
                            tournament.id(), seasons.size(),
                            (System.nanoTime() - tournamentStartedAt) / 1_000_000);
                } catch (RuntimeException exception) {
                    log.error("[SOFA_SCORE][LEAGUE] Tournament fetch failed: tournamentId={}, name={}, reason={}",
                            tournament.id(), tournament.name(), exception.getMessage(), exception);
                    throw exception;
                }
            }

            log.info("[SOFA_SCORE][LEAGUE] Fetch completed: leagues={}, durationMs={}",
                    result.size(), (System.nanoTime() - startedAt) / 1_000_000);
            return List.copyOf(result);
        });
    }
}
