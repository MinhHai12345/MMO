package com.mmo.module.fb.ingest.application.service.integration;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.model.SofaScoreExternalMappingResult;
import com.mmo.module.fb.ingest.application.port.in.SofaScoreExternalMappingUseCase;
import com.mmo.module.fb.ingest.application.port.in.FixtureIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.in.LeagueIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.in.TeamIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.out.SofaScoreMappingStatePort;
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
public class SofaScoreExternalMappingUseCaseImpl implements SofaScoreExternalMappingUseCase {

    private final LeagueIngestionUseCase leagueIngestionService;
    private final TeamIngestionUseCase teamIngestionService;
    private final FixtureIngestionUseCase fixtureIngestionService;
    private final SofaScoreMappingStatePort mappingStatePort;

    @Override
    public SofaScoreExternalMappingResult mapExistingCoreData() {
        // Dependency order matters: team and fixture mapping requires league/season mappings.
        leagueIngestionService.syncLeaguesForProvider(Provider.SOFA_SCORE);
        teamIngestionService.syncTeamsForProvider(Provider.SOFA_SCORE);
        fixtureIngestionService.syncFixturesForProvider(Provider.SOFA_SCORE, null, null);

        SofaScoreExternalMappingResult result = new SofaScoreExternalMappingResult(
                Provider.SOFA_SCORE.name(),
                mappingStatePort.countLeagues(),
                mappingStatePort.countSeasons(),
                mappingStatePort.countTeams(),
                mappingStatePort.countFixtures()
        );
        log.info("SofaScore external ID mapping finished: {}", result);
        return result;
    }

}
