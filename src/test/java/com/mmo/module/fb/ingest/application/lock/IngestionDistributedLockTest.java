package com.mmo.module.fb.ingest.application.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
        verify(connection).close();
    }

    @Test
    void skipsOperationWhenAnotherNodeOwnsLock() throws Exception {
        when(acquireResult.getInt(1)).thenReturn(0);
        AtomicBoolean executed = new AtomicBoolean();

        boolean result = lock.execute("mmo:test:lock", () -> executed.set(true));

        assertFalse(result);
        assertFalse(executed.get());
        verify(connection, never()).prepareStatement("SELECT RELEASE_LOCK(?)");
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
}
