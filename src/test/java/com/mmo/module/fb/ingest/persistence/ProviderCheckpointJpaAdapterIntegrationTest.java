package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.ingest.application.port.out.ProviderCheckpointStore;
import com.mmo.module.fb.ingest.enums.CheckpointStatus;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.repository.ProviderSyncCheckpointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(ProviderCheckpointJpaAdapter.class)
@TestPropertySource(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ProviderCheckpointJpaAdapterIntegrationTest {

    @Autowired
    private ProviderCheckpointStore checkpointStore;

    @Autowired
    private ProviderSyncCheckpointRepository checkpointRepository;

    @Test
    void storesCheckpointStateIndependentlyPerDatasetLeagueAndSeason() {
        checkpointStore.markStatus(Provider.FOOTBALL_DATA_UK, Dataset.FIXTURE,
                39L, "2021/2022", CheckpointStatus.NOT_AVAILABLE);

        assertFalse(checkpointStore.isCompleted(Provider.FOOTBALL_DATA_UK, Dataset.FIXTURE,
                39L, "2021/2022"));
        assertTrue(checkpointStore.isSatisfied(Provider.FOOTBALL_DATA_UK, Dataset.FIXTURE,
                39L, "2021/2022"));
        assertFalse(checkpointStore.isCompleted(Provider.FOOTBALL_DATA_UK, Dataset.TEAM,
                39L, "2021/2022"));
        assertFalse(checkpointStore.isSatisfied(Provider.FOOTBALL_DATA_UK, Dataset.TEAM,
                39L, "2021/2022"));
        assertEquals(1, checkpointRepository.count());

        checkpointStore.markStatus(Provider.FOOTBALL_DATA_UK, Dataset.FIXTURE,
                39L, "2021/2022", CheckpointStatus.COMPLETED);
        checkpointStore.markStatus(Provider.FOOTBALL_DATA_UK, Dataset.TEAM,
                39L, "2021/2022", CheckpointStatus.PARTIAL);

        assertTrue(checkpointStore.isCompleted(Provider.FOOTBALL_DATA_UK, Dataset.FIXTURE,
                39L, "2021/2022"));
        assertFalse(checkpointStore.isCompleted(Provider.FOOTBALL_DATA_UK, Dataset.TEAM,
                39L, "2021/2022"));
        assertEquals(2, checkpointRepository.count());
    }

    @Test
    void legacyCheckpointWithoutStatusRemainsCompleted() {
        var legacy = new com.mmo.module.fb.ingest.entity.ProviderSyncCheckpoint();
        legacy.setProvider(Provider.FOOTBALL_DATA_UK);
        legacy.setCheckpointKey("CORE_INITIAL_BACKFILL");
        legacy.setCompletedAt(java.time.Instant.now());
        checkpointRepository.saveAndFlush(legacy);

        assertTrue(checkpointStore.isCompleted(Provider.FOOTBALL_DATA_UK, "CORE_INITIAL_BACKFILL"));
    }
}
