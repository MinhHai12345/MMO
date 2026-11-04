package com.mmo.module.fb.ingest.application.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Serializes all ingest workflows across Quartz jobs and application instances.
 *
 * <p>MySQL advisory locks are scoped to the database server and owned by a physical
 * connection. The connection is deliberately held for the whole operation and the
 * lock is released in a finally block. Closing the connection is the final safety net
 * if release itself fails.</p>
 *
 * <p>Do not add @Transactional to callers: the lock must be held on this dedicated
 * connection, independently from the short database transactions used by repositories.</p>
 */
@Slf4j
@Component
public class IngestionDistributedLock {

    public static final String GLOBAL_INGEST_LOCK = "mmo:fb:ingest:global";
    private static final String ACQUIRE_SQL = "SELECT GET_LOCK(?, 0)";
    private static final String RELEASE_SQL = "SELECT RELEASE_LOCK(?)";

    private final DataSource dataSource;

    public IngestionDistributedLock(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    /**
     * Executes an ingest workflow only when the global database lock is acquired.
     *
     * @return true when the operation ran; false when another instance/job owns the lock
     */
    public boolean execute(String lockName, Runnable operation) {
        validate(lockName, operation);

        try (Connection connection = dataSource.getConnection()) {
            if (!acquire(connection, lockName)) {
                log.info("Ingestion execution skipped because lock is already held: {}", lockName);
                return false;
            }

            try {
                operation.run();
                return true;
            } finally {
                release(connection, lockName);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to manage MySQL ingestion lock: " + lockName, exception);
        }
    }

    private boolean acquire(Connection connection, String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ACQUIRE_SQL)) {
            statement.setString(1, lockName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("GET_LOCK returned no result for " + lockName);
                }
                int result = resultSet.getInt(1);
                if (resultSet.wasNull()) {
                    throw new SQLException("GET_LOCK returned NULL for " + lockName);
                }
                return result == 1;
            }
        }
    }

    private void release(Connection connection, String lockName) {
        try (PreparedStatement statement = connection.prepareStatement(RELEASE_SQL)) {
            statement.setString(1, lockName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getInt(1) != 1 || resultSet.wasNull()) {
                    log.error("MySQL ingestion lock release did not return success: {}", lockName);
                }
            }
        } catch (SQLException exception) {
            // Closing the owning connection releases a MySQL advisory lock as a fallback.
            log.error("Failed to explicitly release ingestion lock {}; connection close will release it",
                    lockName, exception);
        }
    }

    private void validate(String lockName, Runnable operation) {
        if (lockName == null || lockName.isBlank() || lockName.length() > 64) {
            throw new IllegalArgumentException("MySQL lock name must contain 1..64 characters");
        }
        Objects.requireNonNull(operation, "operation must not be null");
    }
}
