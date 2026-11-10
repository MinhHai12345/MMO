package com.mmo.module.fb.ingest.application.orchestration;

import com.mmo.module.fb.ingest.application.lock.IngestionDistributedLock;
import com.mmo.module.fb.ingest.application.port.in.FixtureIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.in.LeagueIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.in.SeasonIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.in.StandingsIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.in.TeamIngestionUseCase;
import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointPort;
import com.mmo.module.fb.ingest.application.registry.SeasonBatchIngestionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveSeasonDataSyncOrchestratorTest {

    @Mock private IngestionDistributedLock distributedLock;
    @Mock private IngestionPlanFactory planFactory;
    @Mock private ProviderCheckpointPort checkpointStore;
    @Mock private LeagueIngestionUseCase leagueIngestionService;
    @Mock private SeasonIngestionUseCase seasonIngestionService;
    @Mock private TeamIngestionUseCase teamIngestionService;
    @Mock private FixtureIngestionUseCase fixtureIngestionService;
    @Mock private StandingsIngestionUseCase standingsIngestionService;
    @Mock private SeasonBatchIngestionRegistry seasonBatchRegistry;

    @InjectMocks
    private ActiveSeasonDataSyncOrchestrator orchestrator;

    @Test
    void failsInsteadOfReportingSuccessWhenAnotherWorkflowOwnsTheLock() {
        when(distributedLock.executeScoped(eq("workflow/active-season"), any(Runnable.class)))
                .thenReturn(false);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> orchestrator.sync(null, null));

        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage()
                .contains("another ingestion workflow owns the lock"));
    }
}
