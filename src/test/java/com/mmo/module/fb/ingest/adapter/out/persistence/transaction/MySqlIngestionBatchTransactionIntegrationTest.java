package com.mmo.module.fb.ingest.adapter.out.persistence.transaction;

import com.mmo.module.fb.ingest.application.port.out.IngestionBatchTransactionPort;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exercises atomic persistence and cursor updates against the MySQL 8 CI service. */
@EnabledIfEnvironmentVariable(named = "MMO_MYSQL_TEST_URL", matches = ".+")
class MySqlIngestionBatchTransactionIntegrationTest {

    private static final String DATA_TABLE = "fb_ingestion_batch_data_it";
    private static final String CURSOR_TABLE = "fb_ingestion_batch_cursor_it";
    private static JdbcTemplate jdbc;
    private static IngestionBatchTransactionPort transactions;

    @BeforeAll
    static void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(System.getenv("MMO_MYSQL_TEST_URL"));
        dataSource.setUsername(System.getenv("MMO_MYSQL_TEST_USERNAME"));
        dataSource.setPassword(System.getenv("MMO_MYSQL_TEST_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        transactions = new IngestionBatchTransactionAdapter(new DataSourceTransactionManager(dataSource));
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + DATA_TABLE
                + " (id BIGINT NOT NULL PRIMARY KEY, data_value VARCHAR(100) NOT NULL) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + CURSOR_TABLE
                + " (id BIGINT NOT NULL PRIMARY KEY, cursor_value VARCHAR(100) NOT NULL) ENGINE=InnoDB");
    }

    @BeforeEach
    void clearTables() {
        jdbc.update("DELETE FROM " + DATA_TABLE);
        jdbc.update("DELETE FROM " + CURSOR_TABLE);
    }

    @AfterAll
    static void tearDown() {
        if (jdbc != null) {
            jdbc.update("DELETE FROM " + DATA_TABLE);
            jdbc.update("DELETE FROM " + CURSOR_TABLE);
        }
    }

    @Test
    void rollsBackPersistedDataAndCursorTogetherWhenBatchFails() {
        assertThrows(IllegalStateException.class, () -> transactions.execute(() -> {
            jdbc.update("INSERT INTO " + DATA_TABLE + " (id, data_value) VALUES (?, ?)",
                    1L, "persisted-row");
            jdbc.update("INSERT INTO " + CURSOR_TABLE + " (id, cursor_value) VALUES (?, ?)",
                    1L, "cursor-100");
            throw new IllegalStateException("simulated failure before commit");
        }));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + DATA_TABLE, Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + CURSOR_TABLE, Integer.class));
    }

    @Test
    void commitsDataAndCursorTogetherWhenBatchSucceeds() {
        transactions.execute(() -> {
            jdbc.update("INSERT INTO " + DATA_TABLE + " (id, data_value) VALUES (?, ?)",
                    2L, "persisted-row");
            jdbc.update("INSERT INTO " + CURSOR_TABLE + " (id, cursor_value) VALUES (?, ?)",
                    2L, "cursor-200");
            return null;
        });

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM " + DATA_TABLE, Integer.class));
        assertEquals("cursor-200", jdbc.queryForObject(
                "SELECT cursor_value FROM " + CURSOR_TABLE + " WHERE id = 2", String.class));
    }
}