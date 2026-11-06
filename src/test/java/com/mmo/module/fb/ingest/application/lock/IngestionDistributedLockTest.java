package com.mmo.module.fb.ingest.application.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IngestionDistributedLockTest {

    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement acquireStatement;
    private ResultSet acquireResult;
    private PreparedStatement releaseStatement;
    private ResultSet releaseResult;
    private IngestionDistributedLock lock;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        acquireStatement = mock(PreparedStatement.class);
        acquireResult = mock(ResultSet.class);
        releaseStatement = mock(PreparedStatement.class);
        releaseResult = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT GET_LOCK(?, 0)")).thenReturn(acquireStatement);
        when(connection.prepareStatement("SELECT RELEASE_LOCK(?)")).thenReturn(releaseStatement);
        when(acquireStatement.executeQuery()).thenReturn(acquireResult);
        when(releaseStatement.executeQuery()).thenReturn(releaseResult);
        when(acquireResult.next()).thenReturn(true);
        when(releaseResult.next()).thenReturn(true);
        when(releaseResult.getInt(1)).thenReturn(1);
        lock = new IngestionDistributedLock(dataSource);
    }

    @Test
    void executesOperationAndReleasesLockWhenAcquired() throws Exception {
        when(acquireResult.getInt(1)).thenReturn(1);
        AtomicBoolean executed = new AtomicBoolean();

        boolean result = lock.execute("mmo:test:lock", () -> executed.set(true));

        assertTrue(result);
        assertTrue(executed.get());
        verify(acquireStatement).setString(1, "mmo:test:lock");
        verify(releaseStatement).setString(1, "mmo:test:lock");
        verify(connection, never()).abort(any(Executor.class));
        verify(connection).close();
    }

    @Test
    void scopesLockToCurrentDatabaseCatalog() throws Exception {
        when(connection.getCatalog()).thenReturn("mmo_test");
        when(acquireResult.getInt(1)).thenReturn(1);

        assertTrue(lock.execute("mmo:test:lock", () -> { }));

        verify(acquireStatement).setString(1, "mmo:test:lock:mmo_test");
        verify(releaseStatement).setString(1, "mmo:test:lock:mmo_test");
    }

    @Test
    void skipsOperationWhenAnotherNodeOwnsLock() throws Exception {
        when(acquireResult.getInt(1)).thenReturn(0);
        AtomicBoolean executed = new AtomicBoolean();

        boolean result = lock.execute("mmo:test:lock", () -> executed.set(true));

        assertFalse(result);
        assertFalse(executed.get());
        verify(connection, never()).prepareStatement("SELECT RELEASE_LOCK(?)");
        verify(connection, never()).abort(any(Executor.class));
        verify(connection).close();
    }

    @Test
    void releasesLockWhenOperationThrows() throws Exception {
        when(acquireResult.getInt(1)).thenReturn(1);
        IllegalStateException failure = new IllegalStateException("ingest failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> lock.execute("mmo:test:lock", () -> { throw failure; }));

        assertSame(failure, thrown);
        verify(releaseStatement).executeQuery();
        verify(connection).close();
    }

    @Test
    void abortsOwningConnectionWhenReleaseThrows() throws Exception {
        when(acquireResult.getInt(1)).thenReturn(1);
        when(releaseStatement.executeQuery()).thenThrow(new SQLException("connection lost"));

        assertTrue(lock.execute("mmo:test:lock", () -> { }));

        verify(connection).abort(any(Executor.class));
        verify(connection).close();
    }

    @Test
    void abortsOwningConnectionWhenReleaseDoesNotConfirmOwnership() throws Exception {
        when(acquireResult.getInt(1)).thenReturn(1);
        when(releaseResult.getInt(1)).thenReturn(0);

        assertTrue(lock.execute("mmo:test:lock", () -> { }));

        verify(connection).abort(any(Executor.class));
        verify(connection).close();
    }

    @Test
    void closesConnectionAndFailsWhenGetLockReturnsNull() throws Exception {
        when(acquireResult.getInt(1)).thenReturn(0);
        when(acquireResult.wasNull()).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> lock.execute("mmo:test:lock", () -> fail("operation must not run")));

        verify(connection).close();
    }

    @Test
    void rejectsInvalidLockNameBeforeOpeningConnection() {
        assertThrows(IllegalArgumentException.class,
                () -> lock.execute("x".repeat(65), () -> { }));
        verifyNoInteractions(dataSource);
    }

    @Test
    void derivesStableBoundedMySqlLockNameFromLogicalScope() throws Exception {
        when(acquireResult.getInt(1)).thenReturn(1);
        String scope = "unit/FOOTBALL_DATA/FIXTURE/39/2024/2025";

        assertTrue(lock.executeScoped(scope, () -> { }));

        org.mockito.ArgumentCaptor<String> captor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(acquireStatement).setString(eq(1), captor.capture());
        String lockName = captor.getValue();
        assertTrue(lockName.startsWith("mmo:fb:ingest:"));
        assertTrue(lockName.length() <= 64);
        verify(releaseStatement).setString(1, lockName);
    }
}
