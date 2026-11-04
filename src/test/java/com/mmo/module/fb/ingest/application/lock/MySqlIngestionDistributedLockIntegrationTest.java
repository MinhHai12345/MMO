package com.mmo.module.fb.ingest.application.lock;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Optional integration test. Set MMO_MYSQL_TEST_URL, MMO_MYSQL_TEST_USERNAME and
 * MMO_MYSQL_TEST_PASSWORD to a disposable MySQL 8 database to run it.
 */
@EnabledIfEnvironmentVariable(named = "MMO_MYSQL_TEST_URL", matches = ".+")
class MySqlIngestionDistributedLockIntegrationTest {

    @Test
    void serializesIndependentConnectionsAndReleasesAfterCompletion() {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl(System.getenv("MMO_MYSQL_TEST_URL"));
            dataSource.setUsername(System.getenv("MMO_MYSQL_TEST_USERNAME"));
            dataSource.setPassword(System.getenv("MMO_MYSQL_TEST_PASSWORD"));
            dataSource.setMaximumPoolSize(2);

            IngestionDistributedLock lock = new IngestionDistributedLock(dataSource);
            String lockName = "mmo:fb:ingest:test:" + ProcessHandle.current().pid();
            AtomicBoolean nestedOperationRan = new AtomicBoolean();

            boolean firstExecution = lock.execute(lockName, () -> {
                boolean secondExecution = lock.execute(lockName, () -> nestedOperationRan.set(true));
                assertFalse(secondExecution, "second independent connection must not acquire held lock");
            });

            assertTrue(firstExecution);
            assertFalse(nestedOperationRan.get());

            boolean afterRelease = lock.execute(lockName, () -> nestedOperationRan.set(true));
            assertTrue(afterRelease, "lock must be available after the first connection releases it");
            assertTrue(nestedOperationRan.get());
        }
    }
}
