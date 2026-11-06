package com.mmo.module.fb.ingest.application.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Serializes ingestion workflows across Quartz jobs and application instances.
 *
 * <p>MySQL advisory locks belong to a database session, not a transaction. The
 * connection is held for the operation and the lock is explicitly released in a
 * finally block. If release cannot be confirmed, the physical connection is aborted:
 * closing a HikariCP proxy alone may only return the still-locked session to the pool.</p>
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
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private final DataSource dataSource;

    public IngestionDistributedLock(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    /**
     * Executes an ingest workflow only when the database lock is acquired.
     *
     * @return true when the operation ran; false when another instance/job owns the lock
     */
    public boolean execute(String lockName, Runnable operation) {
        validate(lockName, operation);

        try (Connection connection = dataSource.getConnection()) {
            String scopedLockName = scopeToCatalog(connection, lockName);
            if (!acquire(connection, scopedLockName)) {
                log.info("Ingestion execution skipped because lock is already held: {}", scopedLockName);
                return false;
            }

            try {
                operation.run();
                return true;
            } finally {
                release(connection, scopedLockName);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to manage MySQL ingestion lock: " + lockName, exception);
        }
    }

    /**
     * MySQL named locks are server-wide, not schema-wide. Include the active catalog so
     * dev/test schemas sharing one MySQL server do not block one another.
     */
    private String scopeToCatalog(Connection connection, String requestedName) throws SQLException {
        String catalog = connection.getCatalog();
        if (catalog == null || catalog.isBlank()) {
            return requestedName;
        }

        String normalizedCatalog = catalog.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "_");
        String scopedName = requestedName + ":" + normalizedCatalog;
        if (scopedName.length() <= 64) {
            return scopedName;
        }

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(catalog.getBytes(StandardCharsets.UTF_8));
            String suffix = HexFormat.of().formatHex(digest).substring(0, 16);
            String prefix = requestedName.substring(0, Math.min(requestedName.length(), 46));
            return prefix + ":" + suffix;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable for MySQL lock-name scoping", exception);
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
        boolean released = false;
        try (PreparedStatement statement = connection.prepareStatement(RELEASE_SQL)) {
            statement.setString(1, lockName);
            try (ResultSet resultSet = statement.executeQuery()) {
                released = resultSet.next() && resultSet.getInt(1) == 1 && !resultSet.wasNull();
            }
        } catch (SQLException exception) {
            log.error("Failed to explicitly release ingestion lock {}", lockName, exception);
        }

        if (!released) {
            log.error("MySQL ingestion lock release was not confirmed; aborting its owning connection: {}",
                    lockName);
            abortConnection(connection, lockName);
        }
    }

    /**
     * Connection.close() on a pool proxy does not necessarily close the physical
     * MySQL session. JDBC abort forces the driver/pool to discard that session so
     * a session-scoped advisory lock cannot leak into a later borrower.
     */
    private void abortConnection(Connection connection, String lockName) {
        try {
            connection.abort(DIRECT_EXECUTOR);
        } catch (SQLException | RuntimeException exception) {
            log.error("Unable to abort connection holding ingestion lock {}; manual recovery may be required",
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
