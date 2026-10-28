package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.service.FixtureIngestionService;
import com.mmo.module.fb.ingest.service.LeagueIngestionService;
import com.mmo.module.fb.ingest.service.TeamIngestionService;
import com.mmo.module.fb.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.league.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.match.repository.MatchExternalIdRepository;
import com.mmo.module.fb.team.repository.TeamExternalIdRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Maps SofaScore provider IDs onto existing canonical Football-Data entities.
 * This operation intentionally does not create canonical leagues, seasons, teams or matches.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SofaScoreExternalMappingService {

    private final LeagueIngestionService leagueIngestionService;
    private final TeamIngestionService teamIngestionService;
    private final FixtureIngestionService fixtureIngestionService;
    private final LeagueExternalIdRepository leagueExternalIdRepository;
    private final SeasonExternalIdRepository seasonExternalIdRepository;
    private final TeamExternalIdRepository teamExternalIdRepository;
    private final MatchExternalIdRepository matchExternalIdRepository;

    public MappingResult mapExistingCoreData() {
        // Dependency order matters: team and fixture mapping requires league/season mappings.
        leagueIngestionService.syncLeaguesForProvider(Provider.SOFA_SCORE);
        teamIngestionService.syncTeamsForProvider(Provider.SOFA_SCORE);
        fixtureIngestionService.syncFixturesForProvider(Provider.SOFA_SCORE, null, null);

        MappingResult result = new MappingResult(
                Provider.SOFA_SCORE.name(),
                leagueExternalIdRepository.findAllByProvider(Provider.SOFA_SCORE).size(),
                seasonExternalIdRepository.findAllByProvider(Provider.SOFA_SCORE).size(),
                teamExternalIdRepository.findAllByProvider(Provider.SOFA_SCORE).size(),
                matchExternalIdRepository.findAllByProvider(Provider.SOFA_SCORE).size()
        );
        log.info("SofaScore external ID mapping finished: {}", result);
        return result;
    }

    public record MappingResult(
            String provider,
            int leagueMappings,
            int seasonMappings,
            int teamMappings,
            int matchMappings
    ) {
    }
}
